<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteTable, fetchTables, saveTable } from '../api/modules'
import { ElMessage } from 'element-plus'
import { hasPermission, hasRole } from '../utils/auth'

const list = ref([])
const drawerVisible = ref(false)
const query = reactive({ status: '' })
const page = reactive({ current: 1, size: 5, total: 0 })
const canWrite = hasPermission('table:write')
const canDelete = hasPermission('table:delete')
// 顾客端只有 table:read，看到的是桌位占用情况而非管理入口，措辞需与菜单、顶栏标题保持一致
const isCustomerUser = hasRole('user')
const pageTitle = computed(() => (isCustomerUser ? '桌台状态' : '桌台管理'))
const pageDescription = computed(() =>
  isCustomerUser ? '查看门店区域与各桌位的实时占用状态。' : '维护门店区域、桌位容量与当前桌台状态。'
)
const form = reactive({
  id: null,
  tableNo: '',
  capacity: 2,
  areaName: '',
  status: '空闲',
  remark: ''
})

const resetForm = () => {
  Object.assign(form, {
    id: null,
    tableNo: '',
    capacity: 2,
    areaName: '',
    status: '空闲',
    remark: ''
  })
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const loadData = async () => {
  const data = await fetchTables({ ...query, current: page.current, size: page.size })
  list.value = data.records
  page.total = data.total
}

const submit = async () => {
  await saveTable(form)
  ElMessage.success('保存成功')
  resetForm()
  drawerVisible.value = false
  loadData()
}

const editRow = (row) => {
  Object.assign(form, row)
  drawerVisible.value = true
}

const removeRow = async (id) => {
  await deleteTable(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新增桌台</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="筛选状态" clearable style="width: 140px">
          <el-option label="空闲" value="空闲" />
          <el-option label="已预订" value="已预订" />
          <el-option label="清洁中" value="清洁中" />
        </el-select>
        <el-button type="primary" @click="page.current = 1; loadData()">查询</el-button>
      </div>
      <el-table :data="list" border>
        <el-table-column prop="tableNo" label="桌号" />
        <el-table-column prop="capacity" label="人数" width="80" />
        <el-table-column prop="areaName" label="区域" />
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

    <el-drawer v-if="canWrite" v-model="drawerVisible" :title="form.id ? '编辑桌台' : '新增桌台'" size="520px">
      <el-form label-position="top">
        <el-form-item label="桌号"><el-input v-model="form.tableNo" /></el-form-item>
        <el-form-item label="人数"><el-input-number v-model="form.capacity" :min="1" /></el-form-item>
        <el-form-item label="区域"><el-input v-model="form.areaName" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="form.status" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit">保存</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </section>
</template>
