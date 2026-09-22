<script setup lang="ts">
import { PAGE_SIZES, type PageState } from '@/types/pagination'

const props = defineProps<{
  page: PageState
  total: number
}>()

/**
 * 只暴露一个 change 事件，携带「下一页状态」的完整值。
 * <p>父组件因此只需要做一件事：持久化新状态并重新拉取。换页与换每页条数
 * 走同一条路径，避免出现「换了 size 但忘了重置页码」这类分叉。</p>
 */
const emit = defineEmits<{
  change: [value: PageState]
}>()

function onPageChange(pageNum: number) {
  emit('change', { pageNum, pageSize: props.page.pageSize })
}

function onSizeChange(pageSize: number) {
  // 每页条数变化后，原页码大概率越界，统一回到第一页
  emit('change', { pageNum: 1, pageSize })
}
</script>

<template>
  <el-pagination
    v-if="total > 0"
    :current-page="page.pageNum"
    :page-size="page.pageSize"
    :total="total"
    :page-sizes="PAGE_SIZES"
    layout="total, sizes, prev, pager, next"
    class="pagination"
    @current-change="onPageChange"
    @size-change="onSizeChange"
  />
</template>

<style scoped>
.pagination {
  justify-content: center;
  margin-top: 22px;
}

@media (max-width: 767px) {
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
