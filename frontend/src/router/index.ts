import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/',
      redirect: '/profile',
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/delegations',
      name: 'delegations',
      component: () => import('@/views/DelegationsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/test-access',
      name: 'test-access',
      component: () => import('@/views/TestAccessView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('dac_token')

  if (to.meta.requiresAuth && !token) {
    next({ name: 'login' })
  } else if (to.meta.requiresGuest && token) {
    next({ name: 'profile' })
  } else {
    next()
  }
})

export default router
