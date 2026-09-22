<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import HeaderNav from '@/components/HeaderNav.vue'
import HeaderNotifications from '@/components/HeaderNotifications.vue'
import HeaderUserMenu from '@/components/HeaderUserMenu.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')

const isHot = computed(
  () => route.path === '/articles' && route.query.sort === 'hot',
)

const displayName = computed(
  () => userStore.user?.nickname || userStore.user?.username || '',
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
          <HeaderUserMenu
            :is-logged-in="userStore.isLoggedIn"
            :nickname="displayName"
            @publish="router.push('/publish')"
            @profile="onUserCommand('profile')"
            @logout="onUserCommand('logout')"
          />
        </template>

        <HeaderUserMenu
          v-else
          :is-logged-in="userStore.isLoggedIn"
          :nickname="displayName"
          @login="router.push('/login')"
          @register="router.push('/register')"
        />
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

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
}
</style>
