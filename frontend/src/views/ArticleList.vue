<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getArticlePage, getHotArticles, searchArticles } from '@/api/article'
import ArticleListItem from '@/components/ArticleListItem.vue'
import AppShell from '@/components/AppShell.vue'
import ContextRail from '@/components/ContextRail.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import type { ArticleSearchVO, ArticleVO } from '@/types/api'
import type { PageState } from '@/types/pagination'

type ListItem = ArticleVO & Partial<Pick<ArticleSearchVO, 'contentSnippet'>>

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const list = ref<ListItem[]>([])
const hotList = ref<ArticleVO[]>([])
const total = ref(0)
const pagination = reactive<PageState>({ pageNum: 1, pageSize: 10 })
const sort = ref<'time' | 'hot'>(route.query.sort === 'hot' ? 'hot' : 'time')
const keyword = ref((route.query.keyword as string) ?? '')
const lastParams = ref('')

const featuredArticle = computed(() => list.value[0] ?? null)
const remainingArticles = computed(() => list.value.slice(1))

async function load() {
  const params = JSON.stringify({
    kw: keyword.value.trim(),
    page: pagination.pageNum,
    size: pagination.pageSize,
    sort: sort.value,
  })
  if (params === lastParams.value) return

  loading.value = true
  try {
    const value = keyword.value.trim()
    const page = value
      ? await searchArticles({
          keyword: value,
          page: pagination.pageNum,
          size: pagination.pageSize,
        })
      : await getArticlePage({
          pageNum: pagination.pageNum,
          pageSize: pagination.pageSize,
          sort: sort.value,
        })
    lastParams.value = params
    list.value = page.list as ListItem[]
    total.value = Number(page.total)
  } catch {
    // 全局请求拦截器负责展示错误。
  } finally {
    loading.value = false
  }
}

async function loadHotArticles() {
  try {
    hotList.value = await getHotArticles(5)
  } catch {
    // 热门轨道失败不影响文章列表主流程。
  }
}

function resetAndLoad() {
  pagination.pageNum = 1
  load()
}

/** 分页组件只上报「下一页状态」，这里写回后由下方 watch 统一触发加载 */
function onPageChange(next: PageState) {
  pagination.pageNum = next.pageNum
  pagination.pageSize = next.pageSize
}

function onClearKeyword() {
  keyword.value = ''
  if (route.query.keyword !== undefined) {
    router.replace({ path: '/articles', query: { ...route.query, keyword: undefined } })
  }
  resetAndLoad()
}

watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = (value as string) ?? ''
    resetAndLoad()
  },
)

watch(
  () => route.query.sort,
  (value) => {
    sort.value = value === 'hot' ? 'hot' : 'time'
  },
)

watch(sort, (value) => {
  const query = { ...route.query }
  if (value === 'hot') query.sort = 'hot'
  else delete query.sort
  router.replace({ path: '/articles', query })
})

watch([() => pagination.pageNum, () => pagination.pageSize, () => sort.value], load)

onMounted(() => {
  load()
  loadHotArticles()
})
</script>

<template>
  <AppShell>
    <div class="article-list">
      <div class="list-layout">
        <section class="main-column" aria-label="文章列表">
          <div class="toolbar">
            <el-radio-group v-model="sort" :disabled="!!keyword.trim()">
              <el-radio-button value="time">最新</el-radio-button>
              <el-radio-button value="hot">热门</el-radio-button>
            </el-radio-group>
            <el-tag v-if="keyword.trim()" closable @close="onClearKeyword">
              搜索：{{ keyword }}
            </el-tag>
          </div>

          <el-skeleton v-if="loading && list.length === 0" :rows="8" animated />

          <el-empty
            v-else-if="!loading && list.length === 0"
            :description="keyword.trim() ? '没有找到相关文章' : '暂时还没有文章'"
          >
            <el-button v-if="keyword.trim()" type="primary" @click="onClearKeyword">
              清除搜索
            </el-button>
            <el-button v-else type="primary" @click="router.push('/publish')">
              写第一篇文章
            </el-button>
          </el-empty>

          <template v-else>
            <ArticleListItem
              v-if="featuredArticle"
              :article="featuredArticle"
              featured
              @open="router.push(`/articles/${$event}`)"
            />
            <ArticleListItem
              v-for="item in remainingArticles"
              :key="item.id"
              :article="item"
              @open="router.push(`/articles/${$event}`)"
            />
          </template>

          <PaginationBar :page="pagination" :total="total" @change="onPageChange" />
        </section>

        <ContextRail>
          <section class="rail-section">
            <h3>热门文章</h3>
            <button
              v-for="(article, index) in hotList"
              :key="article.id"
              type="button"
              class="hot-item"
              @click="router.push(`/articles/${article.id}`)"
            >
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <strong>{{ article.title }}</strong>
            </button>
            <p v-if="hotList.length === 0" class="rail-empty">暂无热门数据</p>
          </section>

          <section class="rail-section">
            <h3>主题索引</h3>
            <div class="topic-list">
              <span v-for="topic in ['技术', '思考', '写作', '生活']" :key="topic">
                {{ topic }}
              </span>
            </div>
          </section>
        </ContextRail>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.list-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 0.9fr);
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.main-column {
  min-width: 0;
  padding: 24px 28px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.rail-section + .rail-section {
  margin-top: 28px;
}

.rail-section h3 {
  margin: 0 0 12px;
  font-size: 13px;
}

.hot-item {
  width: 100%;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 8px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid #cbdad3;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.hot-item span {
  color: var(--blog-red);
  font-family: var(--blog-font-mono);
}

.hot-item strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.55;
}

.hot-item:hover strong {
  color: var(--blog-red);
}

.rail-empty {
  color: var(--blog-muted);
  font-size: 11px;
}

.topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.topic-list span {
  padding: 6px 9px;
  background: #d4e5de;
  font-size: 11px;
}

@media (max-width: 1199px) {
  .list-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .main-column {
    padding: 18px 14px;
  }

  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
