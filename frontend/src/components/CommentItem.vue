<script setup lang="ts">
import type { CommentVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

defineProps<{
  comment: CommentVO
  depth?: number
}>()

const emit = defineEmits<{
  reply: [comment: CommentVO]
}>()
</script>

<template>
  <div class="comment-item" :class="{ deep: (depth ?? 0) >= 3 }">
    <div class="head">
      <el-avatar :size="28">{{ comment.author?.nickname?.charAt(0) || '?' }}</el-avatar>
      <span class="nickname">{{ comment.author?.nickname || '佚名' }}</span>
      <span class="time">{{ formatDateTime(comment.createTime) }}</span>
      <el-button link type="primary" size="small" @click="emit('reply', comment)">回复</el-button>
    </div>
    <p class="content">{{ comment.content }}</p>
    <div v-if="comment.children.length > 0" class="children">
      <CommentItem
        v-for="child in comment.children"
        :key="child.id"
        :comment="child"
        :depth="(depth ?? 0) + 1"
        @reply="emit('reply', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname {
  font-weight: 500;
}
.time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.content {
  margin: 8px 0 0 36px;
  white-space: pre-wrap;
}
.children {
  margin-left: 36px;
}
.deep .children {
  margin-left: 0;
}
</style>
