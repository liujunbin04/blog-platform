<script setup lang="ts">
import type { ArticleVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

defineProps<{
  article: ArticleVO
  isAuthor: boolean
  readingMinutes: number
}>()

defineEmits<{
  edit: []
  delete: []
}>()
</script>

<template>
  <header class="article-header">
    <div class="article-kicker">
      <span>{{ article.category || '随笔' }}</span>
      <span>{{ readingMinutes }} MIN READ</span>
    </div>
    <h1 class="title">{{ article.title }}</h1>
    <div class="meta">
      <span>{{ article.author?.nickname || '匿名' }}</span>
      <span>{{ formatDateTime(article.createTime) }}</span>
      <span>浏览 {{ article.viewCount }}</span>
      <el-tag v-if="article.status === 0" size="small" type="warning">草稿</el-tag>
      <el-tag v-else-if="article.hidden === 1" size="small" type="warning">已隐藏</el-tag>
      <span v-if="isAuthor" class="owner-actions">
        <el-button link type="primary" @click="$emit('edit')">编辑</el-button>
        <el-button link type="danger" @click="$emit('delete')">删除</el-button>
      </span>
    </div>
  </header>
</template>

<style scoped>
.article-kicker,
.meta {
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 11px;
}

.article-kicker {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: var(--blog-red);
}

.title {
  margin: 14px 0 16px;
  font-family: var(--blog-font-display);
  font-size: clamp(34px, 4vw, 54px);
  line-height: 1.15;
}

.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--blog-line);
}

.owner-actions {
  margin-left: auto;
}

@media (max-width: 767px) {
  .title {
    font-size: 34px;
  }
}
</style>
