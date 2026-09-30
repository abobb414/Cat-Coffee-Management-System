<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { syncBus } from '../utils/syncBus'
import { fetchDashboard } from '../api/modules'

const dashboard = ref({
  catCount: 0,
  reservationCount: 0,
  activeOrderCount: 0,
  todayRevenue: 0,
  hotDrinks: [],
  reservationStatusSummary: [],
  weeklyRevenueTrend: [],
  orderStatusSummary: []
})

const revenueChartRef = ref(null)
const statusChartRef = ref(null)
let revenueChart
let statusChart
const loading = ref(false)

const loadData = async () => {
  if (loading.value) return
  loading.value = true
  try {
    dashboard.value = await fetchDashboard()
  } finally {
    loading.value = false
  }
  await nextTick()
  renderCharts()
}

const renderCharts = () => {
  if (revenueChartRef.value) {
    revenueChart ??= echarts.init(revenueChartRef.value)
    revenueChart.setOption({
      grid: { left: 36, right: 18, top: 30, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: dashboard.value.weeklyRevenueTrend.map((item) => item.name)
      },
      yAxis: { type: 'value' },
      series: [{
        type: 'line',
        smooth: true,
        data: dashboard.value.weeklyRevenueTrend.map((item) => item.value),
        areaStyle: { color: 'rgba(214, 123, 66, 0.18)' },
        lineStyle: { color: '#d67b42', width: 3 },
        itemStyle: { color: '#d67b42' }
      }]
    })
  }

  if (statusChartRef.value) {
    statusChart ??= echarts.init(statusChartRef.value)
    statusChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['48%', '72%'],
        data: dashboard.value.orderStatusSummary.map((item) => ({ name: item.name, value: item.value })),
        label: { color: '#5b4b3e' }
      }]
    })
  }
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
  revenueChart?.dispose()
  statusChart?.dispose()
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h2>经营看板</h2>
        <p>围绕猫咪、座位、预约与订单的运营总览。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadData">刷新数据</el-button>
    </div>

    <div class="card-grid">
      <div class="metric-card">
        <h3>猫咪总数</h3>
        <strong>{{ dashboard.catCount }}</strong>
      </div>
      <div class="metric-card">
        <h3>预约总量</h3>
        <strong>{{ dashboard.reservationCount }}</strong>
      </div>
      <div class="metric-card">
        <h3>活跃订单</h3>
        <strong>{{ dashboard.activeOrderCount }}</strong>
      </div>
      <div class="metric-card">
        <h3>今日营收</h3>
        <strong>¥{{ dashboard.todayRevenue }}</strong>
      </div>
    </div>

    <div class="panel-grid">
      <div class="panel">
        <h3>近 7 天营收趋势</h3>
        <div ref="revenueChartRef" class="chart-box"></div>
      </div>

      <div class="panel">
        <h3>订单状态分布</h3>
        <div ref="statusChartRef" class="chart-box"></div>
      </div>
    </div>

    <div class="panel-grid" style="margin-top: 16px;">
      <div class="panel">
        <h3>热销饮品</h3>
        <div class="list-box">
          <div v-for="item in dashboard.hotDrinks" :key="item.name" class="list-row">
            <span>{{ item.name }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>

      <div class="panel">
        <h3>预约状态分布</h3>
        <div class="list-box">
          <div v-for="item in dashboard.reservationStatusSummary" :key="item.name" class="list-row">
            <span>{{ item.name }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
