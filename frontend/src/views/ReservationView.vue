<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { deleteReservation, fetchReservations, fetchTables, saveReservation } from '../api/modules'
import { ElMessage } from 'element-plus'
import { hasPermission, hasRole } from '../utils/auth'
import { resourceChanged, syncBus } from '../utils/syncBus'

const list = ref([])
const tables = ref([])
const drawerVisible = ref(false)
const query = reactive({ status: '' })
const page = reactive({ current: 1, size: 5, total: 0 })
const canWrite = hasPermission('reservation:write')
const canDelete = hasPermission('reservation:delete')
const isCustomerUser = hasRole('user')
const pageTitle = computed(() => isCustomerUser ? '我的预约' : '预约管理')
const pageDescription = computed(() => isCustomerUser ? '查看并管理你自己的到店预约记录。' : '面向顾客预约、桌台分配和到店状态流转。')
const createLabel = computed(() => isCustomerUser ? '新增预约' : '新增预约')
const form = reactive({
  id: null,
  customerName: '',
  customerPhone: '',
  guestCount: 2,
  reservationTime: '',
  tableId: null,
  status: '待到店',
  note: ''
})

const resetForm = () => {
  Object.assign(form, {
    id: null,
    customerName: '',
    customerPhone: '',
    guestCount: 2,
    reservationTime: '',
    tableId: null,
    status: '待到店',
    note: ''
  })
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const loadData = async () => {
  const [reservationData, tableData] = await Promise.all([
    fetchReservations({ ...query, current: page.current, size: page.size }),
    fetchTables({ current: 1, size: 100 })
  ])
  list.value = reservationData.records
  page.total = reservationData.total
  tables.value = tableData.records
}

const submit = async () => {
  await saveReservation(form)
  ElMessage.success('保存成功')
  resetForm()
  drawerVisible.value = false
  resourceChanged('reservations')
  resourceChanged('tables')
  loadData()
}

const editRow = (row) => {
  Object.assign(form, row)
  drawerVisible.value = true
}

const removeRow = async (id) => {
  await deleteReservation(id)
  ElMessage.success('删除成功')
  resourceChanged('reservations')
  resourceChanged('tables')
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
        <el-select v-model="query.status" placeholder="预约状态" clearable style="width: 160px">
          <el-option label="待到店" value="待到店" />
          <el-option label="已确认" value="已确认" />
          <el-option label="已取消" value="已取消" />
        </el-select>
        <el-button type="primary" @click="page.current = 1; loadData()">查询</el-button>
      </div>
        <el-table :data="list" border>
        <el-table-column prop="customerName" label="客户" />
        <el-table-column prop="customerPhone" label="手机号" />
        <el-table-column prop="guestCount" label="人数" width="80" />
        <el-table-column prop="reservationTime" label="预约时间" min-width="170" />
        <el-table-column prop="status" label="状态" />
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

    <el-drawer v-if="canWrite" v-model="drawerVisible" :title="form.id ? '编辑预约' : '新增预约'" size="520px">
      <el-form label-position="top">
        <el-form-item v-if="!isCustomerUser" label="客户"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.customerPhone" /></el-form-item>
        <el-form-item label="人数"><el-input-number v-model="form.guestCount" :min="1" /></el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="form.reservationTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="桌台">
          <el-select v-model="form.tableId" placeholder="选择桌台">
            <el-option v-for="item in tables" :key="item.id" :label="`${item.tableNo} / ${item.areaName}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isCustomerUser" label="状态"><el-input v-model="form.status" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="3" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit">保存</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </section>
</template>
