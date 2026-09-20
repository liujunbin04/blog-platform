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
  <!-- 多根组件：三段内容需作为 .header-inner 的直接 flex 子元素；不要加包裹元素，也不支持属性透传。 -->
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
