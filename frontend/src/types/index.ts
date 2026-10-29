export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  userId: string
  username: string
  fullName: string
  roleId: number
  roleName: string
  unitCabang: string
}

export interface UserProfileResponse {
  userId: string
  username: string
  fullName: string
  email?: string
  roleId: number
  roleName: string
  unitCabang: string
  grantedPermIds: number[]
}

export interface RolePrivilegeResponse {
  permId: number
  moduleName: string
  subPrivilegeName: string
}

export interface StaffUser {
  userId: string
  username: string
  fullName: string
  roleId: number
  unitCabang: string
  isActive: boolean
  role?: {
    roleId: number
    roleName: string
  }
}

export interface CreateDelegationRequest {
  delegateeId: string
  targetModule: string
  permId: number
  permission: string
  validFrom: string
  validUntil: string
  justification: string
  parentDelegationId?: string
  maxDepth?: number
}

export interface DelegationResponse {
  delegationId: string
  delegatorId: string
  delegatorName: string
  delegateeId: string
  delegateeName: string
  targetModule: string
  permId: number | null
  permission: string
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED' | string
  txId: string
  justification: string
  validFrom: string
  validUntil: string
  createdAt: string
  depth: number
  maxDepth: number
  parentDelegationId?: string
}

export interface CheckAccessRequest {
  delegationId: string
  requesterDelegateeId: string
  resourceId: string
  permission: string
}

export interface CheckAccessResponse {
  status: string
  isGranted: string
}

export interface BlockchainHistoryEntry {
  txId: string
  timestamp: string
  isDelete: string
  value: string
}

export interface NetworkStatusResponse {
  connected: boolean
  status: 'CONNECTED' | 'DISCONNECTED' | 'DEGRADED' | string
  stateName: string
  channel: string
  chaincode: string
  peerEndpoint: string
  timestamp: string
  details: string
}
