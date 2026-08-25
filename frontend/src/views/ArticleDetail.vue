<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { addComment, getCommentTree } from '@/api/comment'
import { deleteArticle, getArticleDetail } from '@/api/article'
import { favoriteArticle, getFavoriteStatus, unfavoriteArticle } from '@/api/favorite'
import { getLikeStatus, likeArticle, unlikeArticle } from '@/api/like'
import type { ArticleVO, CommentVO } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import CommentItem from '@/components/CommentItem.vue'

// 默认 html:false：正文里的原始 HTML 会被转义，防 XSS
const md = new MarkdownIt({ breaks: true })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const articleId = route.params.id as string

const loading = ref(false)
const article = ref<ArticleVO | null>(null)
const liked = ref(false)
const favorited = ref(false)
const comments = ref<CommentVO[]>([])
const commentContent = ref('')
const submitting = ref(false)
/** 正在回复的评论；null 表示发表一级评论 */
const replyTo = ref<CommentVO | null>(null)

const renderedContent = computed(() => (article.value ? md.render(article.value.content) : ''))

const isAuthor = computed(
  () => userStore.isLoggedIn && article.value?.author?.id === userStore.user?.id,
)

/** 未登录点击互动按钮时引导登录，并记录回跳地址 */
function requireLogin(): boolean {
  if (userStore.isLoggedIn) return true
  ElMessage.warning('请先登录')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function loadArticle() {
  loading.value = true
  try {
    article.value = await getArticleDetail(articleId)
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  comments.value = await getCommentTree(articleId)
}

/** 已登录则进入页面时回显点赞/收藏状态 */
async function loadInteractionStatus() {
  if (!userStore.isLoggedIn) return
  liked.value = await getLikeStatus(articleId)
  favorited.value = await getFavoriteStatus(articleId)
}

async function onToggleLike() {
  if (!requireLogin() || !article.value) return
  if (liked.value) {
    await unlikeArticle(articleId)
    liked.value = false
    article.value.likeCount -= 1
  } else {
    await likeArticle(articleId)
    liked.value = true
    article.value.likeCount += 1
  }
}

async function onToggleFavorite() {
  if (!requireLogin()) return
  if (favorited.value) {
    await unfavoriteArticle(articleId)
    favorited.value = false
    ElMessage.success('已取消收藏')
  } else {
    await favoriteArticle(articleId)
    favorited.value = true
    ElMessage.success('收藏成功')
  }
}

async function onDelete() {
  await ElMessageBox.confirm('删除后不可恢复，确定删除该文章吗？', '删除确认', { type: 'warning' })
  await deleteArticle(articleId)
  ElMessage.success('删除成功')
  await router.push('/articles')
}

function onReply(comment: CommentVO) {
  replyTo.value = comment
}

function cancelReply() {
  replyTo.value = null
}

/** 回复任意层级评论都挂到其一级父评论下（见任务 Interfaces 的语义约定） */
function resolveParentId(target: CommentVO): number {
  return target.parentId === 0 ? target.id : target.parentId
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
      articleId,
      parentId: replyTo.value ? resolveParentId(replyTo.value) : undefined,
      content,
    })
    commentContent.value = ''
    cancelReply()
    await loadComments()
  } catch {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadArticle()
  loadComments()
  loadInteractionStatus()
})
</script>

<template>
  <div v-loading="loading" class="article-detail">
    <template v-if="article">
      <el-card>
        <h1 class="title">{{ article.title }}</h1>
        <div class="meta">
          <span>{{ article.author?.nickname || '佚名' }}</span>
          <el-tag v-if="article.category" size="small" type="info">{{ article.category }}</el-tag>
          <span>{{ formatDateTime(article.createTime) }}</span>
          <span>浏览 {{ article.viewCount }}</span>
          <el-tag v-for="tag in (article.tags || '').split(',').filter((t) => t.trim())" :key="tag" size="small">
            {{ tag.trim() }}
          </el-tag>
          <span v-if="isAuthor" class="owner-actions">
            <el-button link type="primary" @click="router.push(`/publish/${article.id}`)">编辑</el-button>
            <el-button link type="danger" @click="onDelete">删除</el-button>
          </span>
        </div>
        <div class="action-bar">
          <el-button :type="liked ? 'primary' : 'default'" round @click="onToggleLike">
            <el-icon><star /></el-icon>
            点赞 {{ article.likeCount }}
          </el-button>
          <el-button :type="favorited ? 'warning' : 'default'" round @click="onToggleFavorite">
            <el-icon><collection-tag /></el-icon>
            {{ favorited ? '已收藏' : '收藏' }}
          </el-button>
        </div>
        <!-- 正文由 markdown-it 渲染，html:false 保证原始 HTML 被转义 -->
        <div class="markdown-body" v-html="renderedContent" />
      </el-card>

      <el-card class="comments-card">
        <h3 class="comments-title">评论（{{ comments.length }}）</h3>
        <div v-if="replyTo" class="reply-banner">
          回复 @{{ replyTo.author?.nickname || '佚名' }}
          <el-button link size="small" @click="cancelReply">取消</el-button>
        </div>
        <div class="comment-editor">
          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="写下你的评论…"
          />
          <el-button type="primary" :loading="submitting" class="comment-submit" @click="onSubmitComment">
            发表评论
          </el-button>
        </div>
        <el-empty v-if="comments.length === 0" description="还没有评论，来抢沙发" :image-size="80" />
        <CommentItem v-for="c in comments" :key="c.id" :comment="c" @reply="onReply" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.title {
  margin-top: 0;
}
.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.owner-actions {
  margin-left: auto;
}
.action-bar {
  display: flex;
  gap: 12px;
  margin: 16px 0;
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.markdown-body {
  line-height: 1.8;
}
.markdown-body :deep(img) {
  max-width: 100%;
}
.markdown-body :deep(pre) {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
}
.markdown-body :deep(code) {
  background: var(--el-fill-color-light);
  padding: 2px 4px;
  border-radius: 3px;
}
.comments-card {
  margin-top: 16px;
}
.comments-title {
  margin-top: 0;
}
.reply-banner {
  margin-bottom: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 14px;
}
.comment-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
  margin-bottom: 16px;
}
.comment-editor :deep(.el-textarea) {
  width: 100%;
}
</style>
