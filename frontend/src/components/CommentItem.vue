<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import type { CommentVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

defineProps<{
  comment: CommentVO
  depth?: number
}>()

const emit = defineEmits<{
  reply: [comment: CommentVO]
  delete: [comment: CommentVO]
}>()

const userStore = useUserStore()

/** 只能删除自己的评论 */
function isMine(comment: CommentVO): boolean {
  return userStore.isLoggedIn && comment.author?.id === userStore.user?.id
}
</script>

<template>
  <div class="comment-item" :class="{ deep: (depth ?? 0) >= 3 }">
    <div class="head">
      <el-avatar :size="28">{{ comment.author?.nickname?.charAt(0) || '?' }}</el-avatar>
      <span class="nickname">{{ comment.author?.nickname || '佚名' }}</span>
      <!-- 楼中楼回复标注被回复目标，避免看不出在回复谁 -->
      <span v-if="comment.replyToId !== 0" class="reply-to">
        回复@{{ comment.replyToNickname || '佚名' }}
      </span>
      <span class="time">{{ formatDateTime(comment.createTime) }}</span>
      <span class="actions">
        <el-button link type="primary" size="small" @click="emit('reply', comment)">回复</el-button>
        <el-button v-if="isMine(comment)" link type="danger" size="small" @click="emit('delete', comment)">
          删除
        </el-button>
      </span>
    </div>
    <p class="content">{{ comment.content }}</p>
    <div v-if="comment.children.length > 0" class="children">
      <CommentItem
        v-for="child in comment.children"
        :key="child.id"
        :comment="child"
        :depth="(depth ?? 0) + 1"
        @reply="emit('reply', $event)"
        @delete="emit('delete', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--blog-line-soft);
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname {
  font-weight: 700;
}
.reply-to {
  color: var(--blog-red);
  font-size: 12px;
}
.time {
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 10px;
}
.actions {
  margin-left: auto;
  display: inline-flex;
}
.content {
  margin: 9px 0 0 36px;
  line-height: 1.75;
  white-space: pre-wrap;
}
.children {
  margin-left: 36px;
}
.deep .children {
  margin-left: 0;
}

@media (max-width: 767px) {
  .head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .actions {
    flex-basis: 100%;
    margin-left: 36px;
  }
}
</style>
