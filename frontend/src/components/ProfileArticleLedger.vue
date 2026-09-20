<script setup lang="ts">
import type { ArticleVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface PageState {
  pageNum: number
  pageSize: number
}

defineProps<{
  list: ArticleVO[]
  total: number
  page: PageState
  loading: boolean
}>()

const emit = defineEmits<{
  'update:page': [value: PageState]
  'page-change': [page: number]
  'size-change': []
  create: []
  edit: [article: ArticleVO]
  delete: [article: ArticleVO]
  'toggle-hide': [article: ArticleVO]
  open: [article: ArticleVO]
}>()

function onSizeChange(size: number) {
  emit('update:page', { pageNum: 1, pageSize: size })
  emit('size-change')
}
</script>

<template>
  <el-skeleton v-if="loading && list.length === 0" :rows="6" animated />
  <el-empty v-else-if="list.length === 0" description="你还没有文章">
    <el-button type="primary" @click="$emit('create')">写第一篇文章</el-button>
  </el-empty>

  <div v-else class="ledger">
    <div class="ledger-head">
      <span>文章</span>
      <span>状态</span>
      <span>数据</span>
      <span>操作</span>
    </div>
    <article v-for="row in list" :key="row.id" class="ledger-row">
      <div class="ledger-title">
        <button type="button" @click="$emit('open', row)">
          {{ row.title }}
        </button>
        <span>{{ row.category || '未分类' }} · {{ formatDateTime(row.createTime) }}</span>
      </div>
      <el-tag v-if="row.status === 0" size="small" type="warning">草稿</el-tag>
      <el-tag v-else-if="row.hidden === 1" size="small" type="info">已隐藏</el-tag>
      <el-tag v-else size="small" type="success">已发布</el-tag>
      <div class="ledger-data">{{ row.viewCount }} 浏览<br />{{ row.likeCount }} 点赞</div>
      <div class="ledger-actions">
        <el-button link type="primary" @click="$emit('edit', row)">编辑</el-button>
        <el-button
          v-if="row.status === 1 && row.hidden !== 1"
          link
          type="warning"
          @click="$emit('toggle-hide', row)"
        >
          隐藏
        </el-button>
        <el-button
          v-else-if="row.status === 1 && row.hidden === 1"
          link
          type="success"
          @click="$emit('toggle-hide', row)"
        >
          公开
        </el-button>
        <el-button link type="danger" @click="$emit('delete', row)">删除</el-button>
      </div>
    </article>
  </div>

  <el-pagination
    v-if="total > 0"
    :current-page="page.pageNum"
    :page-size="page.pageSize"
    :total="total"
    :page-sizes="[10, 20, 50]"
    layout="total, sizes, prev, pager, next"
    class="pagination"
    @current-change="$emit('page-change', $event)"
    @size-change="onSizeChange"
  />
</template>

<style scoped>
.ledger-head,
.ledger-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 90px 110px 190px;
  gap: 16px;
  align-items: center;
}

.ledger-head {
  padding: 0 4px 10px;
  border-bottom: 1px solid var(--blog-line);
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 10px;
}

.ledger-row {
  padding: 16px 4px;
  border-bottom: 1px solid var(--blog-line-soft);
}

.ledger-title {
  min-width: 0;
}

.ledger-title button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--blog-ink);
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.ledger-title button:hover {
  color: var(--blog-red);
}

.ledger-title span,
.ledger-data {
  display: block;
  margin-top: 6px;
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 10px;
  line-height: 1.6;
}

.ledger-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.pagination {
  justify-content: center;
  margin-top: 22px;
}

@media (max-width: 767px) {
  .ledger-head {
    display: none;
  }

  .ledger-row {
    grid-template-columns: 1fr auto;
    gap: 10px;
  }

  .ledger-title {
    grid-column: 1 / -1;
  }

  .ledger-actions {
    justify-content: flex-start;
  }

  .pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .pagination :deep(.el-pagination__sizes),
  .pagination :deep(.el-pagination__total) {
    display: none;
  }
}
</style>
