import axios, { type AxiosInstance } from 'axios'
import type {
  ApiResponse,
  LoginResponse,
  UserProfileResponse,
  RolePrivilegeResponse,
  StaffUser,
  CreateDelegationRequest,
  DelegationResponse,
  CheckAccessRequest,
  CheckAccessResponse,
  BlockchainHistoryEntry,
  NetworkStatusResponse,
} from '@/types'

const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request Interceptor: Attach JWT Token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('dac_token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response Interceptor: Handle 401 Unauthorized
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('dac_token')
      localStorage.removeItem('dac_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  login: async (username: string, password: string):Promise<ApiResponse<LoginResponse>> => {
    const response = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', {
      username,
      password,
    })
    return response.data
  },

  getProfile: async (): Promise<ApiResponse<UserProfileResponse>> => {
    const response = await apiClient.get<ApiResponse<UserProfileResponse>>('/auth/me')
    return response.data
  },
}

export const roleApi = {
  getPrivilegesByRoleId: async (roleId: number): Promise<ApiResponse<RolePrivilegeResponse[]>> => {
    const response = await apiClient.get<ApiResponse<RolePrivilegeResponse[]>>(`/roles/${roleId}/privileges`)
    return response.data
  },

  getStaffUsers: async (): Promise<ApiResponse<StaffUser[]>> => {
    const response = await apiClient.get<ApiResponse<StaffUser[]>>('/roles/users')
    return response.data
  },
}

export const delegationApi = {
  createDelegation: async (payload: CreateDelegationRequest): Promise<ApiResponse<DelegationResponse>> => {
    const response = await apiClient.post<ApiResponse<DelegationResponse>>('/delegations', payload)
    return response.data
  },

  getMyDelegations: async (): Promise<ApiResponse<DelegationResponse[]>> => {
    const response = await apiClient.get<ApiResponse<DelegationResponse[]>>('/delegations/my-delegations')
    return response.data
  },

  getDelegationsByDelegatee: async (delegateeId: string): Promise<ApiResponse<DelegationResponse[]>> => {
    const response = await apiClient.get<ApiResponse<DelegationResponse[]>>(`/delegations/by-delegatee/${delegateeId}`)
    return response.data
  },

  revokeDelegation: async (delegationId: string, reason?: string): Promise<ApiResponse<void>> => {
    const response = await apiClient.delete<ApiResponse<void>>(`/delegations/${delegationId}`, {
      data: { reason: reason || 'Manual revocation by delegator' },
    })
    return response.data
  },

  checkAccess: async (payload: CheckAccessRequest): Promise<ApiResponse<CheckAccessResponse>> => {
    const response = await apiClient.post<ApiResponse<CheckAccessResponse>>('/delegations/check-access', payload)
    return response.data
  },

  getBlockchainHistory: async (delegationId: string): Promise<ApiResponse<BlockchainHistoryEntry[]>> => {
    const response = await apiClient.get<ApiResponse<BlockchainHistoryEntry[]>>(`/delegations/${delegationId}/blockchain-history`)
    return response.data
  },

  getNetworkStatus: async (): Promise<ApiResponse<NetworkStatusResponse>> => {
    const response = await apiClient.get<ApiResponse<NetworkStatusResponse>>('/delegations/network-status')
    return response.data
  },
}

export const testAccessApi = {
  executeMasterDataPasien: async (body?: { patientId?: string; actionNote?: string }): Promise<ApiResponse<any>> => {
    const response = await apiClient.post<ApiResponse<any>>('/test-access/master-data-pasien', body || {})
    return response.data
  },

  executeLogistikPemesanan: async (body?: any): Promise<ApiResponse<any>> => {
    const response = await apiClient.post<ApiResponse<any>>('/test-access/logistik-pemesanan', body || {})
    return response.data
  },

  executeRawatJalan: async (body?: any): Promise<ApiResponse<any>> => {
    const response = await apiClient.post<ApiResponse<any>>('/test-access/transaksi-rawat-jalan', body || {})
    return response.data
  },

  executeBacaRekamMedis: async (body?: any): Promise<ApiResponse<any>> => {
    const response = await apiClient.post<ApiResponse<any>>('/test-access/rekam-medis-baca', body || {})
    return response.data
  },
}

export default apiClient
