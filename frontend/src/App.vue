<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
</script>

<template>
  <el-container class="layout">
    <el-header class="layout-header">
      <AppHeader />
    </el-header>
    <el-main class="layout-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-header {
  padding: 0;
  border-bottom: 1px solid var(--el-border-color-light);
}
.layout-main {
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}
</style>
