<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { deleteActivity, fetchActivities, saveActivity } from '../api/modules'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission, hasRole } from '../utils/auth'
import { resourceChanged, syncBus } from '../utils/syncBus'

const list = ref([])
const page = reactive({ current: 1, size: 8, total: 0 })
const query = reactive({ status: '', activityType: '' })
const drawerVisible = ref(false)
const canManage = hasPermission('activity:write')
const isCustomerUser = hasRole('user')
const form = reactive({
  id: null,
  name: '',
  activityType: '限时促销',
  bannerImage: '',
  startTime: '',
  endTime: '',
  status: 1,
  description: '',
  rules: [{ ruleType: '满减', ruleValue: '', targetType: '全场', targetId: null, sortOrder: 1 }]
})

const pageTitle = computed(() => isCustomerUser ? '活动专区' : '活动营销')
const pageDescription = computed(() => isCustomerUser
  ? '查看当前可参与的猫咖活动、会员玩法和权益说明。'
  : '维护营销活动、规则配置和有效期。')

const resetForm = () => {
  Object.assign(form, {
    id: null,
    name: '',
    activityType: '限时促销',
    bannerImage: '',
    startTime: '',
    endTime: '',
    status: 1,
    description: '',
    rules: [{ ruleType: '满减', ruleValue: '', targetType: '全场', targetId: null, sortOrder: 1 }]
  })
}

const loadData = async () => {
  const data = await fetchActivities({
    current: page.current,
    size: page.size,
    status: query.status === '' ? undefined : query.status,
    activityType: query.activityType || undefined
  })
  list.value = data.records
  page.total = data.total
}

const openCreate = () => {
  resetForm()
  drawerVisible.value = true
}

const addRule = () => {
  form.rules.push({
    ruleType: '满减',
    ruleValue: '',
    targetType: '全场',
    targetId: null,
    sortOrder: form.rules.length + 1
  })
}

const editRow = (row) => {
  Object.assign(form, {
    ...row,
    rules: row.rules?.length ? row.rules.map((item) => ({ ...item })) : [{ ruleType: '满减', ruleValue: '', targetType: '全场', targetId: null, sortOrder: 1 }]
  })
  drawerVisible.value = true
}

const submit = async () => {
  await saveActivity(form)
  ElMessage.success('活动保存成功')
  drawerVisible.value = false
  resetForm()
  resourceChanged('activities')
  loadData()
}

const removeRow = async (id) => {
  await ElMessageBox.confirm('确认删除这条活动吗？', '删除活动', { type: 'warning' })
  await deleteActivity(id)
  ElMessage.success('活动删除成功')
  resourceChanged('activities')
  loadData()
}

let stopSync
onMounted(() => {
  loadData()
  stopSync = syncBus.on('resource-changed', ({ resource }) => {
    if (resource === 'activities') loadData()
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
      <el-button v-if="canManage" type="primary" @click="openCreate">新增活动</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="query.activityType" placeholder="活动类型" clearable style="width: 180px">
          <el-option label="限时促销" value="限时促销" />
          <el-option label="会员拉新" value="会员拉新" />
          <el-option label="节日活动" value="节日活动" />
        </el-select>
        <el-select v-model="query.status" placeholder="活动状态" clearable style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="page.current = 1; loadData()">查询</el-button>
      </div>

      <div class="list-box">
        <div v-for="item in list" :key="item.id" class="list-row" style="align-items: flex-start; gap: 16px;">
          <div style="display: grid; gap: 6px; flex: 1;">
            <strong>{{ item.name }}</strong>
            <small>类型：{{ item.activityType }}，有效期：{{ item.startTime }} 至 {{ item.endTime }}</small>
            <small>{{ item.description || '暂无活动说明' }}</small>
            <small>规则：{{ item.rules?.map((rule) => `${rule.ruleType} - ${rule.ruleValue}`).join('；') || '暂无规则' }}</small>
          </div>
          <div style="display: grid; gap: 8px; justify-items: end;">
            <span class="record-status" :class="item.status === 1 ? 'is-active' : 'is-muted'">{{ item.status === 1 ? '进行中' : '已停用' }}</span>
            <div v-if="canManage" class="table-actions">
              <el-button link type="primary" @click="editRow(item)">编辑</el-button>
              <el-button link type="danger" @click="removeRow(item.id)">删除</el-button>
            </div>
          </div>
        </div>
      </div>

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

    <el-drawer v-if="canManage" v-model="drawerVisible" :title="form.id ? '编辑活动' : '新增活动'" size="560px">
      <el-form label-position="top">
        <el-form-item label="活动名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="活动类型">
          <el-select v-model="form.activityType">
            <el-option label="限时促销" value="限时促销" />
            <el-option label="会员拉新" value="会员拉新" />
            <el-option label="节日活动" value="节日活动" />
          </el-select>
        </el-form-item>
        <el-form-item label="Banner 图片"><el-input v-model="form.bannerImage" placeholder="可填写活动头图 URL" /></el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="活动说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="活动状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <div class="list-box" style="margin-bottom: 16px;">
          <div v-for="(rule, index) in form.rules" :key="index" class="panel" style="padding: 16px;">
            <div class="toolbar" style="margin-bottom: 12px;">
              <strong>规则 {{ index + 1 }}</strong>
              <el-button v-if="form.rules.length > 1" @click="form.rules.splice(index, 1)">删除规则</el-button>
            </div>
            <el-form-item label="规则类型">
              <el-select v-model="rule.ruleType">
                <el-option label="满减" value="满减" />
                <el-option label="折扣" value="折扣" />
                <el-option label="积分奖励" value="积分奖励" />
                <el-option label="赠券" value="赠券" />
              </el-select>
            </el-form-item>
            <el-form-item label="规则内容"><el-input v-model="rule.ruleValue" placeholder="例如：满50减12 / 支付返双倍积分" /></el-form-item>
            <el-form-item label="作用对象">
              <el-select v-model="rule.targetType">
                <el-option label="全场" value="全场" />
                <el-option label="会员" value="会员" />
                <el-option label="饮品" value="饮品" />
                <el-option label="订单" value="订单" />
              </el-select>
            </el-form-item>
            <el-form-item label="排序"><el-input-number v-model="rule.sortOrder" :min="1" /></el-form-item>
          </div>
        </div>

        <el-form-item>
          <el-button @click="addRule">新增规则</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </section>
</template>
