import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, roleApi } from '@/services/api'
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
  const isLoading = ref<boolean>(false)
  const errorMessage = ref<string | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const isDoctor = computed(() => {
    return user.value?.roleName?.toLowerCase().includes('dokter') || user.value?.roleId === 101
  })
  const isNurse = computed(() => {
    return user.value?.roleName?.toLowerCase().includes('perawat') || user.value?.roleId === 201
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
      }
    } catch (err) {
      console.error('Failed to fetch user profile:', err)
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
    localStorage.removeItem('dac_token')
    localStorage.removeItem('dac_user')
  }

  return {
    token,
    user,
    privileges,
    isLoading,
    errorMessage,
    isAuthenticated,
    isDoctor,
    isNurse,
    login,
    fetchProfile,
    fetchPrivileges,
    logout,
  }
})
