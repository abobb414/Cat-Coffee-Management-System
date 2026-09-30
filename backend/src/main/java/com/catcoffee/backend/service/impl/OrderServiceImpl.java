package com.catcoffee.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.catcoffee.backend.common.PageResult;
import com.catcoffee.backend.dto.OrderCreateRequest;
import com.catcoffee.backend.dto.OrderItemRequest;
import com.catcoffee.backend.entity.CafeTable;
import com.catcoffee.backend.entity.CustomerOrder;
import com.catcoffee.backend.entity.CustomerOrderItem;
import com.catcoffee.backend.entity.Drink;
import com.catcoffee.backend.exception.BusinessException;
import com.catcoffee.backend.mapper.CafeTableMapper;
import com.catcoffee.backend.mapper.CustomerOrderItemMapper;
import com.catcoffee.backend.mapper.CustomerOrderMapper;
import com.catcoffee.backend.mapper.DrinkMapper;
import com.catcoffee.backend.security.AuthUser;
import com.catcoffee.backend.security.SecurityUtils;
import com.catcoffee.backend.service.OrderService;
import com.catcoffee.backend.service.impl.MarketingServiceImpl.OrderBenefitResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CustomerOrderMapper customerOrderMapper;
    private final CustomerOrderItemMapper customerOrderItemMapper;
    private final DrinkMapper drinkMapper;
    private final CafeTableMapper cafeTableMapper;
    private final MarketingServiceImpl marketingService;

    @Override
    public PageResult<CustomerOrder> list(long current, long size, String orderStatus, String payStatus) {
        AuthUser currentUser = SecurityUtils.getCurrentUser();
        LambdaQueryWrapper<CustomerOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(orderStatus), CustomerOrder::getOrderStatus, orderStatus)
                .eq(StringUtils.hasText(payStatus), CustomerOrder::getPayStatus, payStatus)
                .eq(isCustomerUser(currentUser), CustomerOrder::getUserId, currentUser.getId())
                .orderByDesc(CustomerOrder::getCreateTime);
        Page<CustomerOrder> page = customerOrderMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerOrder save(OrderCreateRequest request) {
        AuthUser currentUser = SecurityUtils.getCurrentUser();
        CafeTable cafeTable = cafeTableMapper.selectById(request.getTableId());
        if (cafeTable == null) {
            throw new BusinessException("桌台不存在");
        }

        CustomerOrder order = new CustomerOrder();
        BeanUtils.copyProperties(request, order);
        // 支付与订单状态必须由服务端控制，客户端不能通过编辑请求伪造结算结果。
        order.setPayStatus("待支付");
        order.setOrderStatus("待制作");
        if (isCustomerUser(currentUser)) {
            order.setUserId(currentUser.getId());
            order.setCustomerName(currentUser.getNickname());
        } else if (!StringUtils.hasText(request.getCustomerName())) {
            throw new BusinessException("客户姓名不能为空");
        }
        BigDecimal originalAmount = calculateOrderAmount(request.getItems());
        order.setOriginalAmount(originalAmount);
        OrderBenefitResult benefitResult = order.getUserId() == null
                ? new OrderBenefitResult(BigDecimal.ZERO, null, 0)
                : marketingService.applyOrderBenefits(order.getUserId(), request.getPointsUsed(), request.getUserCouponId(), originalAmount);
        order.setDiscountAmount(benefitResult.discountAmount());
        order.setPointsUsed(benefitResult.pointsUsed());
        order.setUserCouponId(benefitResult.userCoupon() == null ? null : benefitResult.userCoupon().getId());
        order.setPayableAmount(originalAmount.subtract(benefitResult.discountAmount()));
        order.setTotalAmount(order.getPayableAmount());
        order.setPointsAwarded(0);

        if (request.getId() == null) {
            order.setOrderNo(buildOrderNo());
            persistItems(request.getItems(), order);
            marketingService.consumeOrderBenefits(order, benefitResult.userCoupon(), benefitResult.pointsUsed());
            customerOrderMapper.updateById(order);
        } else {
            CustomerOrder exist = customerOrderMapper.selectById(request.getId());
            if (exist == null) {
                throw new BusinessException("订单不存在");
            }
            assertOwner(exist, currentUser);
            marketingService.rollbackOrderBenefits(exist);
            order.setId(request.getId());
            order.setOrderNo(exist.getOrderNo());
            order.setUserId(exist.getUserId());
            if (isCustomerUser(currentUser)) {
                order.setCustomerName(exist.getCustomerName());
                order.setPayStatus(exist.getPayStatus());
                order.setOrderStatus(exist.getOrderStatus());
            } else {
                // 管理端只允许显式推进既有状态，不接受新增订单的客户端状态覆盖。
                order.setPayStatus(StringUtils.hasText(request.getPayStatus()) ? request.getPayStatus() : exist.getPayStatus());
                order.setOrderStatus(StringUtils.hasText(request.getOrderStatus()) ? request.getOrderStatus() : exist.getOrderStatus());
            }
            restoreInventory(request.getId());
            customerOrderMapper.updateById(order);
            customerOrderItemMapper.delete(new LambdaQueryWrapper<CustomerOrderItem>().eq(CustomerOrderItem::getOrderId, request.getId()));
            persistItems(request.getItems(), order);
            marketingService.consumeOrderBenefits(order, benefitResult.userCoupon(), benefitResult.pointsUsed());
            customerOrderMapper.updateById(order);
        }
        return customerOrderMapper.selectById(order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AuthUser currentUser = SecurityUtils.getCurrentUser();
        CustomerOrder order = customerOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        assertOwner(order, currentUser);
        marketingService.rollbackOrderBenefits(order);
        restoreInventory(id);
        customerOrderMapper.deleteById(id);
        customerOrderItemMapper.delete(new LambdaQueryWrapper<CustomerOrderItem>().eq(CustomerOrderItem::getOrderId, id));
    }

    private BigDecimal calculateOrderAmount(List<OrderItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : items) {
            Drink drink = drinkMapper.selectById(item.getDrinkId());
            if (drink == null || drink.getStatus() == 0) {
                throw new BusinessException("存在不可售卖的饮品");
            }
            if (drink.getStock() < item.getQuantity()) {
                throw new BusinessException(drink.getName() + " 库存不足");
            }
            total = total.add(drink.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }

    private void persistItems(List<OrderItemRequest> items, CustomerOrder order) {
        if (order.getId() == null) {
            customerOrderMapper.insert(order);
        }
        for (OrderItemRequest item : items) {
            Drink drink = drinkMapper.selectById(item.getDrinkId());
            CustomerOrderItem orderItem = new CustomerOrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setDrinkId(drink.getId());
            orderItem.setDrinkName(drink.getName());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setUnitPrice(drink.getPrice());
            orderItem.setSubtotal(drink.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            customerOrderItemMapper.insert(orderItem);

            drink.setStock(drink.getStock() - item.getQuantity());
            drink.setSales(drink.getSales() + item.getQuantity());
            drinkMapper.updateById(drink);
        }
    }

    private void restoreInventory(Long orderId) {
        List<CustomerOrderItem> oldItems = customerOrderItemMapper.selectList(
                new LambdaQueryWrapper<CustomerOrderItem>().eq(CustomerOrderItem::getOrderId, orderId)
        );
        for (CustomerOrderItem oldItem : oldItems) {
            Drink drink = drinkMapper.selectById(oldItem.getDrinkId());
            if (drink != null) {
                drink.setStock(drink.getStock() + oldItem.getQuantity());
                drink.setSales(Math.max(0, drink.getSales() - oldItem.getQuantity()));
                drinkMapper.updateById(drink);
            }
        }
    }

    private String buildOrderNo() {
        return "CC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private boolean isCustomerUser(AuthUser currentUser) {
        return currentUser.getRoles() != null && currentUser.getRoles().contains("user");
    }

    private void assertOwner(CustomerOrder order, AuthUser currentUser) {
        if (isCustomerUser(currentUser) && !currentUser.getId().equals(order.getUserId())) {
            throw new BusinessException("只能操作自己的订单记录");
        }
    }
}
