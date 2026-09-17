<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import { login } from '@/api/auth'
import AppShell from '@/components/AppShell.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: (route.query.username as string) ?? '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const loginVO = await login(form)
    userStore.setLogin(loginVO)
    ElMessage.success('登录成功')
    await router.replace((route.query.redirect as string) || '/articles')
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
        <div class="brand-kicker">READ · WRITE · RESPOND</div>
        <h1>写作是一张持续展开的索引</h1>
        <p>记录想法，整理判断，与读者建立长期的交流。</p>
      </section>

      <section class="auth-form-panel">
        <div class="form-heading">
          <p class="brand-kicker">WELCOME BACK</p>
          <h2>欢迎回来</h2>
          <p>继续阅读和写作</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" aria-label="用户名" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="密码"
              aria-label="密码"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="footer-link">
          还没有账号？<router-link to="/register">去注册</router-link>
        </div>
      </section>
    </div>
  </AppShell>
</template>

<style scoped>
.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  min-height: 560px;
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
  font-size: 42px;
  line-height: 1.3;
}

.auth-brand p {
  max-width: 330px;
  color: #b8c9d7;
  line-height: 1.8;
}

.auth-form-panel {
  align-self: center;
  padding: 54px 72px;
}

.form-heading h2 {
  margin: 8px 0 6px;
  font-family: var(--blog-font-display);
  font-size: 34px;
}

.form-heading p:last-child {
  margin: 0 0 28px;
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
    font-size: 27px;
  }

  .auth-brand p {
    display: none;
  }

  .auth-form-panel {
    padding: 34px 22px 42px;
  }
}
</style>
