<script setup lang="ts">
import type { OutlineItem } from '@/utils/editor'

defineProps<{
  items: OutlineItem[]
}>()

defineEmits<{
  navigate: [line: number]
}>()
</script>

<template>
  <section class="editor-outline" aria-label="文章大纲">
    <div class="outline-label">OUTLINE</div>
    <p v-if="items.length === 0" class="empty">输入 Markdown 标题后自动生成大纲。</p>
    <button
      v-for="item in items"
      :key="`${item.line}-${item.text}`"
      type="button"
      :style="{ paddingLeft: `${8 + (item.level - 1) * 12}px` }"
      @click="$emit('navigate', item.line)"
    >
      {{ item.text }}
    </button>
  </section>
</template>

<style scoped>
.editor-outline {
  padding: 14px;
  border-right: 1px solid var(--blog-line);
  background: var(--blog-blueprint);
}

.outline-label {
  margin-bottom: 12px;
  color: var(--blog-muted);
  font-family: var(--blog-font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.empty {
  color: var(--blog-muted);
  font-size: 11px;
  line-height: 1.7;
}

button {
  width: 100%;
  padding: 7px 8px;
  border: 0;
  background: transparent;
  color: var(--blog-ink-soft);
  font-size: 11px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
}

button:hover {
  background: var(--blog-paper);
  color: var(--blog-ink);
}
</style>
