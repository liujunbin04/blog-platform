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
