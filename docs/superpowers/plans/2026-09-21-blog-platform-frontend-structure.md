# 博客平台前端结构收敛 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `ArticleDetail.vue`、`ArticleEdit.vue`、`Profile.vue` 和 `AppHeader.vue` 四个胖组件按业务职责拆成子组件，让每个文件只讲一件事，同时不改变视觉样式、用户流程和接口调用。

**Architecture:** 父组件继续持有状态和全部 API 调用，子组件以纯展示为主，通过 props 收数据、emits 回事件；唯一例外是 `HeaderNotifications` 自己管理通知加载状态。模板和 scoped 样式原样搬移，不重新设计视觉。本轮不抽 composable，不引入新依赖，不新增测试文件。

**Tech Stack:** Vue 3、TypeScript、Vite、Element Plus、Vitest、ESLint、Oxlint

---

## 前置条件

在隔离 worktree 中执行本计划。分支名建议 `codex/frontend-component-split`。

所有前端命令在 `frontend/` 目录执行：

```powershell
cd frontend
```

本计划不新增测试文件，验证依赖类型检查、两个 Linter、构建和既有测试：

```powershell
npm run type-check
npx eslint .
npx oxlint .
npm run build
npm test
```

## 文件结构

**创建（11 个）：**

- `frontend/src/components/ArticleDetailHeader.vue`：文章详情头部（类别、标题、元信息、作者操作）。
- `frontend/src/components/ArticleRail.vue`：文章详情右侧栏（作者、标签、互动、未来占位）。
- `frontend/src/components/ArticleComments.vue`：评论区（编辑器、一级评论列表、分页）。
- `frontend/src/components/EditorDocumentBar.vue`：编辑器顶部文档栏。
- `frontend/src/components/EditorMetadataFields.vue`：标题、分类、标签表单项。
- `frontend/src/components/EditorPanels.vue`：预览、移动端大纲和信息面板。
- `frontend/src/components/ProfileArticleLedger.vue`：我的文章表格和分页。
- `frontend/src/components/ProfileFavoriteLedger.vue`：我的收藏表格和分页。
- `frontend/src/components/HeaderNav.vue`：站点名、导航和搜索框。
- `frontend/src/components/HeaderNotifications.vue`：通知下拉，自带状态。
- `frontend/src/components/HeaderUserMenu.vue`：写文章按钮、用户下拉、登录注册按钮。

**修改（4 个）：**

- `frontend/src/views/ArticleDetail.vue`
- `frontend/src/views/ArticleEdit.vue`
- `frontend/src/views/Profile.vue`
- `frontend/src/components/AppHeader.vue`

**不改动：** `EditorToolbar.vue`、`EditorOutline.vue`、`CommentItem.vue`、`AppShell.vue`、`ContextRail.vue`，以及 `api/`、`types/`、`stores/`、`router/`、`utils/`。

---

### Task 1: 拆分 ArticleDetail 头部为 ArticleDetailHeader

**Files:**
- Create: `frontend/src/components/ArticleDetailHeader.vue`
- Modify: `frontend/src/views/ArticleDetail.vue`（template 第 219-246 行头部区块）

- [ ] **Step 1: 创建 ArticleDetailHeader 组件**

创建 `frontend/src/components/ArticleDetailHeader.vue`，内容为：

```vue
<script setup lang="ts">
import type { ArticleVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

defineProps<{
  article: ArticleVO
  isAuthor: boolean
  readingMinutes: number
}>()

defineEmits<{
  edit: []
  delete: []
}>()
</script>

<template>
  <header class="article-header">
    <div class="article-kicker">
      <span>{{ article.category || '随笔' }}</span>
      <span>{{ readingMinutes }} MIN READ</span>
    </div>
    <h1 class="title">{{ article.title }}</h1>
    <div class="meta">
      <span>{{ article.author?.nickname || '匿名' }}</span>
      <span>{{ formatDateTime(article.createTime) }}</span>
      <span>浏览 {{ article.viewCount }}</span>
      <el-tag v-if="article.status === 0" size="small" type="warning">草稿</el-tag>
      <el-tag v-else-if="article.hidden === 1" size="small" type="warning">已隐藏</el-tag>
      <span v-if="isAuthor" class="owner-actions">
        <el-button link type="primary" @click="$emit('edit')">编辑</el-button>
        <el-button link type="danger" @click="$emit('delete')">删除</el-button>
      </span>
    </div>
  </header>
</template>

<style scoped>
.article-kicker,
.meta {
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 11px;
}

.article-kicker {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: var(--blog-red);
}

.title {
  margin: 14px 0 16px;
  font-family: var(--blog-font-display);
  font-size: clamp(34px, 4vw, 54px);
  line-height: 1.15;
}

.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--blog-line);
}

.owner-actions {
  margin-left: auto;
}
</style>
```

- [ ] **Step 2: 在 ArticleDetail 中引用组件**

在 `frontend/src/views/ArticleDetail.vue` 的 import 区（第 15 行 `AppShell` 之后）加入：

```typescript
import ArticleDetailHeader from '@/components/ArticleDetailHeader.vue'
```

把 template 第 219-246 行整段 `<header class="article-header">...</header>` 替换为：

```vue
<ArticleDetailHeader
  :article="article"
  :is-author="isAuthor"
  :reading-minutes="readingMinutes"
  @edit="router.push(`/publish/${article.id}`)"
  @delete="onDelete"
/>
```

- [ ] **Step 3: 从 ArticleDetail 的 scoped 样式删除已搬走的规则**

删除 `.article-kicker`、`.article-kicker, .meta`、`.title`、`.meta`、`.owner-actions` 相关规则，保留 `.article-main`、`.markdown-body` 和其余规则。

- [ ] **Step 4: 运行静态检查**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS，无未使用 import 或未定义变量。

- [ ] **Step 5: 提交**

```powershell
git add -- frontend/src/components/ArticleDetailHeader.vue frontend/src/views/ArticleDetail.vue
git commit -m "refactor(frontend): 抽出文章详情头部"
```

---

### Task 2: 拆分 ArticleDetail 右栏为 ArticleRail

**Files:**
- Create: `frontend/src/components/ArticleRail.vue`
- Modify: `frontend/src/views/ArticleDetail.vue`（template 第 249-282 行右栏区块）

- [ ] **Step 1: 创建 ArticleRail 组件**

创建 `frontend/src/components/ArticleRail.vue`，内容为：

```vue
<script setup lang="ts">
import { CollectionTag, Star } from '@element-plus/icons-vue'

import ContextRail from '@/components/ContextRail.vue'
import type { ArticleVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

defineProps<{
  article: ArticleVO
  liked: boolean
  favorited: boolean
  articleTags: string[]
}>()

defineEmits<{
  'toggle-like': []
  'toggle-favorite': []
}>()
</script>

<template>
  <ContextRail label="IN THIS ARTICLE">
    <section class="rail-block">
      <h3>作者</h3>
      <strong>{{ article.author?.nickname || '匿名' }}</strong>
      <p>{{ formatDateTime(article.createTime) }}</p>
    </section>

    <section class="rail-block">
      <h3>标签</h3>
      <div v-if="articleTags.length > 0" class="tag-list">
        <span v-for="tag in articleTags" :key="tag">{{ tag }}</span>
      </div>
      <p v-else>暂无标签</p>
    </section>

    <section class="rail-block">
      <h3>互动</h3>
      <el-button :type="liked ? 'primary' : 'default'" class="rail-action" @click="$emit('toggle-like')">
        <el-icon><Star /></el-icon>
        点赞 {{ article.likeCount }}
      </el-button>
      <el-button
        :type="favorited ? 'warning' : 'default'"
        class="rail-action"
        @click="$emit('toggle-favorite')"
      >
        <el-icon><CollectionTag /></el-icon>
        {{ favorited ? '已收藏' : '收藏' }}
      </el-button>
    </section>

    <section class="rail-block future-slot" aria-hidden="true">
      <h3>扩展位</h3>
      <p>为未来的阅读辅助能力保留结构位置，当前不展示任何功能入口。</p>
    </section>
  </ContextRail>
</template>

<style scoped>
.rail-block + .rail-block {
  margin-top: 26px;
}

.rail-block h3 {
  margin: 0 0 10px;
  font-size: 12px;
}

.rail-block p {
  margin: 8px 0 0;
  color: #5e7c70;
  font-size: 11px;
  line-height: 1.65;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.tag-list span {
  padding: 5px 8px;
  background: #d4e5de;
  font-size: 11px;
}

.rail-action {
  width: 100%;
  margin: 0 0 8px;
}

.future-slot {
  padding-top: 18px;
  border-top: 1px dashed #9eb8ac;
}
</style>
```

- [ ] **Step 2: 在 ArticleDetail 中替换右栏**

加入 import：

```typescript
import ArticleRail from '@/components/ArticleRail.vue'
```

把 template 第 249-282 行整段 `<ContextRail label="IN THIS ARTICLE">...</ContextRail>` 替换为：

```vue
<ArticleRail
  :article="article"
  :liked="liked"
  :favorited="favorited"
  :article-tags="articleTags"
  @toggle-like="onToggleLike"
  @toggle-favorite="onToggleFavorite"
/>
```

删除 `ArticleDetail.vue` 中不再使用的 `ContextRail` 和 `CollectionTag`、`Star` imports。

- [ ] **Step 3: 从 ArticleDetail 的 scoped 样式删除已搬走的规则**

删除 `.rail-block`、`.rail-block + .rail-block`、`.rail-block h3`、`.rail-block p`、`.tag-list`、`.tag-list span`、`.rail-action`、`.future-slot` 规则。

- [ ] **Step 4: 运行静态检查**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add -- frontend/src/components/ArticleRail.vue frontend/src/views/ArticleDetail.vue
git commit -m "refactor(frontend): 抽出文章详情右侧栏"
```

---

### Task 3: 拆分 ArticleDetail 评论区为 ArticleComments

**Files:**
- Create: `frontend/src/components/ArticleComments.vue`
- Modify: `frontend/src/views/ArticleDetail.vue`（template 第 284-323 行评论区）

- [ ] **Step 1: 创建 ArticleComments 组件**

创建 `frontend/src/components/ArticleComments.vue`。组件内部持有 `commentContent`，提交时通过 `submit` 事件带出内容：

```vue
<script setup lang="ts">
import { ref } from 'vue'

import CommentItem from '@/components/CommentItem.vue'
import type { CommentVO } from '@/types/api'

const props = defineProps<{
  comments: CommentVO[]
  commentTotal: number
  commentPage: number
  commentPageSize: number
  submitting: boolean
  replyTo: CommentVO | null
}>()

const emit = defineEmits<{
  submit: [content: string]
  'change-page': [page: number]
  reply: [comment: CommentVO]
  'cancel-reply': []
  'delete-comment': [comment: CommentVO]
}>()

const commentContent = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

defineExpose({
  focusEditor() {
    textareaRef.value?.focus()
  },
})

function onSubmit() {
  const content = commentContent.value.trim()
  if (!content) return
  emit('submit', content)
  commentContent.value = ''
}
</script>

<template>
  <section class="comments-section">
    <h2>评论（{{ props.commentTotal }}）</h2>

    <div v-if="replyTo" class="reply-banner">
      回复 @{{ replyTo.author?.nickname || '匿名' }}
      <el-button link size="small" @click="emit('cancel-reply')">取消</el-button>
    </div>

    <div class="comment-editor">
      <el-input
        ref="textareaRef"
        v-model="commentContent"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="写下你的评论"
      />
      <el-button type="primary" :loading="submitting" @click="onSubmit">发表评论</el-button>
    </div>

    <el-empty v-if="comments.length === 0" description="还没有评论，来抢沙发" :image-size="80" />
    <CommentItem
      v-for="comment in comments"
      :key="comment.id"
      :comment="comment"
      @reply="emit('reply', $event)"
      @delete="emit('delete-comment', $event)"
    />
    <el-pagination
      v-if="commentTotal > commentPageSize"
      :current-page="commentPage"
      :page-size="commentPageSize"
      :total="commentTotal"
      layout="prev, pager, next"
      class="comment-pagination"
      @current-change="emit('change-page', $event)"
    />
  </section>
</template>

<style scoped>
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

@media (max-width: 767px) {
  .comments-section {
    padding: 20px 16px 90px;
  }
}
</style>
```

注意：`el-input` 的 `ref` 指向组件实例而非原生 textarea，`focusEditor` 改为通过 `textareaRef.value?.focus()` 调用。若 Element Plus 的 `el-input` 实例不含 `focus`，改回 `document.querySelector<HTMLTextAreaElement>('.comment-editor textarea')?.focus()`。

- [ ] **Step 2: 改写 ArticleDetail 的评论逻辑**

在 `frontend/src/views/ArticleDetail.vue` 中：

1. 删除 `const commentContent = ref('')`（输入内容改由子组件持有）。
2. 保留 `submitting` 和 `replyTo`，它们仍由父组件管理。
3. 删除原 `focusCommentEditor` 中直接查询 DOM 的实现，改为通过模板 ref 调用子组件暴露的方法：

```typescript
const commentsRef = ref<InstanceType<typeof ArticleComments> | null>(null)

function focusCommentEditor() {
  commentsRef.value?.focusEditor()
}
```

`onSubmitComment` 改为接收内容参数：

```typescript
async function onSubmitComment(content: string) {
  submitting.value = true
  try {
    await addComment({
      articleId: articleId.value,
      replyToId: replyTo.value?.id,
      content,
    })
    cancelReply()
    await loadComments()
  } catch {
    // 错误由请求拦截器提示。
  } finally {
    submitting.value = false
  }
}
```

`resetArticleState` 中删除 `commentContent.value = ''` 一行（该状态已不存在），其余重置项保持不变。

`loadComments` 需支持指定页码，供 `change-page` 使用：

```typescript
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
```

- [ ] **Step 3: 在 ArticleDetail 模板中替换评论区**

加入 import：

```typescript
import ArticleComments from '@/components/ArticleComments.vue'
```

把 template 第 284-323 行整段 `<section class="comments-section">...</section>` 替换为：

```vue
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
```

保留移动端底部 `mobile-actions`，其中评论按钮仍调用 `focusCommentEditor`。

- [ ] **Step 4: 从 ArticleDetail 的 scoped 样式删除评论区规则**

删除 `.comments-section`、`.comments-section h2`、`.reply-banner`、`.comment-editor`、`.comment-editor :deep(.el-textarea)`、`.comment-pagination`，以及移动端媒体查询里的 `.comments-section` 规则。保留 `.mobile-actions` 相关规则。

- [ ] **Step 5: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/ArticleComments.vue frontend/src/views/ArticleDetail.vue
git commit -m "refactor(frontend): 抽出文章评论区"
```

---

### Task 4: 拆分 ArticleEdit 顶部文档栏为 EditorDocumentBar

**Files:**
- Create: `frontend/src/components/EditorDocumentBar.vue`
- Modify: `frontend/src/views/ArticleEdit.vue`（template 第 155-168 行文档栏）

- [ ] **Step 1: 创建 EditorDocumentBar 组件**

创建 `frontend/src/components/EditorDocumentBar.vue`：

```vue
<script setup lang="ts">
defineProps<{
  isEditing: boolean
  title: string
  characterCount: number
  saving: boolean
  showDraftButton: boolean
  primaryLabel: string
}>()

defineEmits<{
  back: []
  'save-draft': []
  'save-publish': []
}>()
</script>

<template>
  <header class="document-bar">
    <el-button link @click="$emit('back')">返回</el-button>
    <span class="document-state">{{ isEditing ? 'EDITING' : 'DRAFT' }}</span>
    <strong class="document-title">{{ title || '未命名文章' }}</strong>
    <span class="document-count">{{ characterCount }} 字</span>
    <el-button v-if="showDraftButton" :disabled="saving" @click="$emit('save-draft')">
      存草稿
    </el-button>
    <el-button type="primary" :loading="saving" @click="$emit('save-publish')">
      {{ primaryLabel }}
    </el-button>
  </header>
</template>

<style scoped>
.document-bar {
  min-height: 54px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 16px;
  background: var(--blog-ink);
  color: var(--blog-paper);
}

.document-state,
.document-count {
  color: #a9becc;
  font-family: var(--blog-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.document-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-count {
  margin-left: auto;
}

@media (max-width: 767px) {
  .document-bar {
    flex-wrap: wrap;
    padding-block: 8px;
  }

  .document-title {
    flex: 1;
  }

  .document-count {
    display: none;
  }
}
</style>
```

- [ ] **Step 2: 在 ArticleEdit 中替换文档栏**

加入 import：

```typescript
import EditorDocumentBar from '@/components/EditorDocumentBar.vue'
```

把 template 第 155-168 行整段 `<header class="document-bar">...</header>` 替换为：

```vue
<EditorDocumentBar
  :is-editing="Boolean(articleId)"
  :title="form.title"
  :character-count="editorStats.characters"
  :saving="saving"
  :show-draft-button="showDraftButton"
  :primary-label="articleId && loadedStatus === 1 ? '保存修改' : '发布文章'"
  @back="router.back()"
  @save-draft="onSubmit(0)"
  @save-publish="onSubmit(1)"
/>
```

- [ ] **Step 3: 从 ArticleEdit 的 scoped 样式删除文档栏规则**

删除 `.document-bar`、`.document-state, .document-count, .panel-label` 中的文档栏部分（`.panel-label` 仍被预览和信息面板使用，需保留 `.panel-label` 独立规则）、`.document-title`、`.document-count`，以及移动端媒体查询里的 `.document-bar`、`.document-title`、`.document-count` 规则。

确保留下 `.panel-label` 规则：

```css
.panel-label {
  color: #a9becc;
  font-family: var(--blog-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}
```

- [ ] **Step 4: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/EditorDocumentBar.vue frontend/src/views/ArticleEdit.vue
git commit -m "refactor(frontend): 抽出编辑器文档栏"
```

---

### Task 5: 拆分 ArticleEdit 元信息表单为 EditorMetadataFields

**Files:**
- Create: `frontend/src/components/EditorMetadataFields.vue`
- Modify: `frontend/src/views/ArticleEdit.vue`（template 第 189-215 行元信息字段）

- [ ] **Step 1: 创建 EditorMetadataFields 组件**

创建 `frontend/src/components/EditorMetadataFields.vue`。表单项通过 `prop` 名称挂载到父级 `el-form` 的校验上下文：

```vue
<script setup lang="ts">
interface MetadataFields {
  title: string
  category: string
  tags: string
}

const props = defineProps<{
  modelValue: MetadataFields
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MetadataFields]
}>()

function update<K extends keyof MetadataFields>(key: K, value: MetadataFields[K]) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <div class="metadata-fields">
    <el-form-item prop="title" class="title-field">
      <el-input
        :model-value="modelValue.title"
        size="large"
        maxlength="200"
        placeholder="文章标题"
        aria-label="文章标题"
        @update:model-value="update('title', $event)"
      />
    </el-form-item>
    <el-form-item prop="category">
      <el-input
        :model-value="modelValue.category"
        placeholder="分类"
        aria-label="文章分类"
        @update:model-value="update('category', $event)"
      />
    </el-form-item>
    <el-form-item prop="tags">
      <el-input
        :model-value="modelValue.tags"
        placeholder="标签，使用英文逗号分隔"
        aria-label="文章标签"
        @update:model-value="update('tags', $event)"
      />
    </el-form-item>
  </div>
</template>

<style scoped>
.metadata-fields {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(120px, 0.7fr) minmax(160px, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.metadata-fields :deep(.el-form-item) {
  margin-bottom: 0;
}

.metadata-fields :deep(.el-form-item__error) {
  position: static;
}

.title-field :deep(input) {
  font-family: var(--blog-font-display);
  font-size: 20px;
  font-weight: 700;
}

@media (max-width: 767px) {
  .metadata-fields {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 2: 在 ArticleEdit 中替换元信息字段**

加入 import：

```typescript
import EditorMetadataFields from '@/components/EditorMetadataFields.vue'
```

删除 `form.title`、`form.category`、`form.tags` 在 `onSubmit` 中的直接使用改为通过 `form` 组合；把 template 第 189-215 行整段 `<div class="metadata-fields">...</div>` 替换为：

```vue
<EditorMetadataFields
  :model-value="{ title: form.title, category: form.category, tags: form.tags }"
  @update:model-value="
    (value) => {
      form.title = value.title
      form.category = value.category
      form.tags = value.tags
    }
  "
/>
```

`form` 仍是 `reactive<ArticleFormData>`，所以 `onSubmit` 和 `loadForEdit` 不需要改动。

- [ ] **Step 3: 从 ArticleEdit 的 scoped 样式删除元信息字段规则**

删除 `.metadata-fields`、`.metadata-fields :deep(.el-form-item), .content-field` 中属于元信息的部分（`.content-field` 保留）、`.metadata-fields :deep(.el-form-item__error)`、`.title-field :deep(input)`，以及移动端媒体查询里的 `.metadata-fields` 规则。

保留 `.content-field` 规则：

```css
.content-field {
  margin-bottom: 0;
}
```

- [ ] **Step 4: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/EditorMetadataFields.vue frontend/src/views/ArticleEdit.vue
git commit -m "refactor(frontend): 抽出编辑器元信息字段"
```

---

### Task 6: 拆分 ArticleEdit 面板为 EditorPanels

**Files:**
- Create: `frontend/src/components/EditorPanels.vue`
- Modify: `frontend/src/views/ArticleEdit.vue`（template 第 174-228 行面板区块）

- [ ] **Step 1: 创建 EditorPanels 组件**

创建 `frontend/src/components/EditorPanels.vue`，负责预览面板、移动端大纲面板、移动端信息面板和移动端切换导航：

```vue
<script setup lang="ts">
import EditorOutline from '@/components/EditorOutline.vue'
import type { ArticleFormData } from '@/api/article'
import type { OutlineItem } from '@/utils/editor'

type MobilePanel = 'edit' | 'preview' | 'outline' | 'info'

defineProps<{
  previewHtml: string
  outline: OutlineItem[]
  mobilePanel: MobilePanel
  form: ArticleFormData
}>()

defineEmits<{
  'update:mobilePanel': [value: MobilePanel]
  navigate: [line: number]
}>()
</script>

<template>
  <nav class="tablet-editor-nav" aria-label="编辑与预览切换">
    <button
      type="button"
      :class="{ active: mobilePanel === 'edit' }"
      @click="$emit('update:mobilePanel', 'edit')"
    >
      编辑
    </button>
    <button
      type="button"
      :class="{ active: mobilePanel === 'preview' }"
      @click="$emit('update:mobilePanel', 'preview')"
    >
      预览
    </button>
  </nav>

  <section class="preview-panel">
    <div class="panel-label">PREVIEW</div>
    <div class="editor-preview markdown-body" v-html="previewHtml" />
  </section>

  <section class="mobile-outline-panel">
    <EditorOutline :items="outline" @navigate="$emit('navigate', $event)" />
  </section>

  <section class="mobile-info-panel">
    <div class="panel-label">DOCUMENT</div>
    <p>分类：{{ form.category || '未分类' }}</p>
    <p>标签：{{ form.tags || '暂无标签' }}</p>
    <p>状态：{{ form.status === 0 ? '草稿' : '已发布' }}</p>
  </section>
</template>

<style scoped>
.panel-label {
  color: #a9becc;
  font-family: var(--blog-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.preview-panel {
  min-width: 0;
  padding: 20px 24px;
  background: #f9fbfc;
}

.editor-preview {
  min-height: 520px;
}

.mobile-outline-panel,
.mobile-info-panel,
.tablet-editor-nav {
  display: none;
}

@media (max-width: 1199px) {
  .tablet-editor-nav {
    display: flex;
    gap: 6px;
    padding: 8px 14px;
    border-bottom: 1px solid var(--blog-line);
    background: var(--blog-blueprint);
  }

  .tablet-editor-nav button {
    min-width: 72px;
    min-height: 32px;
    border: 1px solid var(--blog-line);
    background: var(--blog-paper);
    color: var(--blog-ink-soft);
  }

  .tablet-editor-nav button.active {
    border-color: var(--blog-ink);
    background: var(--blog-ink);
    color: var(--blog-paper);
  }

  .preview-panel {
    display: none;
  }

  .mobile-panel-preview .preview-panel {
    display: block;
  }
}

@media (max-width: 767px) {
  .preview-panel,
  .mobile-outline-panel,
  .mobile-info-panel {
    display: none;
    padding: 16px;
  }

  .mobile-panel-preview .preview-panel,
  .mobile-panel-outline .mobile-outline-panel,
  .mobile-panel-info .mobile-info-panel {
    display: block;
  }

  .mobile-outline-panel :deep(.editor-outline) {
    padding: 0;
    border-right: 0;
    background: transparent;
  }

  .tablet-editor-nav {
    display: none;
  }
}
</style>
```

需要父组件把 `mobile-panel-*` class 放在同时包含子组件根元素的容器上。`EditorPanels` 内部的 `.mobile-panel-preview .preview-panel` 选择器依赖父级 class，因此父组件模板中 `editor-grid` 的 `:class` 必须保留。

`EditorPanels` 根元素是多个节点，Vue 3 支持多根组件；父组件用 `class` 绑定到多根组件时样式会应用到每个根节点，这里 `mobile-panel-*` class 放在 `editor-grid` 容器上即可。

注意：多根组件会破坏 `inheritAttrs` 的自动透传，`v-model:mobilePanel` 和 `@navigate` 通过显式 props 和 emits 传入，不受影响。

- [ ] **Step 2: 在 ArticleEdit 中替换面板**

加入 import：

```typescript
import EditorPanels from '@/components/EditorPanels.vue'
```

删除 `EditorOutline` 在父组件中的使用（保留 `editorStats`、`outline`、`previewHtml` 计算属性）。

把 template 第 174-228 行的平板导航、预览面板、移动端大纲面板、移动端信息面板整段替换为：

```vue
<EditorPanels
  v-model:mobile-panel="mobilePanel"
  :preview-html="previewHtml"
  :outline="outline"
  :form="form"
  @navigate="navigateToLine"
/>
```

保留 `editor-grid` 容器和它的 `:class="`mobile-panel-${mobilePanel}`"`，以及在 `document-panel` 之后的 `desktop-outline`：

```vue
<div class="editor-grid" :class="`mobile-panel-${mobilePanel}`">
  <EditorOutline class="desktop-outline" :items="outline" @navigate="navigateToLine" />

  <section class="document-panel">
    ...（保持不变）
  </section>

  <EditorPanels
    v-model:mobile-panel="mobilePanel"
    :preview-html="previewHtml"
    :outline="outline"
    :form="form"
    @navigate="navigateToLine"
  />
</div>
```

保留 `EditorOutline` 在父组件中的 desktop 用法和 import。

- [ ] **Step 3: 从 ArticleEdit 的 scoped 样式删除已搬走的面板规则**

删除 `.preview-panel`、`.editor-preview`、`.mobile-outline-panel`、`.mobile-info-panel`、`.tablet-editor-nav`，以及两个媒体查询中对应的规则。保留 `.document-panel`、`.editor-grid`、`.desktop-outline`、`.mobile-editor-nav`、`.metadata-fields` 之外的规则。

注意 `.document-panel, .preview-panel` 共享规则需拆开，只保留 `.document-panel`：

```css
.document-panel {
  min-width: 0;
  padding: 20px 24px;
  border-right: 1px solid var(--blog-line);
}
```

- [ ] **Step 4: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/EditorPanels.vue frontend/src/views/ArticleEdit.vue
git commit -m "refactor(frontend): 抽出编辑器预览与移动端面板"
```

---

### Task 7: 拆分 Profile 我的文章为 ProfileArticleLedger

**Files:**
- Create: `frontend/src/components/ProfileArticleLedger.vue`
- Modify: `frontend/src/views/Profile.vue`（template 第 133-197 行文章标签页）

- [ ] **Step 1: 创建 ProfileArticleLedger 组件**

创建 `frontend/src/components/ProfileArticleLedger.vue`：

```vue
<script setup lang="ts">
import type { ArticleVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface PageState {
  pageNum: number
  pageSize: number
}

defineProps<{
  list: ArticleVO[]
  total: number
  page: PageState
  loading: boolean
}>()

defineEmits<{
  'update:page': [value: PageState]
  'page-change': [page: number]
  'size-change': []
  edit: [article: ArticleVO]
  delete: [article: ArticleVO]
  'toggle-hide': [article: ArticleVO]
  open: [article: ArticleVO]
}>()
</script>

<template>
  <el-skeleton v-if="loading && list.length === 0" :rows="6" animated />
  <el-empty v-else-if="list.length === 0" description="你还没有文章">
    <el-button type="primary" @click="$emit('create')">写第一篇文章</el-button>
  </el-empty>

  <div v-else class="ledger">
    <div class="ledger-head">
      <span>文章</span>
      <span>状态</span>
      <span>数据</span>
      <span>操作</span>
    </div>
    <article v-for="row in list" :key="row.id" class="ledger-row">
      <div class="ledger-title">
        <button type="button" @click="$emit('open', row)">{{ row.title }}</button>
        <span>{{ row.category || '未分类' }} · {{ formatDateTime(row.createTime) }}</span>
      </div>
      <el-tag v-if="row.status === 0" size="small" type="warning">草稿</el-tag>
      <el-tag v-else-if="row.hidden === 1" size="small" type="info">已隐藏</el-tag>
      <el-tag v-else size="small" type="success">已发布</el-tag>
      <div class="ledger-data">{{ row.viewCount }} 浏览<br />{{ row.likeCount }} 点赞</div>
      <div class="ledger-actions">
        <el-button link type="primary" @click="$emit('edit', row)">编辑</el-button>
        <el-button
          v-if="row.status === 1 && row.hidden !== 1"
          link
          type="warning"
          @click="$emit('toggle-hide', row)"
        >
          隐藏
        </el-button>
        <el-button
          v-else-if="row.status === 1 && row.hidden === 1"
          link
          type="success"
          @click="$emit('toggle-hide', row)"
        >
          公开
        </el-button>
        <el-button link type="danger" @click="$emit('delete', row)">删除</el-button>
      </div>
    </article>
  </div>

  <el-pagination
    v-if="total > 0"
    :current-page="page.pageNum"
    :page-size="page.pageSize"
    :total="total"
    :page-sizes="[10, 20, 50]"
    layout="total, sizes, prev, pager, next"
    class="pagination"
    @current-change="$emit('page-change', $event)"
    @size-change="
      (size) => {
        $emit('update:page', { pageNum: 1, pageSize: size })
        $emit('size-change')
      }
    "
  />
</template>

<style scoped>
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
```

空状态的“写第一篇文章”按钮当前意图是跳转发布页。为避免子组件直接用路由，且避免给 `open` 传入空文章，改用显式事件 `create`。组件最终的 emits 定义为：

```typescript
defineEmits<{
  'update:page': [value: PageState]
  'page-change': [page: number]
  'size-change': []
  create: []
  edit: [article: ArticleVO]
  delete: [article: ArticleVO]
  'toggle-hide': [article: ArticleVO]
  open: [article: ArticleVO]
}>()
```

- [ ] **Step 2: 在 Profile 中替换文章标签页内容**

加入 import：

```typescript
import ProfileArticleLedger from '@/components/ProfileArticleLedger.vue'
```

把 template 第 133-197 行文章标签页内部内容替换为：

```vue
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
  @page-change="loadMyArticles"
  @size-change="onMySizeChange"
  @create="router.push('/publish')"
  @edit="(row) => router.push(`/publish/${row.id}`)"
  @delete="onDeleteArticle"
  @toggle-hide="onToggleHide"
  @open="(row) => router.push(`/articles/${row.id}`)"
/>
```

`onMySizeChange` 现有实现会重置 `myPage.pageNum = 1`，与子组件 `update:page` 的 `pageNum: 1` 重复但幂等，保持不变。

- [ ] **Step 3: 从 Profile 的 scoped 样式删除文章表格规则**

`.ledger-head`、`.ledger-row`、`.ledger-title`、`.ledger-data`、`.ledger-actions`、`.pagination` 仍被收藏组件复用，因此先保留到收藏组件创建后再统一删除（Task 8 处理）。

- [ ] **Step 4: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/ProfileArticleLedger.vue frontend/src/views/Profile.vue
git commit -m "refactor(frontend): 抽出个人中心文章列表"
```

---

### Task 8: 拆分 Profile 我的收藏为 ProfileFavoriteLedger

**Files:**
- Create: `frontend/src/components/ProfileFavoriteLedger.vue`
- Modify: `frontend/src/views/Profile.vue`（template 第 199-236 行收藏标签页）

- [ ] **Step 1: 创建 ProfileFavoriteLedger 组件**

创建 `frontend/src/components/ProfileFavoriteLedger.vue`：

```vue
<script setup lang="ts">
import type { FavoriteVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface PageState {
  pageNum: number
  pageSize: number
}

defineProps<{
  list: FavoriteVO[]
  total: number
  page: PageState
  loading: boolean
}>()

defineEmits<{
  'update:page': [value: PageState]
  'page-change': [page: number]
  'size-change': []
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

  <el-pagination
    v-if="total > 0"
    :current-page="page.pageNum"
    :page-size="page.pageSize"
    :total="total"
    :page-sizes="[10, 20, 50]"
    layout="total, sizes, prev, pager, next"
    class="pagination"
    @current-change="$emit('page-change', $event)"
    @size-change="
      (size) => {
        $emit('update:page', { pageNum: 1, pageSize: size })
        $emit('size-change')
      }
    "
  />
</template>

<style scoped>
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
```

- [ ] **Step 2: 在 Profile 中替换收藏标签页内容**

加入 import：

```typescript
import ProfileFavoriteLedger from '@/components/ProfileFavoriteLedger.vue'
```

把 template 第 199-236 行收藏标签页内部内容替换为：

```vue
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
  @page-change="loadFavorites"
  @size-change="onFavoriteSizeChange"
  @cancel="onCancelFavorite"
  @open="(row) => router.push(`/articles/${row.articleId}`)"
/>
```

- [ ] **Step 3: 从 Profile 的 scoped 样式删除表格与分页规则**

现在两个 ledger 组件都创建完毕，从 `Profile.vue` 删除 `.ledger-head`、`.ledger-head, .ledger-row`、`.ledger-row`、`.ledger-title`、`.ledger-title button`、`.ledger-title button:hover`、`.ledger-title span, .ledger-data`、`.ledger-actions`、`.pagination`，以及移动端媒体查询里的 `.ledger-head`、`.ledger-row`、`.ledger-title`、`.ledger-actions`、`.pagination` 规则。

保留 `.profile`、`.profile-header`、`.ledger-kicker`、`.profile-header h1`、`:deep(.el-tabs__header)`、`:deep(.el-tabs__content)`，以及移动端对应的 header 和 tabs 规则。

- [ ] **Step 4: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/ProfileFavoriteLedger.vue frontend/src/views/Profile.vue
git commit -m "refactor(frontend): 抽出个人中心收藏列表"
```

---

### Task 9: 拆分 AppHeader 导航为 HeaderNav

**Files:**
- Create: `frontend/src/components/HeaderNav.vue`
- Modify: `frontend/src/components/AppHeader.vue`（template 第 102-124 行 brand、导航、搜索）

- [ ] **Step 1: 创建 HeaderNav 组件**

创建 `frontend/src/components/HeaderNav.vue`：

```vue
<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

defineProps<{
  keyword: string
  isHot: boolean
}>()

defineEmits<{
  'update:keyword': [value: string]
  search: []
  'go-articles': []
  'go-hot': []
}>()
</script>

<template>
  <button class="brand" type="button" @click="$emit('go-articles')">我的博客</button>

  <nav class="primary-nav" aria-label="文章导航">
    <button
      class="nav-link"
      :class="{ 'is-active': !isHot }"
      type="button"
      @click="$emit('go-articles')"
    >
      发现
    </button>
    <button class="nav-link" :class="{ 'is-active': isHot }" type="button" @click="$emit('go-hot')">
      热榜
    </button>
  </nav>

  <div class="search-wrap">
    <el-input
      :model-value="keyword"
      class="search"
      placeholder="搜索文章、作者或标签"
      clearable
      @update:model-value="$emit('update:keyword', $event)"
      @keyup.enter="$emit('search')"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>
  </div>
</template>

<style scoped>
.brand,
.nav-link {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.brand {
  font-family: var(--blog-font-display);
  font-size: 19px;
  font-weight: 700;
  white-space: nowrap;
}

.primary-nav {
  display: inline-flex;
  align-self: stretch;
}

.nav-link {
  padding: 0 5px;
  color: var(--blog-muted);
  font-size: 13px;
}

.nav-link.is-active {
  color: var(--blog-ink);
  box-shadow: inset 0 -2px var(--blog-red);
}

.search-wrap {
  flex: 1;
  display: flex;
  justify-content: flex-end;
}

.search {
  width: 100%;
  max-width: 360px;
}

@media (max-width: 900px) {
  .primary-nav {
    display: none;
  }

  .search {
    max-width: none;
  }
}

@media (max-width: 640px) {
  .brand {
    margin-right: auto;
  }

  .search-wrap {
    order: 3;
    flex-basis: 100%;
  }
}
</style>
```

- [ ] **Step 2: 在 AppHeader 中替换导航区块**

加入 import：

```typescript
import HeaderNav from '@/components/HeaderNav.vue'
```

把 template 第 102-124 行的 brand、primary-nav、search-wrap 整段替换为：

```vue
<HeaderNav
  v-model:keyword="keyword"
  :is-hot="isHot"
  @search="onSearch"
  @go-articles="goArticles"
  @go-hot="goHot"
/>
```

删除 `AppHeader.vue` 中不再使用的 `Search` import。

- [ ] **Step 3: 处理 AppHeader 布局壳与移动端样式**

父组件保留 `.app-header` 和 `.header-inner`：

```css
.app-header {
  border-bottom: 1px solid var(--blog-line);
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(12px);
}

.header-inner {
  width: 100%;
  max-width: var(--blog-content-width);
  min-height: 64px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 24px;
}

@media (max-width: 900px) {
  .header-inner {
    gap: 10px;
    padding: 0 14px;
  }
}

@media (max-width: 640px) {
  .header-inner {
    min-height: 58px;
    flex-wrap: wrap;
    padding-block: 8px;
  }
}
```

删除父组件中已搬走的 `.brand`、`.nav-link`、`.primary-nav`、`.search-wrap`、`.search` 规则，以及对应媒体查询片段。

- [ ] **Step 4: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/HeaderNav.vue frontend/src/components/AppHeader.vue
git commit -m "refactor(frontend): 抽出顶部导航与搜索"
```

---

### Task 10: 拆分 AppHeader 通知为 HeaderNotifications

**Files:**
- Create: `frontend/src/components/HeaderNotifications.vue`
- Modify: `frontend/src/components/AppHeader.vue`（template 第 126-164 行通知下拉、script 第 1-99 行通知逻辑）

- [ ] **Step 1: 创建 HeaderNotifications 组件**

创建 `frontend/src/components/HeaderNotifications.vue`，自己管理通知状态：

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { Bell } from '@element-plus/icons-vue'

import { clearNotifications, getNotifications, markAllNotifications } from '@/api/notification'
import { useUserStore } from '@/stores/user'
import type { NotificationVO } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

const notifyList = ref<NotificationVO[]>([])
const unreadCount = ref(0)

async function loadNotifications() {
  try {
    const page = await getNotifications({ pageNum: 1, pageSize: 5 })
    notifyList.value = page.list
    unreadCount.value = Number(page.unreadCount)
  } catch {
    // 全局拦截器已提示错误，保留当前列表状态。
  }
}

function onNotifyVisible(visible: boolean) {
  if (visible) loadNotifications()
}

async function onMarkAllRead() {
  try {
    await markAllNotifications()
    await loadNotifications()
  } catch {
    // 全局拦截器已提示错误。
  }
}

async function onClearNotifications() {
  try {
    await ElMessageBox.confirm(
      '清空后全部通知将被删除且不可恢复，确定清空吗？',
      '清空确认',
      { type: 'warning' },
    )
  } catch {
    return
  }

  await clearNotifications()
  ElMessage.success('通知已清空')
  await loadNotifications()
}

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      loadNotifications()
    } else {
      notifyList.value = []
      unreadCount.value = 0
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
    <el-dropdown placement="bottom-end" popper-class="notify-popper" @visible-change="onNotifyVisible">
      <button class="icon-button" type="button" aria-label="查看通知">
        <el-icon :size="19"><Bell /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <div class="notify-head">
            <span>通知</span>
            <span class="notify-head-actions">
              <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="onMarkAllRead">
                全部已读
              </el-button>
              <el-button
                v-if="notifyList.length > 0"
                link
                type="danger"
                size="small"
                @click="onClearNotifications"
              >
                清空
              </el-button>
            </span>
          </div>
          <el-empty v-if="notifyList.length === 0" description="暂无通知" :image-size="60" />
          <el-dropdown-item
            v-for="n in notifyList"
            :key="n.id"
            class="notify-item"
            @click="router.push(`/articles/${n.articleId}`)"
          >
            <span class="notify-dot" :class="{ unread: n.isRead === 0 }" />
            {{ n.senderNickname || '匿名' }}
            {{ n.type === 'LIKE' ? '赞了你的文章' : (n.isRead === 0 ? '评论/回复了你' : '评论了你') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </el-badge>
</template>

<style scoped>
.icon-button {
  min-width: 40px;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 12px;
  border-bottom: 1px solid var(--blog-line-soft);
}

.notify-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.notify-item {
  white-space: normal;
  line-height: 1.5;
}

.notify-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: transparent;
}

.notify-dot.unread {
  background: var(--blog-red);
}
</style>
```

`.notify-popper` 是全局面板样式（原文件末尾非 scoped `<style>`），移到 `AppHeader.vue` 保留，或复制到本组件末尾的 `<style>`。本计划选择**保留在 AppHeader.vue**，因为它作用于 teleport 到 body 的下拉面板，与组件挂载位置无关。

- [ ] **Step 2: 在 AppHeader 中删除通知逻辑**

从 `AppHeader.vue` 删除：

- script 中的 `notifyList`、`unreadCount`、`loadNotifications`、`onNotifyVisible`、`onMarkAllRead`、`onClearNotifications`、`watch` 段落。
- 相关 imports：`Bell`、`clearNotifications`、`getNotifications`、`markAllNotifications`、`NotificationVO`，以及不再需要的 `watch`、`ElMessageBox`、两个 message-box 样式 import。
- `ElMessage` 仍被 `onUserCommand` 使用，保留。

- [ ] **Step 3: 在 AppHeader 模板中替换通知下拉**

加入 import：

```typescript
import HeaderNotifications from '@/components/HeaderNotifications.vue'
```

把 template 第 126-164 行整段 `<el-badge>...</el-badge>` 替换为：

```vue
<HeaderNotifications />
```

- [ ] **Step 4: 从 AppHeader 的 scoped 样式删除通知规则**

删除 `.notify-head`、`.notify-head-actions`、`.notify-item`、`.notify-dot`、`.notify-dot.unread`。保留 `.icon-button` 中仍被用户菜单使用的部分（`.icon-button, .user-trigger` 共享规则需保留 `.user-trigger`，可在 HeaderUserMenu 完成后统一处理）。

保留文件末尾全局 `<style>`：

```css
.notify-popper {
  width: 320px;
}
```

- [ ] **Step 5: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/HeaderNotifications.vue frontend/src/components/AppHeader.vue
git commit -m "refactor(frontend): 抽出顶部通知下拉"
```

---

### Task 11: 拆分 AppHeader 用户菜单为 HeaderUserMenu

**Files:**
- Create: `frontend/src/components/HeaderUserMenu.vue`
- Modify: `frontend/src/components/AppHeader.vue`（template 第 166-196 行用户菜单与登录按钮）

- [ ] **Step 1: 创建 HeaderUserMenu 组件**

创建 `frontend/src/components/HeaderUserMenu.vue`：

```vue
<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue'

defineProps<{
  isLoggedIn: boolean
  nickname: string
}>()

defineEmits<{
  publish: []
  profile: []
  logout: []
  login: []
  register: []
}>()
</script>

<template>
  <template v-if="isLoggedIn">
    <el-button class="publish-button" type="primary" @click="$emit('publish')">写文章</el-button>

    <el-dropdown @command="(command: string) => $emit(command === 'profile' ? 'profile' : 'logout')">
      <button class="user-trigger" type="button" aria-label="账户菜单">
        <span class="user-avatar">{{ nickname.charAt(0) || '?' }}</span>
        <el-icon><ArrowDown /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">个人中心</el-dropdown-item>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </template>

  <template v-else>
    <el-button @click="$emit('login')">登录</el-button>
    <el-button type="primary" @click="$emit('register')">注册</el-button>
  </template>
</template>

<style scoped>
.user-trigger {
  min-width: 40px;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--blog-moss);
  color: var(--blog-moss-ink);
  font-weight: 700;
}

@media (max-width: 640px) {
  .publish-button {
    display: none;
  }
}
</style>
```

注意：`$emit` 在模板中不能直接用三元表达式调用带参形式做类型推断，若 `vue-tsc` 报错，改为在 script 中定义：

```typescript
function onUserCommand(command: string) {
  if (command === 'profile') emit('profile')
  else emit('logout')
}
```

并改用 `@command="onUserCommand"`。

- [ ] **Step 2: 在 AppHeader 中替换用户菜单**

加入 import：

```typescript
import HeaderUserMenu from '@/components/HeaderUserMenu.vue'
```

把 template 第 166-196 行用户菜单和未登录按钮整段替换为：

```vue
<HeaderUserMenu
  :is-logged-in="userStore.isLoggedIn"
  :nickname="userStore.user?.nickname || userStore.user?.username || ''"
  @publish="router.push('/publish')"
  @profile="onUserCommand('profile')"
  @logout="onUserCommand('logout')"
  @login="router.push('/login')"
  @register="router.push('/register')"
/>
```

删除 `ArrowDown` import。

- [ ] **Step 3: 从 AppHeader 的 scoped 样式删除用户菜单规则**

删除 `.publish-button` 的移动端隐藏规则、`.user-trigger`、`.user-avatar`。

`.icon-button, .user-trigger` 共享规则只保留 `.actions`、`.icon-button` 相关；`.brand, .nav-link, .icon-button, .user-trigger` 共享规则改为 `.icon-button` 独立规则：

```css
.icon-button {
  min-width: 40px;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
```

保留 `.actions`、`.header-inner` 和媒体查询里的 `.publish-button` 兜底规则（可删除，因按钮已搬走）。

- [ ] **Step 4: 运行静态检查并提交**

Run:

```powershell
cd frontend
npm run type-check
npx eslint .
npx oxlint .
```

Expected: PASS。

```powershell
git add -- frontend/src/components/HeaderUserMenu.vue frontend/src/components/AppHeader.vue
git commit -m "refactor(frontend): 抽出顶部用户菜单"
```

---

### Task 12: 完整回归与手动验证

**Files:**
- Modify: `docs/superpowers/plans/2026-09-21-blog-platform-frontend-structure.md`

- [ ] **Step 1: 运行完整前端验证**

Run:

```powershell
cd frontend
npm test
npm run type-check
npx eslint .
npx oxlint .
npm run build
```

Expected: 全部 PASS。

- [ ] **Step 2: 确认四个胖组件已瘦身**

Run:

```powershell
rg -c "" frontend/src/views/ArticleDetail.vue frontend/src/views/ArticleEdit.vue frontend/src/views/Profile.vue frontend/src/components/AppHeader.vue
```

Expected: 每个文件行数明显低于拆分前，`ArticleDetail.vue` 与 `ArticleEdit.vue` 约为原行数一半。

- [ ] **Step 3: 手动回归四个页面**

启动开发服务：

```powershell
cd frontend
npm run dev
```

逐项验证：

- 文章详情：点赞、收藏、评论分页、回复、删除评论、删除文章。
- 文章编辑：工具栏、大纲跳转、预览、移动端四面板、存草稿、发布。
- 个人中心：两个标签页分页、隐藏、删除、取消收藏。
- 顶部导航：搜索、发现与热榜切换、通知下拉、用户菜单、移动端布局。

Expected: 行为与拆分前一致，移动端顶栏、编辑器面板和详情页底部操作条均不塌陷。

- [ ] **Step 4: 勾选验收项并提交**

```powershell
git add -- docs/superpowers/plans/2026-09-21-blog-platform-frontend-structure.md
git commit -m "docs: 完成前端结构收敛验证"
```

---

## 验收清单

- [ ] `ArticleDetail.vue` 拆出 `ArticleDetailHeader`、`ArticleRail`、`ArticleComments`。
- [ ] `ArticleEdit.vue` 拆出 `EditorDocumentBar`、`EditorMetadataFields`、`EditorPanels`。
- [ ] `Profile.vue` 拆出 `ProfileArticleLedger`、`ProfileFavoriteLedger`。
- [ ] `AppHeader.vue` 拆出 `HeaderNav`、`HeaderNotifications`、`HeaderUserMenu`。
- [ ] 所有子组件通过 props 和 emits 通信，通知下拉独立管理状态。
- [ ] 页面视觉、交互流程和接口调用保持不变。
- [ ] 类型检查、两个 Linter、构建和既有测试全部通过。
- [ ] 四个页面手动回归全部通过。
