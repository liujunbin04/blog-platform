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
import ProfileArticleLedger from '@/components/ProfileArticleLedger.vue'
import ProfileFavoriteLedger from '@/components/ProfileFavoriteLedger.vue'
import { useUserStore } from '@/stores/user'
import type { ArticleVO, FavoriteVO } from '@/types/api'

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
          <ProfileArticleLedger
            :list="myList"
            :total="myTotal"
            :page="myPage"
            :loading="myLoading"
            @update:page="
              (value) => {
                myPage.pageNum = value.pageNum
                myPage.pageSize = value.pageSize
              }
            "
            @page-change="
              (page) => {
                myPage.pageNum = page
                loadMyArticles()
              }
            "
            @size-change="onMySizeChange"
            @create="router.push('/publish')"
            @edit="(row) => router.push(`/publish/${row.id}`)"
            @delete="onDeleteArticle"
            @toggle-hide="onToggleHide"
            @open="(row) => router.push(`/articles/${row.id}`)"
          />
        </el-tab-pane>

        <el-tab-pane label="我的收藏" name="favorites">
          <ProfileFavoriteLedger
            :list="favList"
            :total="favTotal"
            :page="favPage"
            :loading="favLoading"
            @update:page="
              (value) => {
                favPage.pageNum = value.pageNum
                favPage.pageSize = value.pageSize
              }
            "
            @page-change="
              (page) => {
                favPage.pageNum = page
                loadFavorites()
              }
            "
            @size-change="onFavoriteSizeChange"
            @cancel="onCancelFavorite"
            @open="(row) => router.push(`/articles/${row.articleId}`)"
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
}
</style>
