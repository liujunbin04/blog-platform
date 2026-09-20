<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import CommentItem from '@/components/CommentItem.vue'
import type { CommentVO } from '@/types/api'

defineProps<{
  comments: CommentVO[]
  commentTotal: number
  commentPage: number
  commentPageSize: number
  submitting: boolean
  replyTo: CommentVO | null
}>()

const emit = defineEmits<{
  submit: [content: string]
  'change-page': [page: number]
  reply: [comment: CommentVO]
  'cancel-reply': []
  'delete-comment': [comment: CommentVO]
}>()

const commentContent = ref('')
const editorRef = ref<{ focus: () => void } | null>(null)

async function focusEditor() {
  await nextTick()
  editorRef.value?.focus()
}

defineExpose({ focusEditor, clearEditor })

function onSubmit() {
  const content = commentContent.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  emit('submit', content)
}

function clearEditor() {
  commentContent.value = ''
}
</script>

<template>
  <section class="comments-section">
    <h2>评论（{{ commentTotal }}）</h2>

    <div v-if="replyTo" class="reply-banner">
      回复 @{{ replyTo.author?.nickname || '匿名' }}
      <el-button link size="small" @click="emit('cancel-reply')">取消</el-button>
    </div>

    <div class="comment-editor">
      <el-input
        ref="editorRef"
        v-model="commentContent"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="写下你的评论"
      />
      <el-button type="primary" :loading="submitting" @click="onSubmit">发表评论</el-button>
    </div>

    <el-empty v-if="comments.length === 0" description="还没有评论，来抢沙发" :image-size="80" />
    <CommentItem
      v-for="comment in comments"
      :key="comment.id"
      :comment="comment"
      @reply="emit('reply', $event)"
      @delete="emit('delete-comment', $event)"
    />
    <el-pagination
      v-if="commentTotal > commentPageSize"
      :current-page="commentPage"
      :page-size="commentPageSize"
      :total="commentTotal"
      layout="prev, pager, next"
      class="comment-pagination"
      @current-change="emit('change-page', $event)"
    />
  </section>
</template>

<style scoped>
.comments-section {
  max-width: 840px;
  margin-top: 28px;
  padding: 28px;
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.comments-section h2 {
  margin-top: 0;
  font-family: var(--blog-font-display);
}

.reply-banner {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: var(--blog-blueprint);
  font-size: 13px;
}

.comment-editor {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 20px;
}

.comment-editor :deep(.el-textarea) {
  width: 100%;
}

.comment-pagination {
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 767px) {
  .comments-section {
    padding: 20px 16px 90px;
  }
}
</style>
