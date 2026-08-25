<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { getArticleDetail, publishArticle, updateArticle } from '@/api/article'
import type { ArticleFormData } from '@/api/article'

const md = new MarkdownIt({ breaks: true })

const route = useRoute()
const router = useRouter()

/** 有 id 即编辑模式 */
const articleId = route.params.id as string | undefined

const formRef = ref<FormInstance>()
const pageLoading = ref(false)
const saving = ref(false)
const form = reactive<ArticleFormData>({
  title: '',
  category: '',
  tags: '',
  content: '',
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

async function loadForEdit() {
  if (!articleId) return
  pageLoading.value = true
  try {
    const article = await getArticleDetail(articleId)
    form.title = article.title
    form.category = article.category ?? ''
    form.tags = article.tags ?? ''
    form.content = article.content
  } finally {
    pageLoading.value = false
  }
}

async function onSubmit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const saved = articleId
      ? await updateArticle(articleId, form)
      : await publishArticle(form)
    ElMessage.success(articleId ? '更新成功' : '发布成功')
    await router.push(`/articles/${saved.id}`)
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

onMounted(loadForEdit)
</script>

<template>
  <div v-loading="pageLoading" class="article-edit">
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
        <el-button type="primary" size="large" :loading="saving" @click="onSubmit">
          {{ articleId ? '保存修改' : '发布文章' }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
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
.editor-preview :deep(img) {
  max-width: 100%;
}
@media (max-width: 900px) {
  .editor {
    grid-template-columns: 1fr;
  }
}
</style>
