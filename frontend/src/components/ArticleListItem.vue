<script setup lang="ts">
import { Star, View } from '@element-plus/icons-vue'

import type { ArticleSearchVO, ArticleVO } from '@/types/api'
import { formatDateTime, highlightHtml } from '@/utils/format'

type ListItem = ArticleVO & Partial<Pick<ArticleSearchVO, 'contentSnippet'>>

defineProps<{
  article: ListItem
  featured?: boolean
}>()

defineEmits<{
  open: [id: number]
}>()
</script>

<template>
  <article
    class="article-item"
    :class="{ featured }"
    role="button"
    tabindex="0"
    @click="$emit('open', article.id)"
    @keydown.enter.prevent="$emit('open', article.id)"
    @keydown.space.prevent="$emit('open', article.id)"
  >
    <div class="eyebrow">{{ article.category || '随笔' }}</div>
    <div class="body">
      <h2 class="title" v-html="highlightHtml(article.title)" />
      <p v-if="article.contentSnippet" class="snippet" v-html="highlightHtml(article.contentSnippet)" />
    </div>
    <div class="meta">
      <span>{{ article.author?.nickname || '匿名' }}</span>
      <span>{{ formatDateTime(article.createTime) }}</span>
      <span><el-icon><View /></el-icon>{{ article.viewCount }}</span>
      <span><el-icon><Star /></el-icon>{{ article.likeCount }}</span>
    </div>
  </article>
</template>

<style scoped>
.article-item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 110px;
  gap: 18px;
  padding: 22px 4px;
  border-bottom: 1px solid var(--blog-line);
  cursor: pointer;
  transition:
    padding var(--blog-transition),
    color var(--blog-transition);
}

.article-item:hover {
  padding-left: 10px;
  color: var(--blog-red);
}

.article-item.featured {
  display: block;
  padding: 28px;
  border: 0;
  background: var(--blog-ink);
  color: var(--blog-paper);
  box-shadow: var(--blog-shadow-sm);
}

.eyebrow,
.meta,
.snippet {
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 11px;
}

.featured .eyebrow {
  color: #9fc0b4;
  letter-spacing: 0.12em;
}

.title {
  margin: 0;
  font-family: var(--blog-font-display);
  font-size: 20px;
  line-height: 1.35;
}

.featured .title {
  max-width: 680px;
  margin-top: 10px;
  font-size: 34px;
  line-height: 1.22;
}

.snippet {
  margin: 9px 0 0;
  font-family: var(--blog-font-body);
  line-height: 1.7;
}

.featured .snippet {
  color: #c6d4df;
}

.meta {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.featured .meta {
  justify-content: flex-start;
  margin-top: 18px;
  color: #a9becc;
}

.meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.title :deep(em),
.snippet :deep(em) {
  color: var(--blog-red);
  font-style: normal;
}

.featured .title :deep(em),
.featured .snippet :deep(em) {
  color: #ffb6a6;
}

@media (max-width: 767px) {
  .article-item {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .meta {
    justify-content: flex-start;
  }

  .featured {
    padding: 22px 18px;
  }

  .featured .title {
    font-size: 27px;
  }
}
</style>
