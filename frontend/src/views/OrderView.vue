<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { deleteOrder, fetchDrinks, fetchOrders, fetchPointSummary, fetchTables, fetchUserCoupons, saveOrder } from '../api/modules'
import { ElMessage } from 'element-plus'
import { hasPermission, hasRole } from '../utils/auth'
import { resourceChanged, syncBus } from '../utils/syncBus'

const list = ref([])
const tables = ref([])
const drinks = ref([])
const coupons = ref([])
const pointSummary = ref({ currentPoints: 0 })
const drawerVisible = ref(false)
const query = reactive({ orderStatus: '', payStatus: '' })
const page = reactive({ current: 1, size: 5, total: 0 })
const canWrite = hasPermission('order:write')
const canDelete = hasPermission('order:delete')
const isCustomerUser = hasRole('user')
const pageTitle = computed(() => isCustomerUser ? '我的订单' : '订单管理')
const pageDescription = computed(() => isCustomerUser ? '查看你提交的饮品订单，并继续发起新的预定。' : '维护散客与预约关联订单，并自动扣减饮品库存。')
const createLabel = computed(() => isCustomerUser ? '新增订单' : '新增订单')
const form = reactive({
  id: null,
  customerName: '',
  reservationId: null,
  tableId: null,
  payStatus: '待支付',
  orderStatus: '待制作',
  pointsUsed: 0,
  userCouponId: null,
  remark: '',
  items: [{ drinkId: null, quantity: 1 }]
})

const resetForm = () => {
  Object.assign(form, {
    id: null,
    customerName: '',
    reservationId: null,
    tableId: null,
    payStatus: '待支付',
    orderStatus: '待制作',
    pointsUsed: 0,
    userCouponId: null,
    remark: '',
    items: [{ drinkId: null, quantity: 1 }]
  })
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const loadData = async () => {
  const requests = [
    fetchOrders({ ...query, current: page.current, size: page.size }),
    fetchTables({ current: 1, size: 100 }),
    fetchDrinks({ current: 1, size: 100, status: 1 })
  ]
  if (isCustomerUser) {
    requests.push(fetchPointSummary(), fetchUserCoupons({ current: 1, size: 100, status: '未使用' }))
  }
  const [orderData, tableData, drinkData, pointData, couponData] = await Promise.all(requests)
  list.value = orderData.records
  page.total = orderData.total
  tables.value = tableData.records
  drinks.value = drinkData.records
  if (isCustomerUser) {
    pointSummary.value = pointData
    coupons.value = couponData.records
  }
}

const addItem = () => {
  form.items.push({ drinkId: null, quantity: 1 })
}

const removeItem = (index) => {
  form.items.splice(index, 1)
}

const editRow = (row) => {
  if (row.payStatus === '已支付' || row.orderStatus === '已完成') {
    ElMessage.info('已支付或已完成订单不可重新编辑')
    return
  }
  ElMessage.info('订单编辑需要先加载明细，请通过新增订单创建调整后的订单')
}

const submit = async () => {
  await saveOrder(form)
  ElMessage.success('保存成功')
  resetForm()
  drawerVisible.value = false
  resourceChanged('orders')
  resourceChanged('drinks')
  resourceChanged('points')
  resourceChanged('coupons')
  loadData()
}

const removeRow = async (id) => {
  await deleteOrder(id)
  ElMessage.success('删除成功')
  resourceChanged('orders')
  resourceChanged('drinks')
  resourceChanged('points')
  resourceChanged('coupons')
  loadData()
}

let stopSync
const refreshOnFocus = () => loadData()
onMounted(() => {
  loadData()
  stopSync = syncBus.on('resource-changed', refreshOnFocus)
  window.addEventListener('focus', refreshOnFocus)
})
onBeforeUnmount(() => {
  stopSync?.()
  window.removeEventListener('focus', refreshOnFocus)
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">{{ createLabel }}</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="query.orderStatus" placeholder="订单状态" clearable style="width: 150px">
          <el-option label="待制作" value="待制作" />
          <el-option label="已完成" value="已完成" />
          <el-option label="已取消" value="已取消" />
        </el-select>
        <el-select v-model="query.payStatus" placeholder="支付状态" clearable style="width: 150px">
          <el-option label="待支付" value="待支付" />
          <el-option label="已支付" value="已支付" />
        </el-select>
        <el-button type="primary" @click="page.current = 1; loadData()">查询</el-button>
      </div>

      <el-table :data="list" border>
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column prop="customerName" label="客户" />
        <el-table-column prop="originalAmount" label="原价" width="90" />
        <el-table-column prop="discountAmount" label="优惠" width="90" />
        <el-table-column prop="payableAmount" label="应付" width="90" />
        <el-table-column prop="payStatus" label="支付" />
        <el-table-column prop="orderStatus" label="状态" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="canWrite" link type="primary" @click="editRow(row)">编辑</el-button>
              <el-button v-if="canDelete" link type="danger" @click="removeRow(row.id)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          layout="total, prev, pager, next"
          :total="page.total"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-drawer v-if="canWrite" v-model="drawerVisible" :title="form.id ? '编辑订单' : '新增订单'" size="560px">
      <el-form label-position="top">
        <el-form-item v-if="!isCustomerUser" label="客户"><el-input v-model="form.customerName" /></el-form-item>
        <div v-if="isCustomerUser" class="list-box" style="margin-bottom: 16px;">
          <div class="list-row">
            <span>当前可用积分</span>
            <strong>{{ pointSummary.currentPoints || 0 }}</strong>
          </div>
        </div>
        <el-form-item label="桌台">
          <el-select v-model="form.tableId" placeholder="选择桌台">
            <el-option v-for="item in tables" :key="item.id" :label="item.tableNo" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isCustomerUser" label="支付"><el-input v-model="form.payStatus" /></el-form-item>
        <el-form-item v-if="!isCustomerUser" label="状态"><el-input v-model="form.orderStatus" /></el-form-item>
        <el-form-item v-if="isCustomerUser" label="使用积分">
          <el-input-number v-model="form.pointsUsed" :min="0" :max="pointSummary.currentPoints || 0" />
        </el-form-item>
        <el-form-item v-if="isCustomerUser" label="选择优惠券">
          <el-select v-model="form.userCouponId" clearable placeholder="不使用优惠券">
            <el-option
              v-for="item in coupons"
              :key="item.id"
              :label="`${item.couponName} / 满${item.thresholdAmount}减${item.discountAmount}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>

        <div class="list-box" style="margin-bottom: 14px;">
          <div v-for="(item, index) in form.items" :key="index" class="list-row" style="align-items: flex-start;">
            <div style="display: grid; gap: 10px; flex: 1;">
              <el-select v-model="item.drinkId" placeholder="选择饮品">
                <el-option v-for="drink in drinks" :key="drink.id" :label="`${drink.name} / ￥${drink.price}`" :value="drink.id" />
              </el-select>
              <el-input-number v-model="item.quantity" :min="1" />
            </div>
          </div>
        </div>

        <el-form-item>
          <el-button @click="addItem">新增明细</el-button>
          <el-button type="primary" @click="submit">保存订单</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </section>
</template>
