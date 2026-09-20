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
