import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, roleApi, delegationApi } from '@/services/api'
import type { UserProfileResponse, RolePrivilegeResponse, LoginResponse } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const getStoredUser = (): UserProfileResponse | LoginResponse | null => {
    const saved = localStorage.getItem('dac_user')
    try {
      return saved ? JSON.parse(saved) : null
    } catch {
      return null
    }
  }

  const token = ref<string | null>(localStorage.getItem('dac_token'))
  const user = ref<UserProfileResponse | LoginResponse | null>(getStoredUser())
  const privileges = ref<RolePrivilegeResponse[]>([])
  const hasActiveDelegation = ref<boolean>(localStorage.getItem('dac_has_active_del') === 'true')
  const isLoading = ref<boolean>(false)
  const errorMessage = ref<string | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const isDoctor = computed(() => {
    return user.value?.roleName?.toLowerCase().includes('dokter') || user.value?.roleId === 101
  })
  const isNurse = computed(() => {
    return user.value?.roleName?.toLowerCase().includes('perawat') || user.value?.roleId === 21
  })
  const isAdmin = computed(() => {
    return user.value?.roleName?.toLowerCase().includes('admin') || user.value?.roleId === 1
  })

  async function login(username: string, password: string) {
    isLoading.value = true
    errorMessage.value = null
    try {
      const res = await authApi.login(username, password)
      if (res.success && res.data) {
        token.value = res.data.token
        user.value = res.data
        localStorage.setItem('dac_token', res.data.token)
        localStorage.setItem('dac_user', JSON.stringify(res.data))
        await fetchProfile()
        return true
      }
      return false
    } catch (err: any) {
      errorMessage.value = err.response?.data?.message || 'Login failed. Please check your credentials.'
      return false
    } finally {
      isLoading.value = false
    }
  }

  async function fetchProfile() {
    if (!token.value) return
    try {
      const res = await authApi.getProfile()
      if (res.success && res.data) {
        user.value = res.data
        localStorage.setItem('dac_user', JSON.stringify(res.data))
        if (res.data.roleId) {
          await fetchPrivileges(res.data.roleId)
        }
        await checkActiveDelegations()
      }
    } catch (err) {
      console.error('Failed to fetch user profile:', err)
    }
  }

  async function checkActiveDelegations(): Promise<boolean> {
    if (!token.value || !user.value?.userId) {
      hasActiveDelegation.value = false
      localStorage.setItem('dac_has_active_del', 'false')
      return false
    }

    try {
      const [recRes, allRes] = await Promise.all([
        delegationApi.getDelegationsByDelegatee(user.value.userId),
        delegationApi.getMyDelegations(),
      ])

      const allActive = allRes.success && allRes.data ? allRes.data : []
      const received = recRes.success && recRes.data ? recRes.data : []

      const now = Date.now()
      const isTrulyActive = (del: any): boolean => {
        if (del.status !== 'ACTIVE') return false
        const until = new Date(del.validUntil).getTime()
        if (isNaN(until) || now > until) return false
        if (del.parentDelegationId && del.parentDelegationId.trim() !== '') {
          const parent = allActive.find((d: any) => d.delegationId === del.parentDelegationId)
          if (!parent) return false
          return isTrulyActive(parent)
        }
        return true
      }

      const activeList = received.filter(isTrulyActive)
      const hasActive = activeList.length > 0
      hasActiveDelegation.value = hasActive
      localStorage.setItem('dac_has_active_del', hasActive ? 'true' : 'false')
      return hasActive
    } catch (err) {
      console.warn('Failed to check active delegations in auth store:', err)
      return hasActiveDelegation.value
    }
  }

  async function fetchPrivileges(roleId: number) {
    try {
      const res = await roleApi.getPrivilegesByRoleId(roleId)
      if (res.success && res.data) {
        privileges.value = res.data
      }
    } catch (err) {
      console.error('Failed to fetch role privileges:', err)
    }
  }

  function logout() {
    token.value = null
    user.value = null
    privileges.value = []
    hasActiveDelegation.value = false
    localStorage.removeItem('dac_token')
    localStorage.removeItem('dac_user')
    localStorage.removeItem('dac_has_active_del')
  }

  return {
    token,
    user,
    privileges,
    hasActiveDelegation,
    isLoading,
    errorMessage,
    isAuthenticated,
    isDoctor,
    isNurse,
    isAdmin,
    login,
    fetchProfile,
    fetchPrivileges,
    checkActiveDelegations,
    logout,
  }
})
