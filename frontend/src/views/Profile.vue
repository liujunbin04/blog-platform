<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteArticle, getMyArticles } from '@/api/article'
import { getMyFavorites, unfavoriteArticle } from '@/api/favorite'
import type { ArticleVO, FavoriteVO } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

// ---- 我的作品 Tab 状态 ----
const myList = ref<ArticleVO[]>([])
const myTotal = ref(0)
const myPage = reactive({ pageNum: 1, pageSize: 10 })
const myLoading = ref(false)

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

async function onDeleteArticle(article: ArticleVO) {
  try {
    await ElMessageBox.confirm(`确定删除《${article.title}》吗？`, '删除确认', { type: 'warning' })
  } catch {
    return // 用户取消确认：静默返回
  }
  await deleteArticle(article.id)
  ElMessage.success('删除成功')
  await loadMyArticles()
}

// ---- 我的收藏 Tab 状态 ----
const favList = ref<FavoriteVO[]>([])
const favTotal = ref(0)
const favPage = reactive({ pageNum: 1, pageSize: 10 })
const favLoading = ref(false)

const activeTab = ref('articles')
const favLoadedOnce = ref(false)

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

async function onCancelFavorite(item: FavoriteVO) {
  await unfavoriteArticle(item.articleId)
  ElMessage.success('已取消收藏')
  await loadFavorites()
}

function onTabChange(name: number | string) {
  if (name === 'favorites' && !favLoadedOnce.value) {
    favLoadedOnce.value = true
    loadFavorites()
  }
}

onMounted(() => {
  loadMyArticles()
})
</script>

<template>
  <div class="profile">
    <h2 class="welcome">你好，{{ userStore.user?.nickname || userStore.user?.username }}</h2>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="我的文章" name="articles">
        <el-table v-loading="myLoading" :data="myList">
          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <el-link type="primary" @click="router.push(`/articles/${row.id}`)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="110" />
          <el-table-column prop="viewCount" label="浏览量" width="90" />
          <el-table-column prop="likeCount" label="点赞数" width="90" />
          <el-table-column label="发布时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/publish/${row.id}`)">编辑</el-button>
              <el-button link type="danger" @click="onDeleteArticle(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="myPage.pageNum"
          v-model:page-size="myPage.pageSize"
          :total="myTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @current-change="loadMyArticles"
          @size-change="myPage.pageNum = 1; loadMyArticles()"
        />
      </el-tab-pane>

      <el-tab-pane label="我的收藏" name="favorites">
        <el-table v-loading="favLoading" :data="favList">
          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <el-link type="primary" @click="router.push(`/articles/${row.articleId}`)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="作者" width="130">
            <template #default="{ row }">{{ row.author?.nickname || '佚名' }}</template>
          </el-table-column>
          <el-table-column label="文章发布时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.articleCreateTime) }}</template>
          </el-table-column>
          <el-table-column label="收藏时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.favoriteTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="danger" @click="onCancelFavorite(row)">取消收藏</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="favPage.pageNum"
          v-model:page-size="favPage.pageSize"
          :total="favTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @current-change="loadFavorites"
          @size-change="favPage.pageNum = 1; loadFavorites()"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.welcome {
  margin-top: 0;
}
.pagination {
  justify-content: center;
  margin-top: 16px;
}
</style>
