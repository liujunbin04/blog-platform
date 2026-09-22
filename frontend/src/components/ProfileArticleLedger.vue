<script setup lang="ts">
import PaginationBar from '@/components/PaginationBar.vue'
import type { ArticleVO } from '@/types/api'
import type { PageState } from '@/types/pagination'
import { formatDateTime } from '@/utils/format'

defineProps<{
  list: ArticleVO[]
  total: number
  page: PageState
  loading: boolean
}>()

defineEmits<{
  'update:page': [value: PageState]
  create: []
  edit: [article: ArticleVO]
  delete: [article: ArticleVO]
  'toggle-hide': [article: ArticleVO]
  open: [article: ArticleVO]
}>()
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

  <PaginationBar :page="page" :total="total" @change="$emit('update:page', $event)" />
</template>

<style scoped src="@/styles/ledger.css">
</style>
