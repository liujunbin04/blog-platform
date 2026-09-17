<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'

import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="layout">
      <header class="layout-header">
        <AppHeader />
      </header>
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </el-config-provider>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 30;
}

.page-enter-active,
.page-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.page-enter-from,
.page-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
