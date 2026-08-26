<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, ArrowDown, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getNotifications, markAllNotifications } from '@/api/notification'
import type { NotificationVO } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')

/** 回车或点击按钮：带关键词跳转文章列表页 */
function onSearch() {
  const kw = keyword.value.trim()
  router.push(kw ? { path: '/articles', query: { keyword: kw } } : { path: '/articles' })
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

const notifyList = ref<NotificationVO[]>([])
const unreadCount = ref(0)

async function loadNotifications() {
  const page = await getNotifications({ pageNum: 1, pageSize: 5 })
  notifyList.value = page.list
  unreadCount.value = Number(page.unreadCount)
}

async function onMarkAllRead() {
  await markAllNotifications()
  await loadNotifications()
}

onMounted(() => {
  if (userStore.isLoggedIn) loadNotifications()
})
</script>

<template>
  <div class="app-header">
    <span class="logo" @click="router.push('/articles')">我的博客</span>
    <el-input
      v-model="keyword"
      class="search"
      placeholder="搜索文章"
      clearable
      @keyup.enter="onSearch"
    >
      <template #append>
        <el-button @click="onSearch">
          <el-icon><Search /></el-icon>
        </el-button>
      </template>
    </el-input>
    <div class="actions">
      <template v-if="userStore.isLoggedIn">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
          <el-dropdown placement="bottom-end" width="320" @visible-change="(v: boolean) => v && loadNotifications()">
            <span class="notify-trigger">
              <el-icon :size="20"><Bell /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="notify-head">
                  <span>通知</span>
                  <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="onMarkAllRead">
                    全部已读
                  </el-button>
                </div>
                <el-empty v-if="notifyList.length === 0" description="暂无通知" :image-size="60" />
                <el-dropdown-item
                  v-for="n in notifyList"
                  :key="n.id"
                  class="notify-item"
                  @click="$router.push(`/articles/${n.articleId}`)"
                >
                  <span class="notify-dot" :class="{ unread: n.isRead === 0 }" />
                  {{ n.senderNickname || '佚名' }}
                  {{ n.type === 'LIKE' ? '赞了你的文章' : (n.isRead === 0 ? '评论/回复了你' : '评论了你') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-badge>
        <el-button type="primary" @click="router.push('/publish')">发布文章</el-button>
        <el-dropdown @command="onUserCommand">
          <span class="nickname">
            {{ userStore.user?.nickname || userStore.user?.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
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
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
  padding: 0 24px;
}
.logo {
  font-size: 20px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.search {
  max-width: 360px;
}
.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}
.notify-trigger {
  display: inline-flex;
  cursor: pointer;
  outline: none;
}
.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.notify-item {
  white-space: normal;
  line-height: 1.5;
}
.notify-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: transparent;
  margin-right: 6px;
}
.notify-dot.unread {
  background: var(--el-color-danger);
}
</style>
