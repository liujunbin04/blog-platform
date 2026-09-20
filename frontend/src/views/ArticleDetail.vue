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
import ArticleComments from '@/components/ArticleComments.vue'
import ArticleDetailHeader from '@/components/ArticleDetailHeader.vue'
import ArticleRail from '@/components/ArticleRail.vue'
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
const submitting = ref(false)
const replyTo = ref<CommentVO | null>(null)
const commentsRef = ref<InstanceType<typeof ArticleComments> | null>(null)

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

async function loadComments(page = commentPage.value) {
  const result = await getCommentPage({
    articleId: articleId.value,
    pageNum: page,
    pageSize: commentPageSize,
  })
  commentPage.value = page
  comments.value = result.list
  commentTotal.value = Number(result.total)
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
  commentsRef.value?.focusEditor()
}

async function onSubmitComment(content: string) {
  submitting.value = true
  try {
    await addComment({
      articleId: articleId.value,
      replyToId: replyTo.value?.id,
      content,
    })
    commentsRef.value?.clearEditor()
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

        <ArticleComments
          ref="commentsRef"
          :comments="comments"
          :comment-total="commentTotal"
          :comment-page="commentPage"
          :comment-page-size="commentPageSize"
          :submitting="submitting"
          :reply-to="replyTo"
          @submit="onSubmitComment"
          @change-page="loadComments"
          @reply="onReply"
          @cancel-reply="cancelReply"
          @delete-comment="onDeleteComment"
        />

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
