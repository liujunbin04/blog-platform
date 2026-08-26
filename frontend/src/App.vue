<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
// 按需导入改造后不再有 app.use(ElementPlus)，中文 locale 改由 config-provider 注入
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <el-container class="layout">
      <el-header class="layout-header">
        <AppHeader />
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-config-provider>
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
