<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
// 显式 import 的 ElMessage 对 resolver 不可见，样式需手动副作用导入
import 'element-plus/es/components/message/style/css'
import MarkdownIt from 'markdown-it'
import { getArticleEditData, publishArticle, updateArticle } from '@/api/article'
import type { ArticleFormData } from '@/api/article'

const md = new MarkdownIt({ breaks: true })

const route = useRoute()
const router = useRouter()

/** 有 id 即编辑模式；computed 使 /publish ↔ /publish/:id 互跳时组件复用也能正确切换 */
const articleId = computed(() =>
  route.params.id ? (route.params.id as string) : undefined,
)

const formRef = ref<FormInstance>()
const pageLoading = ref(false)
const saving = ref(false)
/** 编辑中的文章当前状态（0 草稿 / 1 已发布 / 2 已隐藏），决定「存草稿」按钮是否展示 */
const loadedStatus = ref(1)
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

/** 存草稿按钮：仅新建、编辑草稿时展示；已发布文章不提供（避免误把已发布文章改回草稿） */
const showDraftButton = computed(() => !articleId.value || loadedStatus.value === 0)

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

/**
 * 保存：status=1 发布 / 0 存草稿。
 * 草稿保存成功后跳「我的文章」（草稿不在公共列表展示，详情页他人不可见）
 */
async function onSubmit(status: 0 | 1) {
  // validate() 在校验失败时 reject：捕获并早退，字段错误提示由表单自行展示
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
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

watch(
  articleId,
  (id, oldId) => {
    if (!id) {
      // 进入新建模式：清空上一次编辑残留
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
  <div v-loading="pageLoading" class="article-edit">
    <!-- 编辑草稿时提示可见性 -->
    <el-alert
      v-if="articleId && loadedStatus === 0"
      class="draft-alert"
      type="warning"
      :closable="false"
      show-icon
      title="当前是草稿，仅自己可见；点击「发布文章」后才会对外展示"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
      <el-form-item prop="title">
        <el-input v-model="form.title" size="large" placeholder="文章标题（必填，最多 200 字）" />
      </el-form-item>
      <el-form-item prop="category">
        <el-input v-model="form.category" placeholder="分类（选填，如：技术 / 生活）" style="max-width: 240px" />
      </el-form-item>
      <el-form-item prop="tags">
        <el-input v-model="form.tags" placeholder="标签（选填，英文逗号分隔，如：vue,spring）" style="max-width: 360px" />
      </el-form-item>
      <el-form-item prop="content">
        <div class="editor">
          <el-input
            v-model="form.content"
            type="textarea"
            class="editor-input"
            :autosize="{ minRows: 20, maxRows: 30 }"
            placeholder="支持 Markdown 语法…"
          />
          <div class="editor-preview markdown-body" v-html="previewHtml" />
        </div>
      </el-form-item>
      <el-form-item>
        <div class="submit-row">
          <el-button v-if="showDraftButton" size="large" :disabled="saving" @click="onSubmit(0)">
            存草稿
          </el-button>
          <el-button type="primary" size="large" :loading="saving" @click="onSubmit(1)">
            {{ articleId && loadedStatus === 1 ? '保存修改' : '发布文章' }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.draft-alert {
  margin-bottom: 16px;
}
.submit-row {
  display: flex;
  gap: 12px;
  width: 100%;
}
.editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
}
.editor-input :deep(.el-textarea__inner) {
  height: 100%;
  font-family: Consolas, Monaco, monospace;
}
.editor-preview {
  min-height: 480px;
  padding: 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  overflow-wrap: break-word;
}
@media (max-width: 900px) {
  .editor {
    grid-template-columns: 1fr;
  }
}
</style>
