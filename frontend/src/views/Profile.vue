<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import { deleteArticle, getMyArticles, hideArticle, unhideArticle } from '@/api/article'
import { getMyFavorites, unfavoriteArticle } from '@/api/favorite'
import AppShell from '@/components/AppShell.vue'
import { useUserStore } from '@/stores/user'
import type { ArticleVO, FavoriteVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('articles')
const myList = ref<ArticleVO[]>([])
const myTotal = ref(0)
const myPage = reactive({ pageNum: 1, pageSize: 10 })
const myLoading = ref(false)

const favList = ref<FavoriteVO[]>([])
const favTotal = ref(0)
const favPage = reactive({ pageNum: 1, pageSize: 10 })
const favLoading = ref(false)
const favLoadedOnce = ref(false)

async function loadMyArticles() {
  myLoading.value = true
  try {
    const page = await getMyArticles(myPage)
    myList.value = page.list
    myTotal.value = Number(page.total)
  } finally {
    myLoading.value = false
  }
}

async function loadFavorites() {
  favLoading.value = true
  try {
    const page = await getMyFavorites(favPage)
    favList.value = page.list
    favTotal.value = Number(page.total)
  } finally {
    favLoading.value = false
  }
}

function onTabChange(name: number | string) {
  if (name === 'favorites' && !favLoadedOnce.value) {
    favLoadedOnce.value = true
    loadFavorites()
  }
}

function onMySizeChange() {
  myPage.pageNum = 1
  loadMyArticles()
}

function onFavoriteSizeChange() {
  favPage.pageNum = 1
  loadFavorites()
}

async function onDeleteArticle(article: ArticleVO) {
  try {
    await ElMessageBox.confirm(
      `删除后《${article.title}》及其评论、点赞、收藏数据将被彻底清除且不可恢复，确定删除吗？`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }

  await deleteArticle(article.id)
  ElMessage.success('删除成功')
  await loadMyArticles()
}

async function onToggleHide(article: ArticleVO) {
  const hiding = article.hidden !== 1
  try {
    await ElMessageBox.confirm(
      hiding
        ? `确定隐藏《${article.title}》吗？隐藏后他人不可见、不可搜索。`
        : `确定重新公开《${article.title}》吗？`,
      hiding ? '隐藏确认' : '公开确认',
      { type: 'warning' },
    )
  } catch {
    return
  }

  if (hiding) {
    await hideArticle(article.id)
    ElMessage.success('已隐藏')
  } else {
    await unhideArticle(article.id)
    ElMessage.success('已公开')
  }
  await loadMyArticles()
}

async function onCancelFavorite(item: FavoriteVO) {
  await unfavoriteArticle(item.articleId)
  ElMessage.success('已取消收藏')
  await loadFavorites()
}

onMounted(loadMyArticles)
</script>

<template>
  <AppShell>
    <div class="profile">
      <header class="profile-header">
        <div>
          <p class="ledger-kicker">CREATOR LEDGER</p>
          <h1>你好，{{ userStore.user?.nickname || userStore.user?.username }}</h1>
        </div>
        <div class="profile-actions">
          <el-button type="primary" @click="router.push('/publish')">写新文章</el-button>
        </div>
      </header>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="我的文章" name="articles">
          <el-skeleton v-if="myLoading && myList.length === 0" :rows="6" animated />
          <el-empty v-else-if="myList.length === 0" description="你还没有文章">
            <el-button type="primary" @click="router.push('/publish')">写第一篇文章</el-button>
          </el-empty>

          <div v-else class="ledger">
            <div class="ledger-head">
              <span>文章</span>
              <span>状态</span>
              <span>数据</span>
              <span>操作</span>
            </div>
            <article v-for="row in myList" :key="row.id" class="ledger-row">
              <div class="ledger-title">
                <button type="button" @click="router.push(`/articles/${row.id}`)">
                  {{ row.title }}
                </button>
                <span>{{ row.category || '未分类' }} · {{ formatDateTime(row.createTime) }}</span>
              </div>
              <el-tag v-if="row.status === 0" size="small" type="warning">草稿</el-tag>
              <el-tag v-else-if="row.hidden === 1" size="small" type="info">已隐藏</el-tag>
              <el-tag v-else size="small" type="success">已发布</el-tag>
              <div class="ledger-data">{{ row.viewCount }} 浏览<br />{{ row.likeCount }} 点赞</div>
              <div class="ledger-actions">
                <el-button link type="primary" @click="router.push(`/publish/${row.id}`)">
                  编辑
                </el-button>
                <el-button
                  v-if="row.status === 1 && row.hidden !== 1"
                  link
                  type="warning"
                  @click="onToggleHide(row)"
                >
                  隐藏
                </el-button>
                <el-button
                  v-else-if="row.status === 1 && row.hidden === 1"
                  link
                  type="success"
                  @click="onToggleHide(row)"
                >
                  公开
                </el-button>
                <el-button link type="danger" @click="onDeleteArticle(row)">删除</el-button>
              </div>
            </article>
          </div>

          <el-pagination
            v-if="myTotal > 0"
            v-model:current-page="myPage.pageNum"
            v-model:page-size="myPage.pageSize"
            :total="myTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            class="pagination"
            @current-change="loadMyArticles"
            @size-change="onMySizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="我的收藏" name="favorites">
          <el-skeleton v-if="favLoading && favList.length === 0" :rows="6" animated />
          <el-empty v-else-if="favList.length === 0" description="你还没有收藏文章" />

          <div v-else class="ledger">
            <div class="ledger-head favorites-head">
              <span>文章</span>
              <span>作者</span>
              <span>收藏时间</span>
              <span>操作</span>
            </div>
            <article
              v-for="row in favList"
              :key="`${row.articleId}-${row.favoriteTime}`"
              class="ledger-row favorites-row"
            >
              <div class="ledger-title">
                <button type="button" @click="router.push(`/articles/${row.articleId}`)">
                  {{ row.title }}
                </button>
                <span>发布于 {{ formatDateTime(row.articleCreateTime) }}</span>
              </div>
              <span>{{ row.author?.nickname || '匿名' }}</span>
              <span class="ledger-data">{{ formatDateTime(row.favoriteTime) }}</span>
              <div class="ledger-actions">
                <el-button link type="danger" @click="onCancelFavorite(row)">取消收藏</el-button>
              </div>
            </article>
          </div>

          <el-pagination
            v-if="favTotal > 0"
            v-model:current-page="favPage.pageNum"
            v-model:page-size="favPage.pageSize"
            :total="favTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            class="pagination"
            @current-change="loadFavorites"
            @size-change="onFavoriteSizeChange"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </AppShell>
</template>

<style scoped>
.profile {
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.profile-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 30px 32px 24px;
  border-bottom: 1px solid var(--blog-line);
  background: var(--blog-blueprint);
}

.ledger-kicker {
  margin: 0 0 8px;
  color: var(--blog-red);
  font-family: var(--blog-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.profile-header h1 {
  margin: 0;
  font-family: var(--blog-font-display);
  font-size: 34px;
}

:deep(.el-tabs__header) {
  margin: 0;
  padding: 0 32px;
}

:deep(.el-tabs__content) {
  padding: 26px 32px 32px;
}

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
  .profile-header {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px 18px;
  }

  .profile-header h1 {
    font-size: 27px;
  }

  :deep(.el-tabs__header) {
    padding: 0 18px;
  }

  :deep(.el-tabs__content) {
    padding: 18px;
  }

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
