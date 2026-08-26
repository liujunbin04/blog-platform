import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/articles' },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/articles',
      name: 'article-list',
      component: () => import('@/views/ArticleList.vue'),
    },
    {
      path: '/articles/:id',
      name: 'article-detail',
      component: () => import('@/views/ArticleDetail.vue'),
    },
    {
      // 可选数字参数：无 id 为新建，有 id 为编辑
      path: '/publish/:id(\\d+)?',
      name: 'article-edit',
      component: () => import('@/views/ArticleEdit.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/Profile.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

// 全局前置守卫：
// 1. 需要登录的页面未登录 → 登录页并记录回跳地址
// 2. 已登录访问登录/注册页 → 直接回文章列表
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && userStore.isLoggedIn) {
    return { path: '/articles' }
  }
  return true
})

export default router
