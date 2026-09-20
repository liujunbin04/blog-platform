<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { ArrowDown, Bell } from '@element-plus/icons-vue'

import { clearNotifications, getNotifications, markAllNotifications } from '@/api/notification'
import HeaderNav from '@/components/HeaderNav.vue'
import { useUserStore } from '@/stores/user'
import type { NotificationVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')
const notifyList = ref<NotificationVO[]>([])
const unreadCount = ref(0)

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

async function loadNotifications() {
  try {
    const page = await getNotifications({ pageNum: 1, pageSize: 5 })
    notifyList.value = page.list
    unreadCount.value = Number(page.unreadCount)
  } catch {
    // 全局拦截器已提示错误，保留当前列表状态。
  }
}

function onNotifyVisible(visible: boolean) {
  if (visible) loadNotifications()
}

async function onMarkAllRead() {
  try {
    await markAllNotifications()
    await loadNotifications()
  } catch {
    // 全局拦截器已提示错误。
  }
}

async function onClearNotifications() {
  try {
    await ElMessageBox.confirm(
      '清空后全部通知将被删除且不可恢复，确定清空吗？',
      '清空确认',
      { type: 'warning' },
    )
  } catch {
    return
  }

  await clearNotifications()
  ElMessage.success('通知已清空')
  await loadNotifications()
}

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      loadNotifications()
    } else {
      notifyList.value = []
      unreadCount.value = 0
    }
  },
  { immediate: true },
)
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
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
            <el-dropdown placement="bottom-end" popper-class="notify-popper" @visible-change="onNotifyVisible">
              <button class="icon-button" type="button" aria-label="查看通知">
                <el-icon :size="19"><Bell /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <div class="notify-head">
                    <span>通知</span>
                    <span class="notify-head-actions">
                      <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="onMarkAllRead">
                        全部已读
                      </el-button>
                      <el-button
                        v-if="notifyList.length > 0"
                        link
                        type="danger"
                        size="small"
                        @click="onClearNotifications"
                      >
                        清空
                      </el-button>
                    </span>
                  </div>
                  <el-empty v-if="notifyList.length === 0" description="暂无通知" :image-size="60" />
                  <el-dropdown-item
                    v-for="n in notifyList"
                    :key="n.id"
                    class="notify-item"
                    @click="$router.push(`/articles/${n.articleId}`)"
                  >
                    <span class="notify-dot" :class="{ unread: n.isRead === 0 }" />
                    {{ n.senderNickname || '匿名' }}
                    {{ n.type === 'LIKE' ? '赞了你的文章' : (n.isRead === 0 ? '评论/回复了你' : '评论了你') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-badge>

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

.icon-button,
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

.icon-button,
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

.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 12px;
  border-bottom: 1px solid var(--blog-line-soft);
}

.notify-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.notify-item {
  white-space: normal;
  line-height: 1.5;
}

.notify-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: transparent;
}

.notify-dot.unread {
  background: var(--blog-red);
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

<style>
.notify-popper {
  width: 320px;
}
</style>
