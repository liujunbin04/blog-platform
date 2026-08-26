<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
// 从注册页跳转过来时预填用户名
const form = reactive({
  username: (route.query.username as string) ?? '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  // validate() 在校验失败时 reject：捕获并早退，字段错误提示由表单自行展示
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const loginVO = await login(form)
    userStore.setLogin(loginVO)
    ElMessage.success('登录成功')
    await router.replace((route.query.redirect as string) || '/articles')
  } catch {
    // 失败提示已由响应拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="auth-title">登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="密码"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="footer-link">还没有账号？<router-link to="/register">去注册</router-link></div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 80px;
}
.auth-card {
  width: 400px;
}
.auth-title {
  text-align: center;
  margin-top: 0;
}
.submit-btn {
  width: 100%;
}
.footer-link {
  text-align: center;
  font-size: 14px;
}
</style>
