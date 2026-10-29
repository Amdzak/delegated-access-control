<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/AppLayout.vue'
import { delegationApi, roleApi } from '@/services/api'
import type { DelegationResponse, StaffUser } from '@/types'

const authStore = useAuthStore()
const router = useRouter()

// Realtime dynamic clock ticker (ticks every second)
const currentTime = ref(new Date())
let timerInterval: ReturnType<typeof setInterval> | null = null

// Delegation Stats State
const myDelegations = ref<DelegationResponse[]>([])
const isLoadingDelegations = ref(false)

// Sub-Delegation Modal State
const showSubDelegationModal = ref(false)
const selectedParentDelegation = ref<DelegationResponse | null>(null)
const staffList = ref<StaffUser[]>([])
const isLoadingStaff = ref(false)
const selectedSubDelegateeId = ref('')
const subDelegationDuration = ref<'1' | '2' | '4' | 'match_parent'>('match_parent')
const subDelegationJustification = ref('Pelimpahan tugas darurat penanganan pasien rawat inap')
const isSubmittingSubDelegation = ref(false)
const subDelegationSuccess = ref<string | null>(null)
const subDelegationError = ref<string | null>(null)

// Revoke Sub-Delegation State
const showRevokeModal = ref(false)
const revokeTargetId = ref<string | null>(null)
const revokeReason = ref('Tugas selesai dilaksanakan')
const isRevokingSub = ref(false)

// Helper: Memeriksa apakah suatu delegasi benar-benar aktif (termasuk cascading check status induk)
const isDelegationTrulyActive = (d: DelegationResponse): boolean => {
  if (d.status !== 'ACTIVE') return false
  const now = currentTime.value
  if (d.validUntil) {
    const until = new Date(d.validUntil.replace(' ', 'T'))
    if (!isNaN(until.getTime()) && now > until) return false
  }

  // Cascading check: Jika ini sub-delegasi, delegasi induk tidak boleh REVOKED
  if (d.parentDelegationId) {
    const parent = myDelegations.value.find((p) => p.delegationId === d.parentDelegationId)
    if (parent && (parent.status === 'REVOKED' || !isDelegationTrulyActive(parent))) {
      return false
    }
  }

  return true
}

// 1. Delegasi AKTIF yang DITERIMA pengguna saat ini (Staff Penerima Wewenang / Delegatee)
const activeReceivedDelegations = computed(() => {
  const currentUserId = authStore.user?.userId
  return myDelegations.value.filter((d) => d.delegateeId === currentUserId && isDelegationTrulyActive(d))
})

// 2. Delegasi AKTIF yang DIBERIKAN oleh Dokter pengguna saat ini (Delegator)
const activeGrantedDelegations = computed(() => {
  if (!authStore.isDoctor) return []
  const currentUserId = authStore.user?.userId
  return myDelegations.value.filter((d) => d.delegatorId === currentUserId && isDelegationTrulyActive(d))
})

// 3. Sub-Delegasi AKTIF yang DIBERIKAN oleh Staf Non-Dokter pengguna saat ini
const activeGrantedByMe = computed(() => {
  if (authStore.isDoctor) return []
  const currentUserId = authStore.user?.userId
  return myDelegations.value.filter((d) => d.delegatorId === currentUserId && isDelegationTrulyActive(d))
})

// Staf medis yang berhak menerima sub-delegasi (bukan dirinya sendiri, bukan delegator induk, bukan dokter)
const availableSubDelegatees = computed(() => {
  const currentUserId = authStore.user?.userId
  const parentDelegatorId = selectedParentDelegation.value?.delegatorId
  return staffList.value.filter((u) => {
    if (u.userId === currentUserId) return false
    if (u.userId === parentDelegatorId) return false
    const isDoc = u.role?.roleName?.toLowerCase().includes('dokter') || u.roleId === 101
    return !isDoc
  })
})

// Helper: Format Date to YYYY-MM-DD HH:mm:ss
const formatToCustomDate = (d: Date): string => {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// Dynamic Live Countdown Function (Updated every 1 second)
const getCountdown = (validUntilStr?: string) => {
  if (!validUntilStr) return ''
  const until = new Date(validUntilStr.replace(' ', 'T'))
  const diffMs = until.getTime() - currentTime.value.getTime()
  if (diffMs <= 0) return 'Telah kedaluwarsa'

  const totalSeconds = Math.floor(diffMs / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  const pad = (n: number) => String(n).padStart(2, '0')

  if (hours > 0) {
    return `${hours} jam ${pad(minutes)} menit ${pad(seconds)} detik lagi`
  }
  if (minutes > 0) {
    return `${minutes} menit ${pad(seconds)} detik lagi`
  }
  return `${seconds} detik lagi`
}

const activeDelegationsCount = computed(() => {
  return myDelegations.value.filter((d) => isDelegationTrulyActive(d)).length
})

const totalDelegationsCount = computed(() => {
  return myDelegations.value.length
})

const openSubDelegationModal = async (del: DelegationResponse) => {
  selectedParentDelegation.value = del
  subDelegationError.value = null
  subDelegationSuccess.value = null
  showSubDelegationModal.value = true

  if (staffList.value.length === 0) {
    isLoadingStaff.value = true
    try {
      const res = await roleApi.getStaffUsers()
      if (res.success && res.data) {
        staffList.value = res.data
      }
    } catch (err) {
      console.warn('Failed to load staff list for sub-delegation:', err)
    } finally {
      isLoadingStaff.value = false
    }
  }

  const eligible = availableSubDelegatees.value
  if (eligible.length > 0 && eligible[0]) {
    selectedSubDelegateeId.value = eligible[0].userId
  } else {
    selectedSubDelegateeId.value = ''
  }
}

const handleSubDelegationSubmit = async () => {
  if (!selectedParentDelegation.value) return
  if (!selectedSubDelegateeId.value) {
    subDelegationError.value = 'Silakan pilih staf penerima pendelegasian'
    return
  }

  isSubmittingSubDelegation.value = true
  subDelegationError.value = null
  subDelegationSuccess.value = null

  try {
    const parent = selectedParentDelegation.value
    const now = new Date()
    const validFrom = formatToCustomDate(now)

    let untilDate: Date
    const parentUntil = new Date(parent.validUntil.replace(' ', 'T'))

    if (subDelegationDuration.value === 'match_parent') {
      untilDate = parentUntil
    } else {
      const hours = parseInt(subDelegationDuration.value, 10)
      untilDate = new Date(now.getTime() + hours * 3600 * 1000)
      if (untilDate > parentUntil) {
        untilDate = parentUntil
      }
    }

    const validUntil = formatToCustomDate(untilDate)

    const res = await delegationApi.createDelegation({
      delegateeId: selectedSubDelegateeId.value,
      targetModule: parent.targetModule,
      permId: parent.permId || 2899,
      permission: parent.permission || 'READ_WRITE',
      validFrom,
      validUntil,
      justification: subDelegationJustification.value,
      parentDelegationId: parent.delegationId,
      maxDepth: parent.maxDepth || 2,
    })

    if (res.success) {
      subDelegationSuccess.value = `Sub-delegasi ${res.data.delegationId} (Level ${res.data.depth}) berhasil dikomit ke Hyperledger Fabric!`
      const refresh = await delegationApi.getMyDelegations()
      if (refresh.success && refresh.data) {
        myDelegations.value = refresh.data
      }
      setTimeout(() => {
        showSubDelegationModal.value = false
        subDelegationSuccess.value = null
      }, 1500)
    } else {
      subDelegationError.value = res.message || 'Gagal memproses sub-delegasi'
    }
  } catch (err: any) {
    subDelegationError.value = err.response?.data?.message || err.message || 'Gagal mengirim transaksi sub-delegasi'
  } finally {
    isSubmittingSubDelegation.value = false
  }
}

const openRevokeModal = (delegationId: string) => {
  revokeTargetId.value = delegationId
  revokeReason.value = 'Tugas klinis selesai dilaksanakan'
  showRevokeModal.value = true
}

const handleRevokeSubDelegation = async () => {
  if (!revokeTargetId.value) return
  isRevokingSub.value = true
  try {
    const res = await delegationApi.revokeDelegation(revokeTargetId.value, revokeReason.value)
    if (res.success) {
      const refresh = await delegationApi.getMyDelegations()
      if (refresh.success && refresh.data) {
        myDelegations.value = refresh.data
      }
      showRevokeModal.value = false
      await authStore.checkActiveDelegations()
    }
  } catch (err: any) {
    alert(err.response?.data?.message || 'Gagal mencabut sub-delegasi')
  } finally {
    isRevokingSub.value = false
  }
}

onMounted(async () => {
  // Start 1-second dynamic countdown ticker
  timerInterval = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)

  await authStore.fetchProfile()
  isLoadingDelegations.value = true
  try {
    const res = await delegationApi.getMyDelegations()
    if (res.success && res.data) {
      myDelegations.value = res.data
    }
    await authStore.checkActiveDelegations()
  } catch (err) {
    console.warn('Failed to fetch delegation stats for profile:', err)
  } finally {
    isLoadingDelegations.value = false
  }
})

onUnmounted(() => {
  if (timerInterval) {
    clearInterval(timerInterval)
  }
})
</script>

<template>
  <AppLayout>
    <div class="space-y-6">
      <!-- 1. Active Received Delegations Banner (Khusus Staff Penerima Wewenang yang Memiliki Delegasi Aktif) -->
      <div
        v-if="activeReceivedDelegations.length > 0"
        class="bg-emerald-50/90 border border-emerald-300 rounded-xl p-4 sm:p-5 shadow-xs space-y-4"
      >
        <div
          v-for="del in activeReceivedDelegations"
          :key="del.delegationId"
          class="space-y-3"
        >
          <!-- Banner Header -->
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-emerald-200/80 pb-2.5">
            <div class="flex items-center space-x-2.5">
              <span class="flex h-3 w-3 relative">
                <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span class="relative inline-flex rounded-full h-3 w-3 bg-emerald-600"></span>
              </span>
              <h3 class="text-xs sm:text-sm font-extrabold text-emerald-950 tracking-wide uppercase">
                SEDANG ADA STATUS DELEGASI YANG AKTIF
              </h3>
            </div>
            <div class="flex items-center space-x-2">
              <span class="font-mono text-[11px] font-semibold text-emerald-800 bg-white px-2.5 py-0.5 rounded border border-emerald-200 shadow-2xs">
                {{ del.delegationId }}
              </span>
              <span class="px-2 py-0.5 text-[10px] font-bold bg-emerald-700 text-white rounded">
                AKTIF
              </span>
            </div>
          </div>

          <!-- Attributes Grid (Hak Akses, Modul, Aktif Hingga) -->
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 bg-white p-3.5 rounded-lg border border-emerald-200 text-xs shadow-2xs">
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                HAK AKSES :
              </span>
              <span class="font-mono font-bold text-slate-900 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                {{ del.permission || 'READ_WRITE' }}
              </span>
            </div>
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                MODULE :
              </span>
              <span class="font-bold text-teal-800 bg-teal-50 px-2 py-0.5 rounded border border-teal-200">
                {{ del.targetModule }}
              </span>
            </div>
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                AKTIF HINGGA :
              </span>
              <div class="flex items-baseline space-x-1.5 flex-wrap">
                <span class="font-mono font-bold text-emerald-800">
                  {{ del.validUntil }}
                </span>
                <span
                  v-if="getCountdown(del.validUntil)"
                  class="font-mono text-[11px] font-semibold text-emerald-700 bg-emerald-100/80 px-1.5 py-0.5 rounded border border-emerald-200/60"
                >
                  ({{ getCountdown(del.validUntil) }})
                </span>
              </div>
            </div>
          </div>

          <!-- Bottom Footer Details & Sub-Delegation Action -->
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1">
            <div class="text-[11px] text-emerald-900">
              <span class="text-emerald-700">Wewenang klinis dilimpahkan oleh:</span>
              <strong class="font-bold text-slate-900 ml-1">{{ del.delegatorName || del.delegatorId }}</strong>
              <span v-if="del.parentDelegationId" class="ml-2 font-mono text-[10px] text-teal-800 bg-teal-100/60 px-1.5 py-0.5 rounded border border-teal-200">
                Sub-delegasi dari: {{ del.parentDelegationId }}
              </span>
            </div>

            <!-- Action Buttons: Open SIMRS Module & Sub-Delegation -->
            <div class="flex items-center flex-wrap gap-2">
              <router-link
                :to="{ path: '/simrs', query: { module: del.targetModule } }"
                class="inline-flex items-center px-3 py-1.5 text-xs font-bold text-teal-800 bg-white hover:bg-teal-50 rounded-lg border border-teal-300 shadow-2xs transition-colors cursor-pointer"
              >
                <svg class="w-3.5 h-3.5 mr-1.5 text-teal-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/>
                </svg>
                Buka Pengujian Delegasi: {{ del.targetModule }}
              </router-link>

              <button
                v-if="(del.depth ?? 0) < (del.maxDepth ?? 2) - 1"
                @click="openSubDelegationModal(del)"
                class="inline-flex items-center px-3 py-1.5 text-xs font-bold text-white bg-emerald-700 hover:bg-emerald-800 rounded-lg shadow-xs transition-colors cursor-pointer"
              >
                <svg class="w-3.5 h-3.5 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
                </svg>
                Limpahkan Tugas Lanjutan (Sub-Delegasi)
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. Active Granted Delegations Banner (Khusus Dokter yang Melimpahkan Wewenang) -->
      <div
        v-else-if="authStore.isDoctor && activeGrantedDelegations.length > 0"
        class="bg-teal-50/80 border border-teal-300 rounded-xl p-4 sm:p-5 shadow-xs space-y-4"
      >
        <div
          v-for="del in activeGrantedDelegations"
          :key="del.delegationId"
          class="space-y-3"
        >
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-teal-200/80 pb-2.5">
            <div class="flex items-center space-x-2.5">
              <span class="flex h-3 w-3 relative">
                <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-teal-400 opacity-75"></span>
                <span class="relative inline-flex rounded-full h-3 w-3 bg-teal-600"></span>
              </span>
              <h3 class="text-xs sm:text-sm font-extrabold text-teal-950 tracking-wide uppercase">
                STATUS PENDELEGASIAN WEWENANG SEDANG AKTIF
              </h3>
            </div>
            <div class="flex items-center space-x-2">
              <span class="font-mono text-[11px] font-semibold text-teal-800 bg-white px-2.5 py-0.5 rounded border border-teal-200 shadow-2xs">
                {{ del.delegationId }}
              </span>
              <button
                @click="router.push('/delegations')"
                class="px-2.5 py-0.5 text-[10px] font-bold bg-teal-700 hover:bg-teal-800 text-white rounded cursor-pointer transition-colors shadow-2xs"
              >
                Kelola &rarr;
              </button>
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 bg-white p-3.5 rounded-lg border border-teal-200 text-xs shadow-2xs">
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                PENERIMA KUASA :
              </span>
              <span class="font-bold text-slate-900 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                {{ del.delegateeName || del.delegateeId }}
              </span>
            </div>
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                MODULE :
              </span>
              <span class="font-bold text-teal-800 bg-teal-50 px-2 py-0.5 rounded border border-teal-200">
                {{ del.targetModule }}
              </span>
            </div>
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                AKTIF HINGGA :
              </span>
              <div class="flex items-baseline space-x-1.5 flex-wrap">
                <span class="font-mono font-bold text-teal-900">
                  {{ del.validUntil }}
                </span>
                <span
                  v-if="getCountdown(del.validUntil)"
                  class="font-mono text-[11px] font-semibold text-teal-700 bg-teal-100/80 px-1.5 py-0.5 rounded border border-teal-200/60"
                >
                  ({{ getCountdown(del.validUntil) }})
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 2b. Active Sub-Delegations Granted by Current Non-Doctor User -->
      <div
        v-if="!authStore.isDoctor && activeGrantedByMe.length > 0"
        class="bg-teal-50/80 border border-teal-300 rounded-xl p-4 sm:p-5 shadow-xs space-y-4"
      >
        <div
          v-for="del in activeGrantedByMe"
          :key="del.delegationId"
          class="space-y-3"
        >
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-teal-200/80 pb-2.5">
            <div class="flex items-center space-x-2.5">
              <span class="flex h-3 w-3 relative">
                <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-teal-400 opacity-75"></span>
                <span class="relative inline-flex rounded-full h-3 w-3 bg-teal-600"></span>
              </span>
              <h3 class="text-xs sm:text-sm font-extrabold text-teal-950 tracking-wide uppercase">
                SUB-DELEGASI YANG ANDA LIMPAHKAN SEDANG AKTIF
              </h3>
            </div>
            <div class="flex items-center space-x-2">
              <span class="font-mono text-[11px] font-semibold text-teal-800 bg-white px-2.5 py-0.5 rounded border border-teal-200 shadow-2xs">
                {{ del.delegationId }}
              </span>
              <button
                @click="openRevokeModal(del.delegationId)"
                class="px-2.5 py-1 text-[10px] font-bold bg-rose-700 hover:bg-rose-800 text-white rounded cursor-pointer transition-colors shadow-2xs"
              >
                Cabut Wewenang
              </button>
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 bg-white p-3.5 rounded-lg border border-teal-200 text-xs shadow-2xs">
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                PENERIMA KUASA :
              </span>
              <span class="font-bold text-slate-900 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                {{ del.delegateeName || del.delegateeId }}
              </span>
            </div>
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                MODULE :
              </span>
              <span class="font-bold text-teal-800 bg-teal-50 px-2 py-0.5 rounded border border-teal-200">
                {{ del.targetModule }}
              </span>
            </div>
            <div>
              <span class="text-slate-500 font-semibold block text-[11px] uppercase tracking-wider mb-1">
                AKTIF HINGGA :
              </span>
              <div class="flex items-baseline space-x-1.5 flex-wrap">
                <span class="font-mono font-bold text-teal-900">
                  {{ del.validUntil }}
                </span>
                <span
                  v-if="getCountdown(del.validUntil)"
                  class="font-mono text-[11px] font-semibold text-teal-700 bg-teal-100/80 px-1.5 py-0.5 rounded border border-teal-200/60"
                >
                  ({{ getCountdown(del.validUntil) }})
                </span>
              </div>
            </div>
          </div>

          <div class="text-[11px] text-teal-900 pt-0.5">
            <span class="text-teal-700">Berasal dari delegasi induk:</span>
            <strong class="font-mono font-semibold text-slate-900 ml-1">{{ del.parentDelegationId || '-' }}</strong>
          </div>
        </div>
      </div>

      <!-- User Profile Header -->
      <div class="bg-white rounded-xl border border-slate-200 p-6 shadow-xs flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div class="flex items-center space-x-4">
          <div class="w-14 h-14 rounded-full bg-teal-700 text-white flex items-center justify-center font-bold text-xl shadow-xs">
            {{ (authStore.user?.fullName || authStore.user?.username || 'U').charAt(0).toUpperCase() }}
          </div>
          <div>
            <h2 class="text-lg font-bold text-slate-800">
              {{ authStore.user?.fullName || authStore.user?.username }}
            </h2>
            <div class="flex items-center space-x-2 mt-1">
              <span class="text-xs font-mono text-slate-500">ID: {{ authStore.user?.userId }}</span>
              <span class="text-slate-300">&bull;</span>
              <span class="text-xs font-semibold text-teal-800">{{ authStore.user?.roleName }}</span>
              <span class="text-slate-300">&bull;</span>
              <span class="text-xs text-slate-500">{{ authStore.user?.unitCabang }}</span>
            </div>
          </div>
        </div>

        <button
          @click="authStore.fetchProfile"
          class="px-3 py-1.5 text-xs font-medium text-slate-600 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-lg transition-colors cursor-pointer"
        >
          Refresh Data
        </button>
      </div>

      <!-- User Information Cards -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <!-- Card 1: Kredensial Akun -->
        <div class="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-1.5 text-xs sm:text-sm">
          <div>
            <span class="text-slate-500 font-medium">Username:</span>
            <span class="font-semibold text-slate-800 ml-1.5">{{ authStore.user?.username }}</span>
          </div>
          <div>
            <span class="text-slate-500 font-medium">User ID:</span>
            <span class="font-mono font-semibold text-slate-800 ml-1.5">{{ authStore.user?.userId }}</span>
          </div>
          <div>
            <span class="text-slate-500 font-medium">Status Akun:</span>
            <span class="font-semibold text-emerald-700 ml-1.5">Aktif</span>
          </div>
        </div>

        <!-- Card 2: Jabatan & Penugasan -->
        <div class="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-1.5 text-xs sm:text-sm">
          <div>
            <span class="text-slate-500 font-medium">Role:</span>
            <span class="font-bold text-teal-800 ml-1.5">{{ authStore.user?.roleName }}</span>
          </div>
          <div>
            <span class="text-slate-500 font-medium">Unit Kerja:</span>
            <span class="font-semibold text-slate-800 ml-1.5">{{ authStore.user?.unitCabang }}</span>
          </div>
          <div>
            <span class="text-slate-500 font-medium">Hak Delegasi:</span>
            <span class="font-semibold text-slate-800 ml-1.5">{{ authStore.isDoctor ? 'Delegator Sah' : 'Penerima Kuasa' }}</span>
          </div>
        </div>

        <!-- Card 3: Ringkasan Delegasi -->
        <div class="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-1.5 text-xs sm:text-sm">
          <div>
            <span class="text-slate-500 font-medium">Delegasi Aktif:</span>
            <span class="font-bold text-emerald-700 ml-1.5">
              {{ isLoadingDelegations ? 'Memuat...' : `${activeDelegationsCount} Wewenang Berjalan` }}
            </span>
          </div>
          <div>
            <span class="text-slate-500 font-medium">Riwayat Delegasi:</span>
            <span class="font-semibold text-slate-800 ml-1.5">
              {{ isLoadingDelegations ? 'Memuat...' : `${totalDelegationsCount} Kali Dilakukan` }}
            </span>
          </div>
        </div>
      </div>

      <!-- Original Privileges Table (The Baseline) -->
      <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
          <div>
            <h3 class="text-sm font-bold text-slate-800">Daftar Hak Akses (Role-Based Access Control)</h3>
            <p class="text-xs text-slate-500 mt-0.5">
              previleges pada database untuk pengguna <span class="font-semibold text-slate-700">{{ authStore.user?.fullName || authStore.user?.username }}</span>.
            </p>
          </div>
          <span class="inline-flex items-center px-2.5 py-0.5 rounded text-[11px] font-semibold bg-teal-50 text-teal-800 border border-teal-200 self-start">
            Total {{ authStore.privileges.length }} previleges
          </span>
        </div>

        <!-- Table -->
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs text-slate-600">
            <thead class="bg-slate-50 text-slate-700 uppercase tracking-wider text-[11px] font-semibold border-y border-slate-200">
              <tr>
                <th class="py-2.5 px-4 w-24">ID Izin</th>
                <th class="py-2.5 px-4 w-44">Nama Modul</th>
                <th class="py-2.5 px-4">Deskripsi Wewenang / Sub-Privilege</th>
                <th class="py-2.5 px-4 w-32 text-center">Status Akses</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr
                v-for="priv in authStore.privileges"
                :key="priv.permId"
                class="hover:bg-slate-50/70 transition-colors"
              >
                <td class="py-3 px-4 font-mono font-bold text-teal-800">
                  #{{ priv.permId }}
                </td>
                <td class="py-3 px-4 font-semibold text-slate-800">
                  <span class="inline-block px-2 py-0.5 rounded bg-slate-100 border border-slate-200 text-slate-700 text-xs">
                    {{ priv.moduleName }}
                  </span>
                </td>
                <td class="py-3 px-4 text-slate-700">
                  {{ priv.subPrivilegeName }}
                </td>
                <td class="py-3 px-4 text-center">
                  <span class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    GRANTED
                  </span>
                </td>
              </tr>
              <tr v-if="authStore.privileges.length === 0">
                <td colspan="4" class="py-8 text-center text-slate-400">
                  Memuat data hak akses pengguna...
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- MODAL 1: Limpahkan Tugas (Sub-Delegasi Lanjutan) -->
    <div
      v-if="showSubDelegationModal && selectedParentDelegation"
      class="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4"
    >
      <div class="bg-white rounded-xl max-w-lg w-full p-5 shadow-2xl border border-slate-200 space-y-4">
        <!-- Modal Header -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h4 class="font-extrabold text-sm text-slate-900 tracking-wide">
              Limpahkan Tugas (Sub-Delegasi Lanjutan)
            </h4>
            <p class="text-xs text-slate-500">
              Mendelegasikan wewenang klinis ke staf perawat jaga berikutnya
            </p>
          </div>
          <button
            @click="showSubDelegationModal = false"
            class="text-slate-400 hover:text-slate-600 font-bold px-2 py-1 cursor-pointer"
          >
            &times;
          </button>
        </div>

        <!-- Alert messages -->
        <div
          v-if="subDelegationError"
          class="p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg"
        >
          {{ subDelegationError }}
        </div>
        <div
          v-if="subDelegationSuccess"
          class="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-semibold rounded-lg"
        >
          {{ subDelegationSuccess }}
        </div>

        <!-- Sub-Delegation Form -->
        <form @submit.prevent="handleSubDelegationSubmit" class="space-y-3.5 text-xs">
          <!-- Parent Context Card (Read-only) -->
          <div class="bg-slate-50 p-3 rounded-lg border border-slate-200 space-y-2">
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-slate-500">Delegasi Induk:</span>
              <span class="font-mono font-bold text-slate-800">{{ selectedParentDelegation.delegationId }}</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-slate-500">Modul Terdelegasi:</span>
              <span class="font-bold text-teal-800">{{ selectedParentDelegation.targetModule }}</span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-slate-500">Tingkat Baru:</span>
              <span class="font-bold text-emerald-700">
                Level {{ (selectedParentDelegation.depth ?? 0) + 1 }} (Maks. {{ selectedParentDelegation.maxDepth ?? 2 }})
              </span>
            </div>
            <div class="flex items-center justify-between text-[11px]">
              <span class="text-slate-500">Batas Waktu Maksimal Induk:</span>
              <span class="font-mono font-bold text-slate-900">{{ selectedParentDelegation.validUntil }}</span>
            </div>
          </div>

          <!-- Staf Penerima (Delegatee) -->
          <div>
            <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Penerima Kuasa Lanjutan (Sub-Delegatee)
            </label>
            <select
              v-model="selectedSubDelegateeId"
              class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              required
            >
              <option v-if="isLoadingStaff" disabled value="">
                Memuat daftar staf perawat...
              </option>
              <option v-for="user in availableSubDelegatees" :key="user.userId" :value="user.userId">
                {{ user.fullName }} ({{ user.role?.roleName || 'Staf Medis' }} - {{ user.userId }})
              </option>
              <option v-if="!isLoadingStaff && availableSubDelegatees.length === 0" disabled value="">
                Tidak ada staf perawat yang tersedia
              </option>
            </select>
          </div>

          <!-- Durasi Masa Berlaku -->
          <div>
            <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Durasi Masa Berlaku Sub-Delegasi
            </label>
            <select
              v-model="subDelegationDuration"
              class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
            >
              <option value="match_parent">
                Sama dengan Sisa Waktu Delegasi Induk (Hingga {{ selectedParentDelegation.validUntil }})
              </option>
              <option value="1">1 Jam ke Depan</option>
              <option value="2">2 Jam ke Depan</option>
              <option value="4">4 Jam ke Depan</option>
            </select>
          </div>

          <!-- Justifikasi / Alasan Pelimpahan -->
          <div>
            <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Justifikasi / Alasan Pelimpahan Tugas
            </label>
            <input
              v-model="subDelegationJustification"
              type="text"
              class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              placeholder="Contoh: Operan shift jaga perawat rawat inap"
              required
            />
          </div>

          <!-- Modal Actions -->
          <div class="flex items-center justify-end space-x-2 pt-2 border-t border-slate-100">
            <button
              type="button"
              @click="showSubDelegationModal = false"
              class="px-3 py-1.5 border border-slate-300 text-slate-700 hover:bg-slate-100 rounded-lg font-medium cursor-pointer"
            >
              Batal
            </button>
            <button
              type="submit"
              :disabled="isSubmittingSubDelegation || !selectedSubDelegateeId"
              class="px-4 py-1.5 bg-emerald-700 hover:bg-emerald-800 disabled:opacity-50 text-white rounded-lg font-bold shadow-xs cursor-pointer inline-flex items-center"
            >
              <span v-if="isSubmittingSubDelegation" class="inline-block animate-spin mr-1.5">&#9696;</span>
              {{ isSubmittingSubDelegation ? 'Memproses ke Ledger...' : 'Konfirmasi & Kirim ke Fabric' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- MODAL 2: Cabut Sub-Delegasi -->
    <div
      v-if="showRevokeModal"
      class="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4"
    >
      <div class="bg-white rounded-xl max-w-md w-full p-5 shadow-2xl border border-slate-200 space-y-4">
        <h4 class="font-extrabold text-sm text-slate-900">
          Konfirmasi Pencabutan Sub-Delegasi
        </h4>
        <p class="text-xs text-slate-600">
          Wewenang untuk sub-delegasi <strong class="font-mono text-slate-800">{{ revokeTargetId }}</strong> akan langsung dicabut di Hyperledger Fabric World State.
        </p>
        <div>
          <label class="block text-xs font-semibold text-slate-700 mb-1">
            Alasan Pencabutan
          </label>
          <input
            v-model="revokeReason"
            type="text"
            class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs"
          />
        </div>
        <div class="flex items-center justify-end space-x-2 pt-2">
          <button
            @click="showRevokeModal = false"
            class="px-3 py-1.5 border border-slate-300 text-slate-700 rounded-lg text-xs cursor-pointer"
          >
            Batal
          </button>
          <button
            @click="handleRevokeSubDelegation"
            :disabled="isRevokingSub"
            class="px-4 py-1.5 bg-rose-700 hover:bg-rose-800 text-white rounded-lg font-bold text-xs cursor-pointer"
          >
            {{ isRevokingSub ? 'Mencabut...' : 'Cabut Wewenang Sekarang' }}
          </button>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
