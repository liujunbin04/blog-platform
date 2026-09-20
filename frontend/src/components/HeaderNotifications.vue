<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Bell } from '@element-plus/icons-vue'

import { clearNotifications, getNotifications, markAllNotifications } from '@/api/notification'
import { useUserStore } from '@/stores/user'
import type { NotificationVO } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

const notifyList = ref<NotificationVO[]>([])
const unreadCount = ref(0)

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
            @click="router.push(`/articles/${n.articleId}`)"
          >
            <span class="notify-dot" :class="{ unread: n.isRead === 0 }" />
            {{ n.senderNickname || '匿名' }}
            {{ n.type === 'LIKE' ? '赞了你的文章' : (n.isRead === 0 ? '评论/回复了你' : '评论了你') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </el-badge>
</template>

<style scoped>
.icon-button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.icon-button {
  min-width: 40px;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
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
</style>
<style>
/* 下拉面板被 teleport 到 body，需用非 scoped 样式定义宽度。 */
.notify-popper {
  width: 320px;
}
</style>
