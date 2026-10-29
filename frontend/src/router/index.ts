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
      path: '/simrs',
      name: 'simrs',
      component: () => import('@/views/SimrsWorkspaceView.vue'),
      meta: { requiresAuth: true, requiresDelegationOrAdmin: true },
    },
    {
      path: '/delegations',
      name: 'delegations',
      component: () => import('@/views/DelegationsView.vue'),
      meta: { requiresAuth: true, requiresDoctor: true },
    },
    {
      path: '/test-access',
      name: 'test-access',
      component: () => import('@/views/TestAccessView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
  ],
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('dac_token')
  const userStr = localStorage.getItem('dac_user')
  let isDoc = false
  let isAdmin = false
  const hasActiveDel = localStorage.getItem('dac_has_active_del') === 'true'
  if (userStr) {
    try {
      const u = JSON.parse(userStr)
      isDoc = u.roleName?.toLowerCase().includes('dokter') || u.roleId === 101
      isAdmin = u.roleName?.toLowerCase().includes('admin') || u.roleId === 1
    } catch {}
  }

  if (to.meta.requiresAuth && !token) {
    next({ name: 'login' })
  } else if (to.meta.requiresGuest && token) {
    next({ name: 'profile' })
  } else if (to.meta.requiresDoctor && !isDoc) {
    next({ name: 'profile' })
  } else if (to.meta.requiresAdmin && !isAdmin) {
    next({ name: 'profile' })
  } else if (to.meta.requiresDelegationOrAdmin && !isAdmin && !hasActiveDel) {
    next({ name: 'profile' })
  } else {
    next()
  }
})

export default router
