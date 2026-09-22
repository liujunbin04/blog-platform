<script setup lang="ts">
import PaginationBar from '@/components/PaginationBar.vue'
import type { FavoriteVO } from '@/types/api'
import type { PageState } from '@/types/pagination'
import { formatDateTime } from '@/utils/format'

defineProps<{
  list: FavoriteVO[]
  total: number
  page: PageState
  loading: boolean
}>()

defineEmits<{
  'update:page': [value: PageState]
  cancel: [item: FavoriteVO]
  open: [item: FavoriteVO]
}>()
</script>

<template>
  <el-skeleton v-if="loading && list.length === 0" :rows="6" animated />
  <el-empty v-else-if="list.length === 0" description="你还没有收藏文章" />

  <div v-else class="ledger">
    <div class="ledger-head favorites-head">
      <span>文章</span>
      <span>作者</span>
      <span>收藏时间</span>
      <span>操作</span>
    </div>
    <article
      v-for="row in list"
      :key="`${row.articleId}-${row.favoriteTime}`"
      class="ledger-row favorites-row"
    >
      <div class="ledger-title">
        <button type="button" @click="$emit('open', row)">
          {{ row.title }}
        </button>
        <span>发布于 {{ formatDateTime(row.articleCreateTime) }}</span>
      </div>
      <span>{{ row.author?.nickname || '匿名' }}</span>
      <span class="ledger-data">{{ formatDateTime(row.favoriteTime) }}</span>
      <div class="ledger-actions">
        <el-button link type="danger" @click="$emit('cancel', row)">取消收藏</el-button>
      </div>
    </article>
  </div>

  <PaginationBar :page="page" :total="total" @change="$emit('update:page', $event)" />
</template>

<style scoped src="@/styles/ledger.css">
</style>
