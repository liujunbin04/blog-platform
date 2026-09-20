<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import MarkdownIt from 'markdown-it'

import { deleteArticle, getArticleDetail } from '@/api/article'
import { addComment, deleteComment, getCommentPage } from '@/api/comment'
import { favoriteArticle, getFavoriteStatus, unfavoriteArticle } from '@/api/favorite'
import { getLikeStatus, likeArticle, unlikeArticle } from '@/api/like'
import AppShell from '@/components/AppShell.vue'
import ArticleDetailHeader from '@/components/ArticleDetailHeader.vue'
import ArticleRail from '@/components/ArticleRail.vue'
import CommentItem from '@/components/CommentItem.vue'
import { useUserStore } from '@/stores/user'
import type { ArticleVO, CommentVO } from '@/types/api'

const md = new MarkdownIt({ breaks: true })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const articleId = computed(() => String(route.params.id ?? ''))
let articleRequestSeq = 0

const loading = ref(false)
const article = ref<ArticleVO | null>(null)
const liked = ref(false)
const favorited = ref(false)
const comments = ref<CommentVO[]>([])
const commentPage = ref(1)
const commentPageSize = 20
const commentTotal = ref(0)
const commentContent = ref('')
const submitting = ref(false)
const replyTo = ref<CommentVO | null>(null)

const renderedContent = computed(() => (article.value ? md.render(article.value.content) : ''))
const isAuthor = computed(
  () => userStore.isLoggedIn && article.value?.author?.id === userStore.user?.id,
)
const readingMinutes = computed(() => {
  const characters = article.value?.content.replace(/\s/g, '').length ?? 0
  return Math.max(1, Math.ceil(characters / 400))
})
const articleTags = computed(() =>
  (article.value?.tags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean),
)

function requireLogin(): boolean {
  if (userStore.isLoggedIn) return true
  ElMessage.warning('请先登录')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function loadArticle() {
  const requestSeq = ++articleRequestSeq
  loading.value = true
  try {
    const value = await getArticleDetail(articleId.value)
    if (requestSeq !== articleRequestSeq) return
    article.value = value
  } finally {
    if (requestSeq === articleRequestSeq) {
      loading.value = false
    }
  }
}

async function loadComments() {
  const page = await getCommentPage({
    articleId: articleId.value,
    pageNum: commentPage.value,
    pageSize: commentPageSize,
  })
  comments.value = page.list
  commentTotal.value = Number(page.total)
}

async function loadInteractionStatus() {
  if (!userStore.isLoggedIn) return
  liked.value = await getLikeStatus(articleId.value)
  favorited.value = await getFavoriteStatus(articleId.value)
}

async function onToggleLike() {
  if (!requireLogin() || !article.value) return
  try {
    if (liked.value) {
      await unlikeArticle(articleId.value)
      liked.value = false
      article.value.likeCount -= 1
    } else {
      await likeArticle(articleId.value)
      liked.value = true
      article.value.likeCount += 1
    }
  } catch {
    // 失败时保持原状态，错误由请求拦截器提示。
  }
}

async function onToggleFavorite() {
  if (!requireLogin()) return
  try {
    if (favorited.value) {
      await unfavoriteArticle(articleId.value)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteArticle(articleId.value)
      favorited.value = true
      ElMessage.success('收藏成功')
    }
  } catch {
    // 失败时保持原状态。
  }
}

async function onDelete() {
  try {
    await ElMessageBox.confirm(
      '删除后文章及其评论、点赞、收藏数据将被彻底清除且不可恢复，确定删除吗？',
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }

  await deleteArticle(articleId.value)
  ElMessage.success('删除成功')
  await router.push('/articles')
}

function onReply(comment: CommentVO) {
  replyTo.value = comment
}

function cancelReply() {
  replyTo.value = null
}

function focusCommentEditor() {
  document.querySelector<HTMLTextAreaElement>('.comment-editor textarea')?.focus()
}

async function onSubmitComment() {
  const content = commentContent.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }

  submitting.value = true
  try {
    await addComment({
      articleId: articleId.value,
      replyToId: replyTo.value?.id,
      content,
    })
    commentContent.value = ''
    cancelReply()
    await loadComments()
  } catch {
    // 错误由请求拦截器提示。
  } finally {
    submitting.value = false
  }
}

async function onDeleteComment(comment: CommentVO) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除确认', { type: 'warning' })
  } catch {
    return
  }

  await deleteComment(comment.id)
  ElMessage.success('删除成功')
  await loadComments()
}

onMounted(() => {
  loadArticle()
  loadComments()
  loadInteractionStatus()
})

function resetArticleState() {
  article.value = null
  liked.value = false
  favorited.value = false
  comments.value = []
  commentTotal.value = 0
  commentPage.value = 1
  replyTo.value = null
}

watch(articleId, () => {
  resetArticleState()
  window.scrollTo({ top: 0 })
  loadArticle()
  loadComments()
  loadInteractionStatus()
})
</script>

<template>
  <AppShell>
    <div v-loading="loading" class="article-detail">
      <template v-if="article">
        <div class="detail-layout">
          <article class="article-main">
            <ArticleDetailHeader
              :article="article"
              :is-author="isAuthor"
              :reading-minutes="readingMinutes"
              @edit="router.push(`/publish/${article.id}`)"
              @delete="onDelete"
            />

            <div class="markdown-body" v-html="renderedContent" />
          </article>

          <ArticleRail
            :article="article"
            :liked="liked"
            :favorited="favorited"
            :article-tags="articleTags"
            @toggle-like="onToggleLike"
            @toggle-favorite="onToggleFavorite"
          />
        </div>

        <section class="comments-section">
          <h2>评论（{{ commentTotal }}）</h2>

          <div v-if="replyTo" class="reply-banner">
            回复 @{{ replyTo.author?.nickname || '匿名' }}
            <el-button link size="small" @click="cancelReply">取消</el-button>
          </div>

          <div class="comment-editor">
            <el-input
              v-model="commentContent"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              placeholder="写下你的评论"
            />
            <el-button type="primary" :loading="submitting" @click="onSubmitComment">
              发表评论
            </el-button>
          </div>

          <el-empty v-if="comments.length === 0" description="还没有评论，来抢沙发" :image-size="80" />
          <CommentItem
            v-for="comment in comments"
            :key="comment.id"
            :comment="comment"
            @reply="onReply"
            @delete="onDeleteComment"
          />
          <el-pagination
            v-if="commentTotal > commentPageSize"
            v-model:current-page="commentPage"
            :page-size="commentPageSize"
            :total="commentTotal"
            layout="prev, pager, next"
            class="comment-pagination"
            @current-change="loadComments"
          />
        </section>

        <div class="mobile-actions">
          <button type="button" @click="onToggleLike">点赞 {{ article.likeCount }}</button>
          <button type="button" @click="onToggleFavorite">
            {{ favorited ? '已收藏' : '收藏' }}
          </button>
          <button type="button" @click="focusCommentEditor">评论</button>
        </div>
      </template>
    </div>
  </AppShell>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 0.9fr);
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.article-main {
  min-width: 0;
  padding: 46px 56px 56px;
}

.markdown-body {
  padding-top: 26px;
  font-size: 17px;
  line-height: 1.9;
}

.comments-section {
  max-width: 840px;
  margin-top: 28px;
  padding: 28px;
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
}

.comments-section h2 {
  margin-top: 0;
  font-family: var(--blog-font-display);
}

.reply-banner {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: var(--blog-blueprint);
  font-size: 13px;
}

.comment-editor {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 20px;
}

.comment-editor :deep(.el-textarea) {
  width: 100%;
}

.comment-pagination {
  justify-content: center;
  margin-top: 20px;
}

.mobile-actions {
  display: none;
}

@media (max-width: 1199px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .article-main {
    padding: 38px 42px 48px;
  }
}

@media (max-width: 767px) {
  .article-main {
    padding: 26px 18px 36px;
  }

  .comments-section {
    padding: 20px 16px 90px;
  }

  .mobile-actions {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    min-height: 58px;
    background: var(--blog-ink);
  }

  .mobile-actions button {
    min-height: 58px;
    border: 0;
    background: transparent;
    color: var(--blog-paper);
    cursor: pointer;
  }
}
</style>
