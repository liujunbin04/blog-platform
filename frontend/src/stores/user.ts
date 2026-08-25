import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginVO, UserVO } from '@/types/api'

const TOKEN_KEY = 'blog_token'
const USER_KEY = 'blog_user'

function loadStoredUser(): UserVO | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserVO) : null
  } catch {
    // localStorage 数据被手动改坏时不阻塞启动，视为未登录
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  // 初始化时从 localStorage 读回，刷新页面不丢登录态
  const token = ref(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<UserVO | null>(loadStoredUser())

  const isLoggedIn = computed(() => token.value !== '')

  /** 登录成功后写入状态并持久化 */
  function setLogin(loginVO: LoginVO) {
    token.value = loginVO.token
    user.value = loginVO.user
    localStorage.setItem(TOKEN_KEY, loginVO.token)
    localStorage.setItem(USER_KEY, JSON.stringify(loginVO.user))
  }

  /** 退出登录 / 登录过期：清空状态与持久化数据 */
  function clearLogin() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, isLoggedIn, setLogin, clearLogin }
})
