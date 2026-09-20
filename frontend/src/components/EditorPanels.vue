<script setup lang="ts">
import type { ArticleFormData } from '@/api/article'
import EditorOutline from '@/components/EditorOutline.vue'
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
}

@media (max-width: 767px) {
  .preview-panel {
    padding: 16px;
  }

  .mobile-outline-panel {
    padding: 16px;
  }

  .mobile-info-panel {
    padding: 16px;
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
