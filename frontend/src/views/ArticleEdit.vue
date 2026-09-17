<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import MarkdownIt from 'markdown-it'

import { getArticleEditData, publishArticle, updateArticle, type ArticleFormData } from '@/api/article'
import AppShell from '@/components/AppShell.vue'
import EditorOutline from '@/components/EditorOutline.vue'
import EditorToolbar from '@/components/EditorToolbar.vue'
import {
  countEditorStats,
  insertMarkdown,
  parseOutline,
  type MarkdownAction,
} from '@/utils/editor'

const md = new MarkdownIt({ breaks: true })

const route = useRoute()
const router = useRouter()
const articleId = computed(() => (route.params.id ? (route.params.id as string) : undefined))

const formRef = ref<FormInstance>()
const contentTextarea = ref<HTMLTextAreaElement | null>(null)
const pageLoading = ref(false)
const saving = ref(false)
const loadedStatus = ref(1)
const cursorPosition = ref(0)
const mobilePanel = ref<'edit' | 'preview' | 'outline' | 'info'>('edit')

const form = reactive<ArticleFormData>({
  title: '',
  category: '',
  tags: '',
  content: '',
  status: 1,
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' },
  ],
  category: [{ max: 50, message: '分类不能超过 50 个字符', trigger: 'blur' }],
  tags: [{ max: 255, message: '标签不能超过 255 个字符', trigger: 'blur' }],
  content: [{ required: true, message: '请输入文章内容', trigger: 'blur' }],
}

const previewHtml = computed(() => md.render(form.content))
const showDraftButton = computed(() => !articleId.value || loadedStatus.value === 0)
const outline = computed(() => parseOutline(form.content))
const editorStats = computed(() => countEditorStats(form.content, cursorPosition.value))

async function loadForEdit() {
  if (!articleId.value) return

  pageLoading.value = true
  try {
    const article = await getArticleEditData(articleId.value)
    form.title = article.title
    form.category = article.category ?? ''
    form.tags = article.tags ?? ''
    form.content = article.content
    form.status = article.status
    loadedStatus.value = article.status
  } finally {
    pageLoading.value = false
  }
}

async function onSubmit(status: 0 | 1) {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload: ArticleFormData = { ...form, status }
    const saved = articleId.value
      ? await updateArticle(articleId.value, payload)
      : await publishArticle(payload)

    if (status === 0) {
      ElMessage.success('草稿已保存')
      await router.push('/profile')
    } else {
      ElMessage.success(articleId.value && loadedStatus.value === 1 ? '更新成功' : '发布成功')
      await router.push(`/articles/${saved.id}`)
    }
  } catch {
    // 错误由请求拦截器统一提示。
  } finally {
    saving.value = false
  }
}

function updateCursor(event: Event) {
  cursorPosition.value = (event.target as HTMLTextAreaElement).selectionStart
}

async function applyMarkdown(action: MarkdownAction) {
  const textarea = contentTextarea.value
  if (!textarea) return

  const result = insertMarkdown(
    form.content,
    textarea.selectionStart,
    textarea.selectionEnd,
    action,
  )
  form.content = result.value

  await nextTick()
  textarea.focus()
  textarea.setSelectionRange(result.selectionStart, result.selectionEnd)
  cursorPosition.value = result.selectionEnd
  mobilePanel.value = 'edit'
}

async function navigateToLine(line: number) {
  const textarea = contentTextarea.value
  if (!textarea) return

  const lines = form.content.split('\n')
  const offset = lines.slice(0, line - 1).join('\n').length + (line > 1 ? 1 : 0)
  textarea.focus()
  textarea.setSelectionRange(offset, offset)
  cursorPosition.value = offset
  mobilePanel.value = 'edit'
}

watch(
  articleId,
  (id, oldId) => {
    if (!id) {
      form.title = ''
      form.category = ''
      form.tags = ''
      form.content = ''
      form.status = 1
      loadedStatus.value = 1
    } else if (id !== oldId) {
      loadForEdit()
    }
  },
  { immediate: true },
)
</script>

<template>
  <AppShell variant="workspace">
    <div v-loading="pageLoading" class="editor-app">
      <header class="document-bar">
        <el-button link @click="router.back()">返回</el-button>
        <span class="document-state">{{ articleId ? 'EDITING' : 'DRAFT' }}</span>
        <strong class="document-title">{{ form.title || '未命名文章' }}</strong>
        <span class="document-count">{{ editorStats.characters }} 字</span>
        <el-button v-if="showDraftButton" :disabled="saving" @click="onSubmit(0)">存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit(1)">
          {{ articleId && loadedStatus === 1 ? '保存修改' : '发布文章' }}
        </el-button>
      </header>

      <el-alert
        v-if="articleId && loadedStatus === 0"
        class="draft-alert"
        type="warning"
        :closable="false"
        show-icon
        title="当前是草稿，仅自己可见；发布后才会对外展示"
      />

      <el-form ref="formRef" :model="form" :rules="rules" class="editor-form">
        <EditorToolbar @apply="applyMarkdown" />

        <nav class="tablet-editor-nav" aria-label="编辑与预览切换">
          <button
            type="button"
            :class="{ active: mobilePanel === 'edit' }"
            @click="mobilePanel = 'edit'"
          >
            编辑
          </button>
          <button
            type="button"
            :class="{ active: mobilePanel === 'preview' }"
            @click="mobilePanel = 'preview'"
          >
            预览
          </button>
        </nav>

        <div class="editor-grid" :class="`mobile-panel-${mobilePanel}`">
          <EditorOutline class="desktop-outline" :items="outline" @navigate="navigateToLine" />

          <section class="document-panel">
            <div class="metadata-fields">
              <el-form-item prop="title" class="title-field">
                <el-input
                  v-model="form.title"
                  size="large"
                  maxlength="200"
                  placeholder="文章标题"
                  aria-label="文章标题"
                />
              </el-form-item>
              <el-form-item prop="category">
                <el-input v-model="form.category" placeholder="分类" aria-label="文章分类" />
              </el-form-item>
              <el-form-item prop="tags">
                <el-input
                  v-model="form.tags"
                  placeholder="标签，使用英文逗号分隔"
                  aria-label="文章标签"
                />
              </el-form-item>
            </div>

            <el-form-item prop="content" class="content-field">
              <textarea
                ref="contentTextarea"
                v-model="form.content"
                class="markdown-input"
                placeholder="使用 Markdown 开始写作"
                aria-label="Markdown 正文"
                @blur="updateCursor"
                @select="updateCursor"
                @click="updateCursor"
                @keyup="updateCursor"
              />
            </el-form-item>
          </section>

          <section class="preview-panel">
            <div class="panel-label">PREVIEW</div>
            <div class="editor-preview markdown-body" v-html="previewHtml" />
          </section>

          <section class="mobile-outline-panel">
            <EditorOutline :items="outline" @navigate="navigateToLine" />
          </section>

          <section class="mobile-info-panel">
            <div class="panel-label">DOCUMENT</div>
            <p>分类：{{ form.category || '未分类' }}</p>
            <p>标签：{{ form.tags || '暂无标签' }}</p>
            <p>状态：{{ form.status === 0 ? '草稿' : '已发布' }}</p>
          </section>
        </div>
      </el-form>

      <footer class="status-bar">
        <span>{{ editorStats.characters }} 字</span>
        <span>行 {{ editorStats.line }}，列 {{ editorStats.column }}</span>
        <span>UTF-8</span>
        <span>Markdown</span>
      </footer>

      <nav class="mobile-editor-nav" aria-label="编辑器面板">
        <button
          type="button"
          :class="{ active: mobilePanel === 'edit' }"
          @click="mobilePanel = 'edit'"
        >
          编辑
        </button>
        <button
          type="button"
          :class="{ active: mobilePanel === 'preview' }"
          @click="mobilePanel = 'preview'"
        >
          预览
        </button>
        <button
          type="button"
          :class="{ active: mobilePanel === 'outline' }"
          @click="mobilePanel = 'outline'"
        >
          大纲
        </button>
        <button
          type="button"
          :class="{ active: mobilePanel === 'info' }"
          @click="mobilePanel = 'info'"
        >
          信息
        </button>
      </nav>
    </div>
  </AppShell>
</template>

<style scoped>
.editor-app {
  border: 1px solid var(--blog-line);
  background: var(--blog-paper);
  box-shadow: var(--blog-shadow-sm);
}

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
.document-count,
.panel-label {
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

.draft-alert {
  margin: 0;
}

.editor-form {
  display: block;
}

.editor-grid {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr) minmax(0, 1fr);
  min-height: 620px;
}

.document-panel,
.preview-panel {
  min-width: 0;
  padding: 20px 24px;
}

.document-panel {
  border-right: 1px solid var(--blog-line);
}

.preview-panel {
  background: #f9fbfc;
}

.metadata-fields {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(120px, 0.7fr) minmax(160px, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.metadata-fields :deep(.el-form-item),
.content-field {
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

.content-field :deep(.el-form-item__content) {
  display: block;
}

.markdown-input {
  width: 100%;
  min-height: 520px;
  padding: 8px 2px;
  border: 0;
  outline: 0;
  color: var(--blog-ink-soft);
  background: transparent;
  font-family: var(--blog-font-mono);
  font-size: 13px;
  line-height: 1.9;
  resize: vertical;
}

.editor-preview {
  min-height: 520px;
}

.status-bar {
  min-height: 30px;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 14px;
  border-top: 1px solid var(--blog-line);
  background: var(--blog-blueprint);
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 9px;
}

.mobile-outline-panel,
.mobile-info-panel,
.tablet-editor-nav,
.mobile-editor-nav {
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

  .editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .desktop-outline {
    display: none;
  }

  .preview-panel {
    display: none;
  }

  .mobile-panel-preview .preview-panel {
    display: block;
  }
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

  .editor-grid {
    display: block;
    min-height: 560px;
    padding-bottom: 56px;
  }

  .document-panel,
  .preview-panel,
  .mobile-outline-panel,
  .mobile-info-panel {
    display: none;
    padding: 16px;
  }

  .metadata-fields {
    grid-template-columns: 1fr;
  }

  .mobile-panel-edit .document-panel,
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

  .mobile-editor-nav {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    min-height: 54px;
    background: var(--blog-ink);
  }

  .tablet-editor-nav {
    display: none;
  }

  .mobile-editor-nav button {
    min-height: 54px;
    border: 0;
    background: transparent;
    color: #b8c9d7;
  }

  .mobile-editor-nav button.active {
    color: var(--blog-paper);
    box-shadow: inset 0 -2px var(--blog-red);
  }

  .status-bar {
    display: none;
  }
}
</style>
