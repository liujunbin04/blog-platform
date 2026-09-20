<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { ArrowDown } from '@element-plus/icons-vue'

import HeaderNav from '@/components/HeaderNav.vue'
import HeaderNotifications from '@/components/HeaderNotifications.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')

const isHot = computed(
  () => route.path === '/articles' && route.query.sort === 'hot',
)

function goArticles() {
  router.push('/articles')
}

function goHot() {
  router.push({ path: '/articles', query: { sort: 'hot' } })
}

function onSearch() {
  const value = keyword.value.trim()
  router.push(value ? { path: '/articles', query: { keyword: value } } : { path: '/articles' })
}

function onUserCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    userStore.clearLogin()
    ElMessage.success('已退出登录')
    router.push('/articles')
  }
}

</script>

<template>
  <div class="app-header">
    <div class="header-inner">
      <HeaderNav
        v-model:keyword="keyword"
        :is-hot="isHot"
        @search="onSearch"
        @go-articles="goArticles"
        @go-hot="goHot"
      />

      <div class="actions">
        <template v-if="userStore.isLoggedIn">
          <HeaderNotifications />

          <el-button class="publish-button" type="primary" @click="router.push('/publish')">
            写文章
          </el-button>

          <el-dropdown @command="onUserCommand">
            <button class="user-trigger" type="button" aria-label="账户菜单">
              <span class="user-avatar">
                {{ (userStore.user?.nickname || userStore.user?.username || '?').charAt(0) }}
              </span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <template v-else>
          <el-button @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-header {
  border-bottom: 1px solid var(--blog-line);
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(12px);
}

.header-inner {
  width: 100%;
  max-width: var(--blog-content-width);
  min-height: 64px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.user-trigger {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-trigger {
  min-width: 40px;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.user-trigger {
  gap: 3px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--blog-moss);
  color: var(--blog-moss-ink);
  font-weight: 700;
}

@media (max-width: 900px) {
  .header-inner {
    gap: 10px;
    padding: 0 14px;
  }
}

@media (max-width: 640px) {
  .header-inner {
    min-height: 58px;
    flex-wrap: wrap;
    padding-block: 8px;
  }

  .publish-button {
    display: none;
  }
}
</style>
