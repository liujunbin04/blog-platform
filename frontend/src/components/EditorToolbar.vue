<script setup lang="ts">
import type { MarkdownAction } from '@/utils/editor'

defineEmits<{
  apply: [action: MarkdownAction]
}>()

const actions: Array<{ action: MarkdownAction; label: string; title: string }> = [
  { action: 'heading', label: 'H2', title: '二级标题' },
  { action: 'bold', label: 'B', title: '粗体' },
  { action: 'italic', label: 'I', title: '斜体' },
  { action: 'quote', label: '引用', title: '引用' },
  { action: 'unordered-list', label: '• 列表', title: '无序列表' },
  { action: 'ordered-list', label: '1. 列表', title: '有序列表' },
  { action: 'link', label: '链接', title: '链接' },
  { action: 'code', label: '</>', title: '行内代码' },
]
</script>

<template>
  <div class="editor-toolbar" role="toolbar" aria-label="Markdown 格式">
    <button
      v-for="item in actions"
      :key="item.action"
      type="button"
      :title="item.title"
      :aria-label="item.title"
      @click="$emit('apply', item.action)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.editor-toolbar {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 14px;
  border-bottom: 1px solid var(--blog-line);
  background: #f3f6f8;
  overflow-x: auto;
}

button {
  flex: 0 0 auto;
  min-width: 34px;
  min-height: 30px;
  padding: 0 8px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--blog-ink-soft);
  cursor: pointer;
}

button:hover {
  border-color: var(--blog-line);
  background: var(--blog-paper);
}
</style>
