<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getArticlePage, searchArticles } from '@/api/article'
import type { ArticleSearchVO, ArticleVO } from '@/types/api'
import { formatDateTime, highlightHtml } from '@/utils/format'

/** 列表项：普通分页是 ArticleVO，搜索结果多一个 contentSnippet 片段 */
type ListItem = ArticleVO & Partial<Pick<ArticleSearchVO, 'contentSnippet'>>

const route = useRoute()

const loading = ref(false)
const list = ref<ListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const sort = ref<'time' | 'hot'>('time')
const keyword = ref((route.query.keyword as string) ?? '')

/** 关键词非空走 ES 搜索接口，否则走普通分页接口 */
async function load() {
  const params = JSON.stringify({
    kw: keyword.value.trim(),
    page: pageNum.value,
    size: pageSize.value,
    sort: sort.value,
  })
  // 参数快照去重：pageSize 变化引发的 current-change + size-change 连发、
  // 以及 watch 对同批变化的重复触发，都收敛为一次网络请求
  if (params === lastParams.value) return
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const page = kw
      ? await searchArticles({ keyword: kw, page: pageNum.value, size: pageSize.value })
      : await getArticlePage({ pageNum: pageNum.value, pageSize: pageSize.value, sort: sort.value })
    lastParams.value = params
    list.value = page.list as ListItem[]
    total.value = Number(page.total)
  } catch {
    // 错误已由响应拦截器提示
  } finally {
    loading.value = false
  }
}

const lastParams = ref('')

/** 条件变化（换排序 / 换每页大小）从第一页重新加载：只改 pageNum，由 watch 统一触发 */
function resetAndLoad() {
  pageNum.value = 1
}

// 用户停留在列表页时再次通过顶部导航搜索：响应 query 变化
watch(
  () => route.query.keyword,
  (kw) => {
    keyword.value = (kw as string) ?? ''
    resetAndLoad()
  },
)

// 任一查询条件变化统一走 load()；参数快照保证同批变化只发一次请求
watch([pageNum, pageSize, () => sort.value], load)

onMounted(load)
</script>

<template>
  <div class="article-list">
    <div class="toolbar">
      <el-radio-group v-model="sort">
        <el-radio-button value="time">最新</el-radio-button>
        <el-radio-button value="hot">最热</el-radio-button>
      </el-radio-group>
      <el-tag v-if="keyword.trim()" closable @close="resetAndLoad">搜索：{{ keyword }}</el-tag>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂无文章" />
    <div v-loading="loading">
      <el-card
        v-for="item in list"
        :key="item.id"
        class="item"
        shadow="hover"
        @click="$router.push(`/articles/${item.id}`)"
      >
        <!-- 标题/摘要可能含 ES 高亮 <em>，highlightHtml 已确保只放行 em 标签 -->
        <h3 class="title" v-html="highlightHtml(item.title)" />
        <p v-if="item.contentSnippet" class="snippet" v-html="highlightHtml(item.contentSnippet)" />
        <div class="meta">
          <span>{{ item.author?.nickname || '佚名' }}</span>
          <el-tag v-if="item.category" size="small" type="info">{{ item.category }}</el-tag>
          <span>{{ formatDateTime(item.createTime) }}</span>
          <span class="meta-num"><el-icon><view /></el-icon>{{ item.viewCount }}</span>
          <span class="meta-num"><el-icon><star /></el-icon>{{ item.likeCount }}</span>
        </div>
      </el-card>
    </div>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.item {
  margin-bottom: 12px;
  cursor: pointer;
}
.title {
  margin: 0 0 8px;
  font-size: 17px;
}
.title :deep(em),
.snippet :deep(em) {
  color: var(--el-color-primary);
  font-style: normal;
}
.snippet {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.meta-num {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.pagination {
  justify-content: center;
  margin-top: 20px;
}
</style>
