<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '../api/modules'
import { getHomePath, setAuth } from '../utils/auth'
import { copyrightText } from '../utils/site'

const router = useRouter()
const activeTab = ref('login')

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const submitLogin = async () => {
  const data = await login(loginForm)
  setAuth(data)
  ElMessage.success('欢迎回来')
  router.push(getHomePath())
}

const submitRegister = async () => {
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }

  const data = await register({
    username: registerForm.username,
    nickname: registerForm.nickname,
    password: registerForm.password
  })
  setAuth(data)
  ElMessage.success('注册成功，已为你自动登录')
  router.push(getHomePath())
}
</script>

<template>
  <section class="login-shell">
    <div class="login-hero">
      <img class="login-logo" src="/assets/hot-chocolate.png" alt="热可可图标" />
      <div class="login-brand-copy">
        <h1>猫咖管理平台</h1>
        <p>Cat Coffee Console</p>
      </div>
    </div>

    <div class="login-card-wrap">
      <div class="login-card">
        <div class="login-switch">
          <button
            class="login-switch-btn"
            :class="{ active: activeTab === 'login' }"
            type="button"
            @click="activeTab = 'login'"
          >
            登录
          </button>
          <button
            class="login-switch-btn"
            :class="{ active: activeTab === 'register' }"
            type="button"
            @click="activeTab = 'register'"
          >
            注册
          </button>
        </div>

        <el-form v-if="activeTab === 'login'" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button class="login-submit" @click="submitLogin">登录系统</el-button>
        </el-form>

        <el-form v-else label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="registerForm.username" placeholder="4-20 位用户名" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="registerForm.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="registerForm.password" type="password" show-password placeholder="6-20 位密码" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
          </el-form-item>
          <el-button class="login-submit" @click="submitRegister">注册并进入系统</el-button>
        </el-form>
      </div>
    </div>

    <footer class="app-footer login-footer">
      {{ copyrightText }}
    </footer>
  </section>
</template>
