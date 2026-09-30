<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  deleteCouponTemplate,
  fetchCouponTemplates,
  fetchUserCoupons,
  issueCoupon,
  receiveCoupon,
  saveCouponTemplate
} from '../api/modules'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission, hasRole } from '../utils/auth'
import { resourceChanged, syncBus } from '../utils/syncBus'

const templates = ref([])
const userCoupons = ref([])
const templatePage = reactive({ current: 1, size: 6, total: 0 })
const couponPage = reactive({ current: 1, size: 6, total: 0 })
const templateQuery = reactive({ keyword: '', status: '' })
const couponQuery = reactive({ status: '' })
const drawerVisible = ref(false)
const isCustomerUser = hasRole('user')
const canManageTemplates = hasRole('admin')
const canReceive = hasRole('user') && hasPermission('coupon:write')
const form = reactive({
  id: null,
  name: '',
  description: '',
  thresholdAmount: 0,
  discountAmount: 0,
  pointCost: 0,
  totalCount: 100,
  validityType: '固定天数',
  validDays: 7,
  startTime: '',
  endTime: '',
  status: 1
})

const pageTitle = computed(() => isCustomerUser ? '我的优惠券' : '优惠券中心')
const pageDescription = computed(() => isCustomerUser
  ? '领取、查看和使用可抵扣订单金额的会员优惠券。'
  : '管理优惠券模板、定向发放和查看用户券包。')

const resetForm = () => {
  Object.assign(form, {
    id: null,
    name: '',
    description: '',
    thresholdAmount: 0,
    discountAmount: 0,
    pointCost: 0,
    totalCount: 100,
    validityType: '固定天数',
    validDays: 7,
    startTime: '',
    endTime: '',
    status: 1
  })
}

const loadData = async () => {
  const [templateData, couponData] = await Promise.all([
    fetchCouponTemplates({
      current: templatePage.current,
      size: templatePage.size,
      keyword: templateQuery.keyword || undefined,
      status: templateQuery.status === '' ? undefined : templateQuery.status
    }),
    fetchUserCoupons({
      current: couponPage.current,
      size: couponPage.size,
      status: couponQuery.status || undefined
    })
  ])
  templates.value = templateData.records
  templatePage.total = templateData.total
  userCoupons.value = couponData.records
  couponPage.total = couponData.total
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const editRow = (row) => {
  Object.assign(form, row)
  drawerVisible.value = true
}

const submit = async () => {
  await saveCouponTemplate(form)
  ElMessage.success('优惠券模板保存成功')
  drawerVisible.value = false
  resetForm()
  resourceChanged('coupons')
  loadData()
}

const removeRow = async (id) => {
  await ElMessageBox.confirm('确认删除该优惠券模板吗？', '删除确认', { type: 'warning' })
  await deleteCouponTemplate(id)
  ElMessage.success('优惠券模板删除成功')
  resourceChanged('coupons')
  loadData()
}

const sendCoupon = async (templateId) => {
  const { value } = await ElMessageBox.prompt('请输入要发放的用户 ID', '发放优惠券', {
    inputPlaceholder: '例如：3',
    inputValidator: (text) => (/^\d+$/.test(text) ? true : '请输入有效的数字用户 ID')
  }).catch(() => ({ value: null }))
  if (!value) {
    return
  }
  await issueCoupon(templateId, { userId: Number(value) })
  ElMessage.success('优惠券发放成功')
  resourceChanged('coupons')
  resourceChanged('points')
  loadData()
}

const claimCoupon = async (templateId) => {
  await receiveCoupon(templateId)
  ElMessage.success('优惠券领取成功')
  resourceChanged('coupons')
  resourceChanged('points')
  loadData()
}

let stopSync
onMounted(() => {
  loadData()
  stopSync = syncBus.on('resource-changed', ({ resource }) => {
    if (resource === 'coupons' || resource === 'points') loadData()
  })
})
onBeforeUnmount(() => stopSync?.())
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <el-button v-if="canManageTemplates" type="primary" @click="openCreate">新增模板</el-button>
    </div>

    <div class="panel-grid">
      <div class="panel">
        <div class="toolbar">
          <el-input v-model="templateQuery.keyword" placeholder="搜索优惠券名称" clearable />
          <el-select v-model="templateQuery.status" placeholder="模板状态" clearable style="width: 140px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="templatePage.current = 1; loadData()">查询</el-button>
        </div>

        <el-table :data="templates" border>
          <el-table-column prop="name" label="模板名称" min-width="140" />
          <el-table-column prop="thresholdAmount" label="门槛" width="90" />
          <el-table-column prop="discountAmount" label="优惠" width="90" />
          <el-table-column prop="pointCost" label="积分成本" width="110" />
          <el-table-column prop="issuedCount" label="已发放" width="90" />
          <el-table-column prop="totalCount" label="总量" width="90" />
          <el-table-column prop="validityType" label="有效期类型" width="120" />
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button v-if="canManageTemplates" link type="primary" @click="editRow(row)">编辑</el-button>
                <el-button v-if="canManageTemplates" link type="primary" @click="sendCoupon(row.id)">发放</el-button>
                <el-button v-if="canReceive" link type="primary" @click="claimCoupon(row.id)">领取</el-button>
                <el-button v-if="canManageTemplates" link type="danger" @click="removeRow(row.id)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="templatePage.current"
            v-model:page-size="templatePage.size"
            layout="total, prev, pager, next"
            :total="templatePage.total"
            @current-change="loadData"
          />
        </div>
      </div>

      <div class="panel">
        <div class="toolbar">
          <el-select v-model="couponQuery.status" placeholder="券状态" clearable style="width: 140px">
            <el-option label="未使用" value="未使用" />
            <el-option label="已使用" value="已使用" />
            <el-option label="已过期" value="已过期" />
          </el-select>
          <el-button type="primary" @click="couponPage.current = 1; loadData()">查询</el-button>
        </div>

        <div class="list-box">
          <div v-for="item in userCoupons" :key="item.id" class="list-row" style="align-items: flex-start;">
            <div style="display: grid; gap: 4px;">
              <strong>{{ item.couponName }}</strong>
              <small>所属用户：{{ item.username }}</small>
              <small>门槛 ¥{{ item.thresholdAmount }}，优惠 ¥{{ item.discountAmount }}</small>
              <small>来源：{{ item.sourceType }}，到期：{{ item.expireTime || '-' }}</small>
            </div>
            <span class="record-status" :class="item.status === '未使用' ? 'is-active' : 'is-muted'">{{ item.status }}</span>
          </div>
        </div>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="couponPage.current"
            v-model:page-size="couponPage.size"
            layout="total, prev, pager, next"
            :total="couponPage.total"
            @current-change="loadData"
          />
        </div>
      </div>
    </div>

    <el-drawer v-if="canManageTemplates" v-model="drawerVisible" :title="form.id ? '编辑优惠券模板' : '新增优惠券模板'" size="560px">
      <el-form label-position="top">
        <el-form-item label="模板名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="使用门槛"><el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="优惠金额"><el-input-number v-model="form.discountAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="兑换所需积分"><el-input-number v-model="form.pointCost" :min="0" /></el-form-item>
        <el-form-item label="发放总量"><el-input-number v-model="form.totalCount" :min="1" /></el-form-item>
        <el-form-item label="有效期类型">
          <el-select v-model="form.validityType">
            <el-option label="固定天数" value="固定天数" />
            <el-option label="固定时间" value="固定时间" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.validityType === '固定天数'" label="有效天数">
          <el-input-number v-model="form.validDays" :min="1" />
        </el-form-item>
        <template v-else>
          <el-form-item label="开始时间">
            <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
          </el-form-item>
        </template>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit">保存</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </section>
</template>
