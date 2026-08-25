<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')

/** 回车或点击按钮：带关键词跳转文章列表页 */
function onSearch() {
  const kw = keyword.value.trim()
  router.push(kw ? { path: '/articles', query: { keyword: kw } } : { path: '/articles' })
}

function onUserCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    userStore.clearLogin()
    ElMessage.success('已退出登录')
    router.push('/articles')
  }
}
</script>

<template>
  <div class="app-header">
    <span class="logo" @click="router.push('/articles')">我的博客</span>
    <el-input
      v-model="keyword"
      class="search"
      placeholder="搜索文章"
      clearable
      @keyup.enter="onSearch"
    >
      <template #append>
        <el-button @click="onSearch">
          <el-icon><search /></el-icon>
        </el-button>
      </template>
    </el-input>
    <div class="actions">
      <template v-if="userStore.isLoggedIn">
        <el-button type="primary" @click="router.push('/publish')">发布文章</el-button>
        <el-dropdown @command="onUserCommand">
          <span class="nickname">
            {{ userStore.user?.nickname || userStore.user?.username }}
            <el-icon><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
      <template v-else>
        <el-button @click="router.push('/login')">登录</el-button>
        <el-button type="primary" @click="router.push('/register')">注册</el-button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
  padding: 0 24px;
}
.logo {
  font-size: 20px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.search {
  max-width: 360px;
}
.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}
</style>
