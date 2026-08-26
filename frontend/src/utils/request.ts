import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  // 生产部署通过 VITE_API_BASE 指向后端；开发环境缺省直连本地服务
  baseURL: import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api',
  timeout: 10000,
})

// 请求拦截器：自动携带 token（在函数体内调用 useUserStore，
// 保证此时 Pinia 已随 app 初始化完成）
request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 并发请求同时收到 401 时只处理第一个：其余跳过提示与跳转，避免警告轰炸
let handlingUnauthorized = false

/** 未登录 / 登录过期统一处理：清空本地状态并跳转登录页 */
function handleUnauthorized(message: string) {
  const userStore = useUserStore()
  userStore.clearLogin()
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  ElMessage.warning(message)
  if (router.currentRoute.value.path !== '/login') {
    router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    }).finally(() => {
      handlingUnauthorized = false
    })
  } else {
    handlingUnauthorized = false
  }
}

// 响应拦截器：解包统一返回结构 Result<T>，业务层直接拿到 data。
// 注意后端 GlobalExceptionHandler 不改 HTTP 状态码（业务错误也是 HTTP 200），
// 因此 code 判断必须在成功回调里做；HTTP 层错误（如安全过滤器直接返回的 401）走错误回调
request.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result.code === 200) {
      return result.data
    }
    if (result.code === 401) {
      handleUnauthorized(result.message || '未登录或登录已过期')
    } else {
      ElMessage.error(result.message || '请求失败')
    }
    return Promise.reject(new Error(result.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      handleUnauthorized(message || '未登录或登录已过期')
    } else {
      ElMessage.error(message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default request
