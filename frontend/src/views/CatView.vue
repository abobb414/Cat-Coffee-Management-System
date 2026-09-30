<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteCat, fetchCats, saveCat, uploadFile } from '../api/modules'
import { ElMessage } from 'element-plus'
import { hasPermission, hasRole } from '../utils/auth'
import { resourceChanged, syncBus } from '../utils/syncBus'
import { onBeforeUnmount } from 'vue'

const list = ref([])
const drawerVisible = ref(false)
const query = reactive({ keyword: '' })
const page = reactive({ current: 1, size: 5, total: 0 })
const canWrite = hasPermission('cat:write')
const canDelete = hasPermission('cat:delete')
const isCustomerUser = hasRole('user')
const pageTitle = computed(() => isCustomerUser ? '猫咪浏览' : '猫咪管理')
const pageDescription = computed(() => isCustomerUser ? '看看今天在店里的猫咪档案和互动状态。' : '维护店内猫咪档案、健康状态与领养状态。')
const form = reactive({
  id: null,
  name: '',
  breed: '',
  age: 1,
  gender: '母',
  healthStatus: '健康',
  personalityTag: '',
  adoptionStatus: '店内营业',
  feedingCost: 0,
  birthday: '',
  avatar: '',
  introduction: ''
})

const resetForm = () => {
  Object.assign(form, {
    id: null,
    name: '',
    breed: '',
    age: 1,
    gender: '母',
    healthStatus: '健康',
    personalityTag: '',
    adoptionStatus: '店内营业',
    feedingCost: 0,
    birthday: '',
    avatar: '',
    introduction: ''
  })
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const loadData = async () => {
  const data = await fetchCats({ ...query, current: page.current, size: page.size })
  list.value = data.records
  page.total = data.total
}

const submit = async () => {
  await saveCat(form)
  ElMessage.success('保存成功')
  resetForm()
  drawerVisible.value = false
  resourceChanged('cats')
  loadData()
}

const handleUpload = async (options) => {
  try {
    const data = new FormData()
    data.append('file', options.file)
    const result = await uploadFile(data, 'cat')
    form.avatar = result.url
    options.onSuccess?.(result)
    ElMessage.success('头像上传成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '头像上传失败')
    options.onError?.(error)
  }
}

const editRow = (row) => {
  Object.assign(form, row)
  drawerVisible.value = true
}

const removeRow = async (id) => {
  await deleteCat(id)
  ElMessage.success('删除成功')
  resourceChanged('cats')
  loadData()
}

let stopSync
onMounted(() => {
  loadData()
  stopSync = syncBus.on('resource-changed', ({ resource }) => {
    if (resource === 'cats') loadData()
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
      <el-button v-if="canWrite" type="primary" @click="openCreate">新增猫咪</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索猫咪名称/品种" clearable />
        <el-button type="primary" @click="page.current = 1; loadData()">查询</el-button>
      </div>

      <el-table :data="list" border>
        <el-table-column label="头像" width="88">
          <template #default="{ row }">
            <el-image v-if="row.avatar" :src="row.avatar" class="table-thumb" fit="cover" preview-teleported />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="breed" label="品种" />
        <el-table-column prop="age" label="年龄" width="80" />
        <el-table-column prop="healthStatus" label="健康" />
        <el-table-column prop="adoptionStatus" label="状态" />
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

    <el-drawer v-if="canWrite" v-model="drawerVisible" :title="form.id ? '编辑猫咪' : '新增猫咪'" size="520px">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="品种"><el-input v-model="form.breed" /></el-form-item>
        <el-form-item label="年龄"><el-input-number v-model="form.age" :min="0" /></el-form-item>
        <el-form-item label="性别"><el-input v-model="form.gender" /></el-form-item>
        <el-form-item label="健康"><el-input v-model="form.healthStatus" /></el-form-item>
        <el-form-item label="性格"><el-input v-model="form.personalityTag" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="form.adoptionStatus" /></el-form-item>
        <el-form-item label="月成本"><el-input-number v-model="form.feedingCost" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="生日"><el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="头像">
          <div class="upload-block">
            <el-upload :show-file-list="false" :http-request="handleUpload" accept="image/*">
              <el-button>上传图片</el-button>
            </el-upload>
            <el-image v-if="form.avatar" :src="form.avatar" class="upload-preview" fit="cover" preview-teleported />
          </div>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="form.introduction" type="textarea" :rows="3" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit">保存</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </section>
</template>
