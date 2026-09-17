<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import { register } from '@/api/auth'
import AppShell from '@/components/AppShell.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度需在 3-20 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度需在 6-20 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value === form.password) callback()
        else callback(new Error('两次输入的密码不一致'))
      },
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      email: form.email || undefined,
    })
    ElMessage.success('注册成功，请登录')
    await router.push({ path: '/login', query: { username: form.username } })
  } catch {
    // 错误由请求拦截器统一提示。
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AppShell variant="auth">
    <div class="auth-layout">
      <section class="auth-brand" aria-label="博客介绍">
        <div class="brand-kicker">START YOUR INDEX</div>
        <h1>从第一篇文章开始建立自己的知识索引</h1>
        <p>公开写作、持续记录，并与读者保持交流。</p>
      </section>

      <section class="auth-form-panel">
        <div class="form-heading">
          <p class="brand-kicker">CREATE ACCOUNT</p>
          <h2>创建账号</h2>
          <p>开始你的写作与阅读记录</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名（3-20 个字符）" aria-label="用户名" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="密码（6-20 个字符）"
              aria-label="密码"
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="确认密码"
              aria-label="确认密码"
            />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input v-model="form.nickname" placeholder="昵称（选填）" aria-label="昵称" />
          </el-form-item>
          <el-form-item prop="email">
            <el-input v-model="form.email" placeholder="邮箱（选填）" aria-label="邮箱" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
              注册
            </el-button>
          </el-form-item>
        </el-form>

        <div class="footer-link">
          已有账号？<router-link to="/login">去登录</router-link>
        </div>
      </section>
    </div>
  </AppShell>
</template>

<style scoped>
.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  min-height: 620px;
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.auth-brand {
  padding: 54px 48px;
  background: var(--blog-ink);
  color: var(--blog-paper);
}

.brand-kicker {
  color: var(--blog-red);
  font-family: var(--blog-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.auth-brand .brand-kicker {
  color: #9fc0b4;
}

.auth-brand h1 {
  margin: 18px 0 22px;
  font-family: var(--blog-font-display);
  font-size: 40px;
  line-height: 1.3;
}

.auth-brand p {
  max-width: 330px;
  color: #b8c9d7;
  line-height: 1.8;
}

.auth-form-panel {
  align-self: center;
  padding: 46px 72px;
}

.form-heading h2 {
  margin: 8px 0 6px;
  font-family: var(--blog-font-display);
  font-size: 34px;
}

.form-heading p:last-child {
  margin: 0 0 24px;
  color: var(--blog-muted);
}

.submit-btn {
  width: 100%;
}

.footer-link {
  color: var(--blog-muted);
  font-size: 13px;
  text-align: center;
}

.footer-link a {
  color: var(--blog-red);
}

@media (max-width: 767px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    padding: 26px 22px;
  }

  .auth-brand h1 {
    margin: 10px 0 0;
    font-size: 26px;
  }

  .auth-brand p {
    display: none;
  }

  .auth-form-panel {
    padding: 34px 22px 42px;
  }
}
</style>
