<script setup lang="ts">
interface MetadataFields {
  title: string
  category?: string
  tags?: string
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
