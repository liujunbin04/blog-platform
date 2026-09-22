<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue'

defineProps<{
  isLoggedIn: boolean
  nickname: string
}>()

const emit = defineEmits<{
  publish: []
  profile: []
  logout: []
  login: []
  register: []
}>()

function onUserCommand(command: string) {
  if (command === 'profile') emit('profile')
  else if (command === 'logout') emit('logout')
}
</script>

<template>
  <!-- 多根组件：各按钮需作为 .actions 的直接 flex 子元素；不要加包裹元素，也不支持属性透传。 -->
  <template v-if="isLoggedIn">
    <el-button class="publish-button" type="primary" @click="$emit('publish')">写文章</el-button>

    <el-dropdown @command="onUserCommand">
      <button class="user-trigger" type="button" aria-label="账户菜单">
        <span class="user-avatar">{{ nickname.charAt(0) || '?' }}</span>
        <el-icon><ArrowDown /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">个人中心</el-dropdown-item>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </template>

  <template v-else>
    <el-button @click="$emit('login')">登录</el-button>
    <el-button type="primary" @click="$emit('register')">注册</el-button>
  </template>
</template>

<style scoped>
.user-trigger {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  min-width: 40px;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--blog-moss);
  color: var(--blog-moss-ink);
  font-weight: 700;
}

@media (max-width: 640px) {
  .publish-button {
    display: none;
  }
}
</style>
