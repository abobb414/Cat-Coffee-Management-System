<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { syncBus } from '../utils/syncBus'
import { fetchPointFlows, fetchPointSummary } from '../api/modules'
import { hasRole } from '../utils/auth'

const summary = ref({ userId: null, nickname: '', currentPoints: 0 })
const list = ref([])
const query = reactive({ userId: '', changeType: '' })
const page = reactive({ current: 1, size: 8, total: 0 })
const isCustomerUser = hasRole('user')

const pageTitle = computed(() => isCustomerUser ? '积分中心' : '会员积分')
const pageDescription = computed(() => isCustomerUser
  ? '查看积分余额、获取记录与订单抵扣明细。'
  : '按会员维度查看积分余额和积分流水。')

const loadData = async () => {
  const [summaryData, flowData] = await Promise.all([
    fetchPointSummary(),
    fetchPointFlows({
      current: page.current,
      size: page.size,
      userId: isCustomerUser ? undefined : (query.userId ? Number(query.userId) : undefined),
      changeType: query.changeType || undefined
    })
  ])
  summary.value = summaryData
  list.value = flowData.records
  page.total = flowData.total
}

let stopSync
const refreshOnFocus = () => loadData()
onMounted(() => {
  loadData()
  stopSync = syncBus.on('resource-changed', ({ resource }) => {
    if (resource === 'orders' || resource === 'points' || resource === 'coupons') loadData()
  })
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
      <el-button type="primary" @click="loadData">刷新数据</el-button>
    </div>

    <div class="card-grid" style="grid-template-columns: repeat(3, minmax(0, 1fr));">
      <div class="metric-card">
        <h3>当前积分</h3>
        <strong>{{ summary.currentPoints || 0 }}</strong>
      </div>
      <div class="metric-card">
        <h3>会员昵称</h3>
        <strong style="font-size: 22px;">{{ summary.nickname || '-' }}</strong>
      </div>
      <div class="metric-card">
        <h3>会员编号</h3>
        <strong style="font-size: 22px;">{{ summary.userId || '-' }}</strong>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input
          v-if="!isCustomerUser"
          v-model="query.userId"
          placeholder="按用户 ID 查询"
          clearable
          style="width: 180px"
        />
        <el-select v-model="query.changeType" placeholder="积分类型" clearable style="width: 180px">
          <el-option label="发放" value="发放" />
          <el-option label="使用" value="使用" />
          <el-option label="兑换" value="兑换" />
          <el-option label="返还" value="返还" />
          <el-option label="扣回" value="扣回" />
        </el-select>
        <el-button type="primary" @click="page.current = 1; loadData()">查询</el-button>
      </div>

      <el-table :data="list" border>
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="changeType" label="类型" width="90" />
        <el-table-column prop="changeAmount" label="变动" width="100" />
        <el-table-column prop="balanceAfter" label="变动后余额" width="120" />
        <el-table-column prop="bizType" label="业务类型" width="110" />
        <el-table-column prop="bizId" label="业务ID" width="100" />
        <el-table-column prop="remark" label="备注" min-width="220" />
        <el-table-column prop="createTime" label="时间" min-width="170" />
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
  </section>
</template>
