<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/AppLayout.vue'
import { delegationApi } from '@/services/api'
import type {
  DelegationResponse,
  CheckAccessResponse,
  BlockchainHistoryEntry,
} from '@/types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// Form State
const delegationId = ref('')
const requesterDelegateeId = ref('')
const resourceId = ref('Logistik')
const permission = ref('READ_WRITE')

// Available Delegations for Quick Selection
const availableDelegations = ref<DelegationResponse[]>([])
const isLoadingDelegations = ref(false)

// Evaluation State
const isEvaluating = ref(false)
const evaluationResult = ref<{
  status: string
  isGranted: boolean
  raw: CheckAccessResponse | null
  timestamp: string
} | null>(null)
const evaluationError = ref<string | null>(null)

// Simulation Action State (Emergency Dispensing)
const isSimulatingAction = ref(false)
const simulatedActionResult = ref<{
  success: boolean
  title: string
  message: string
  timestamp: string
} | null>(null)

// Modal Ledger State
const showHistoryModal = ref(false)
const blockchainHistory = ref<BlockchainHistoryEntry[]>([])
const isLoadingHistory = ref(false)

// Copy helper
const copied = ref(false)
const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text)
  copied.value = true
  setTimeout(() => {
    copied.value = false
  }, 2000)
}

// Fetch available delegations
const loadDelegations = async () => {
  isLoadingDelegations.value = true
  try {
    const res = await delegationApi.getMyDelegations()
    if (res.success && res.data) {
      availableDelegations.value = res.data
    }
  } catch (err: any) {
    console.error('Failed to load delegations:', err)
  } finally {
    isLoadingDelegations.value = false
  }
}

// Handle query params on mount
onMounted(async () => {
  await loadDelegations()

  if (route.query.delegationId) {
    delegationId.value = String(route.query.delegationId)
  }
  if (route.query.delegateeId) {
    requesterDelegateeId.value = String(route.query.delegateeId)
  } else if (authStore.user?.userId) {
    requesterDelegateeId.value = authStore.user.userId
  }
  if (route.query.resourceId) {
    resourceId.value = String(route.query.resourceId)
  }
  if (route.query.permission) {
    permission.value = String(route.query.permission)
  }

  if (route.query.delegationId && route.query.delegateeId) {
    await handleEvaluate()
  }
})

// Quick Fill from Selected Delegation
const applySelectedDelegation = (del: DelegationResponse) => {
  delegationId.value = del.delegationId
  requesterDelegateeId.value = del.delegateeId
  resourceId.value = del.targetModule
  permission.value = del.permission
  evaluationResult.value = null
  evaluationError.value = null
  simulatedActionResult.value = null
}

// Quick Scenario Presets for Thesis Defence
const applyScenario = (type: 'valid' | 'wrong_user' | 'wrong_resource' | 'wrong_perm') => {
  evaluationResult.value = null
  evaluationError.value = null
  simulatedActionResult.value = null

  const activeDel = availableDelegations.value.find((d) => d.status === 'ACTIVE') || availableDelegations.value[0]

  if (activeDel) {
    delegationId.value = activeDel.delegationId
    resourceId.value = activeDel.targetModule
    permission.value = activeDel.permission

    switch (type) {
      case 'valid':
        requesterDelegateeId.value = activeDel.delegateeId
        break
      case 'wrong_user':
        requesterDelegateeId.value = 'USR-STRANGER-999'
        break
      case 'wrong_resource':
        requesterDelegateeId.value = activeDel.delegateeId
        resourceId.value = 'KEUANGAN_DIREKSI'
        break
      case 'wrong_perm':
        requesterDelegateeId.value = activeDel.delegateeId
        permission.value = 'SUPER_ADMIN'
        break
    }
  } else {
    delegationId.value = 'DEL-' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '-0001'
    switch (type) {
      case 'valid':
        requesterDelegateeId.value = 'USR-DWI-002'
        resourceId.value = 'Logistik'
        permission.value = 'READ_WRITE'
        break
      case 'wrong_user':
        requesterDelegateeId.value = 'USR-UNAUTHORIZED-403'
        resourceId.value = 'Logistik'
        permission.value = 'READ_WRITE'
        break
      case 'wrong_resource':
        requesterDelegateeId.value = 'USR-DWI-002'
        resourceId.value = 'ICU_OPERASIONAL'
        permission.value = 'READ_WRITE'
        break
      case 'wrong_perm':
        requesterDelegateeId.value = 'USR-DWI-002'
        resourceId.value = 'Logistik'
        permission.value = 'ADMIN_MASTER'
        break
    }
  }
}

// Evaluate Access against Hyperledger Fabric Smart Contract
const handleEvaluate = async () => {
  if (!delegationId.value.trim() || !requesterDelegateeId.value.trim()) {
    evaluationError.value = 'Delegation ID dan Requester Delegatee ID wajib diisi.'
    return
  }

  isEvaluating.value = true
  evaluationError.value = null
  evaluationResult.value = null
  simulatedActionResult.value = null

  try {
    const res = await delegationApi.checkAccess({
      delegationId: delegationId.value.trim(),
      requesterDelegateeId: requesterDelegateeId.value.trim(),
      resourceId: resourceId.value.trim(),
      permission: permission.value.trim(),
    })

    if (res.success && res.data) {
      const raw = res.data
      const statusText = raw.status || ''
      const isGrantedBool = raw.isGranted === 'true' || statusText.toUpperCase() === 'GRANTED'

      evaluationResult.value = {
        status: statusText,
        isGranted: isGrantedBool,
        raw: raw,
        timestamp: new Date().toLocaleTimeString('id-ID', { hour12: false }),
      }
    } else {
      evaluationError.value = res.message || 'Evaluasi smart contract gagal mengembalikan status.'
    }
  } catch (err: any) {
    evaluationError.value =
      err.response?.data?.message ||
      err.message ||
      'Terjadi kesalahan saat mengevaluasi Smart Contract Hyperledger Fabric.'
  } finally {
    isEvaluating.value = false
  }
}

// Live Enforcement Simulation: Dispense Emergency Medicine
const simulateDispenseMedicine = async () => {
  if (!delegationId.value.trim() || !requesterDelegateeId.value.trim()) {
    evaluationError.value = 'Silakan isi parameter pengujian terlebih dahulu.'
    return
  }

  isSimulatingAction.value = true
  simulatedActionResult.value = null

  try {
    const res = await delegationApi.checkAccess({
      delegationId: delegationId.value.trim(),
      requesterDelegateeId: requesterDelegateeId.value.trim(),
      resourceId: resourceId.value.trim(),
      permission: permission.value.trim(),
    })

    const raw = res.data
    const statusText = raw?.status || ''
    const isGrantedBool = raw?.isGranted === 'true' || statusText.toUpperCase() === 'GRANTED'

    evaluationResult.value = {
      status: statusText,
      isGranted: isGrantedBool,
      raw: raw,
      timestamp: new Date().toLocaleTimeString('id-ID', { hour12: false }),
    }

    if (isGrantedBool) {
      simulatedActionResult.value = {
        success: true,
        title: 'Tindakan Berhasil Dieksekusi (200 OK)',
        message: `Pengeluaran Obat Darurat (Amoksisilin 500mg) berhasil diverifikasi dan disetujui. Tindakan dicatat di database SIMRS atas wewenang delegasi ${delegationId.value}.`,
        timestamp: new Date().toLocaleString('id-ID'),
      }
    } else {
      simulatedActionResult.value = {
        success: false,
        title: 'Akses Ditolak oleh Smart Contract (403 Forbidden)',
        message: `Sistem SIMRS memblokir tindakan pengeluaran obat darurat. Alasan smart contract: "${statusText}". Transaksi digagalkan demi keselamatan dan kepatuhan medis.`,
        timestamp: new Date().toLocaleString('id-ID'),
      }
    }
  } catch (err: any) {
    simulatedActionResult.value = {
      success: false,
      title: 'Validasi Smart Contract Gagal',
      message: err.response?.data?.message || err.message || 'Smart Contract error',
      timestamp: new Date().toLocaleString('id-ID'),
    }
  } finally {
    isSimulatingAction.value = false
  }
}

// View Blockchain Ledger Modal
const openBlockchainLedger = async () => {
  if (!delegationId.value.trim()) return
  showHistoryModal.value = true
  isLoadingHistory.value = true
  blockchainHistory.value = []

  try {
    const res = await delegationApi.getBlockchainHistory(delegationId.value.trim())
    if (res.success && res.data) {
      blockchainHistory.value = res.data
    }
  } catch (err: any) {
    console.error('Failed to load blockchain history:', err)
  } finally {
    isLoadingHistory.value = false
  }
}
</script>

<template>
  <AppLayout>
    <div class="space-y-6">
      <!-- Title & Context Banner -->
      <div class="bg-slate-900 rounded-xl p-6 text-white shadow-xs">
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div class="inline-block px-2.5 py-0.5 rounded text-[11px] font-semibold bg-slate-800 text-teal-300 mb-2 border border-slate-700">
              Simulator Pengujian Sidang Proposal
            </div>
            <h2 class="text-xl font-bold tracking-tight">
              Simulator Evaluasi Smart Contract (checkAccess)
            </h2>
            <p class="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
              Menguji validasi otoritas pendelegasian wewenang secara real-time langsung terhadap chaincode Hyperledger Fabric (<code class="bg-slate-800 text-teal-300 px-1 py-0.5 rounded font-mono">dac 1.0</code> di channel <code class="bg-slate-800 text-teal-300 px-1 py-0.5 rounded font-mono">z-dac</code>).
            </p>
          </div>

          <button
            @click="router.push('/delegations')"
            class="self-start md:self-center px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 font-semibold text-xs rounded-lg transition-all shadow-xs shrink-0"
          >
            Kelola Pendelegasian
          </button>
        </div>
      </div>

      <!-- Quick Preset Scenarios Bar -->
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-xs">
        <div class="mb-3">
          <h3 class="text-xs font-bold text-slate-700 uppercase tracking-wider">
            Skenario Uji Cepat Sidang (Berdasarkan Tabel 3.3 Proposal Skripsi)
          </h3>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
          <button
            @click="applyScenario('valid')"
            type="button"
            class="p-3 rounded-lg border border-emerald-300 bg-emerald-50/60 hover:bg-emerald-100/70 text-left transition-all"
          >
            <div class="text-xs font-bold text-emerald-900">
              1. Akses Sah (Valid)
            </div>
            <p class="text-[11px] text-emerald-800 mt-1">
              Penerima wewenang cocok, modul sesuai, wewenang aktif. Harapan: <strong>GRANTED</strong>.
            </p>
          </button>

          <button
            @click="applyScenario('wrong_user')"
            type="button"
            class="p-3 rounded-lg border border-rose-300 bg-rose-50/60 hover:bg-rose-100/70 text-left transition-all"
          >
            <div class="text-xs font-bold text-rose-900">
              2. Wrong Delegatee
            </div>
            <p class="text-[11px] text-rose-800 mt-1">
              Pemohon tidak berhak memakai delegasi orang lain. Harapan: <strong>DENIED: mismatch</strong>.
            </p>
          </button>

          <button
            @click="applyScenario('wrong_resource')"
            type="button"
            class="p-3 rounded-lg border border-amber-300 bg-amber-50/60 hover:bg-amber-100/70 text-left transition-all"
          >
            <div class="text-xs font-bold text-amber-900">
              3. Wrong Resource
            </div>
            <p class="text-[11px] text-amber-800 mt-1">
              Mencoba akses modul di luar wewenang (Least Privilege). Harapan: <strong>DENIED: mismatch</strong>.
            </p>
          </button>

          <button
            @click="applyScenario('wrong_perm')"
            type="button"
            class="p-3 rounded-lg border border-purple-300 bg-purple-50/60 hover:bg-purple-100/70 text-left transition-all"
          >
            <div class="text-xs font-bold text-purple-900">
              4. Permission Escalation
            </div>
            <p class="text-[11px] text-purple-800 mt-1">
              Minta hak SUPER_ADMIN melampaui delegasi yang diberikan. Harapan: <strong>DENIED: mismatch</strong>.
            </p>
          </button>
        </div>
      </div>

      <!-- Main Testing Grid: Form & Output -->
      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <!-- LEFT: Test Parameters Form (5 Cols) -->
        <div class="lg:col-span-5 bg-white rounded-xl border border-slate-200 shadow-xs p-5 space-y-4">
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 class="text-xs font-bold text-slate-800 uppercase tracking-wider">
              Parameter Evaluasi Smart Contract
            </h3>
            <button
              @click="loadDelegations"
              :disabled="isLoadingDelegations"
              class="text-xs text-teal-700 hover:text-teal-900 font-semibold"
            >
              {{ isLoadingDelegations ? 'Memuat...' : 'Segarkan' }}
            </button>
          </div>

          <!-- Quick Select from Active Delegations -->
          <div v-if="availableDelegations.length > 0" class="space-y-1.5">
            <label class="block text-xs font-semibold text-slate-700">
              Pilih Cepat dari Delegasi yang Ada:
            </label>
            <div class="max-h-36 overflow-y-auto space-y-1.5 pr-1 border border-slate-200 rounded-lg p-2 bg-slate-50">
              <div
                v-for="del in availableDelegations"
                :key="del.delegationId"
                @click="applySelectedDelegation(del)"
                class="p-2 rounded bg-white border cursor-pointer hover:border-teal-600 transition-all text-xs"
                :class="delegationId === del.delegationId ? 'border-teal-700 ring-1 ring-teal-700' : 'border-slate-200'"
              >
                <div class="flex items-center justify-between font-mono font-bold text-[11px] text-slate-800">
                  <span>{{ del.delegationId }}</span>
                  <span
                    :class="[
                      'px-1.5 py-0.2 rounded text-[9px] font-bold',
                      del.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' :
                      del.status === 'REVOKED' ? 'bg-rose-100 text-rose-800' : 'bg-slate-200 text-slate-700'
                    ]"
                  >
                    {{ del.status }}
                  </span>
                </div>
                <div class="text-[11px] text-slate-600 mt-1 flex items-center justify-between">
                  <span>Penerima: <strong>{{ del.delegateeName || del.delegateeId }}</strong></span>
                  <span class="text-teal-800 font-semibold">{{ del.targetModule }} ({{ del.permission }})</span>
                </div>
              </div>
            </div>
          </div>

          <form @submit.prevent="handleEvaluate" class="space-y-3.5">
            <!-- Delegation ID -->
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">
                Delegation ID <span class="text-rose-600">*</span>
              </label>
              <div class="relative">
                <input
                  v-model="delegationId"
                  type="text"
                  required
                  placeholder="e.g. DEL-20261021-0001"
                  class="w-full px-3 py-2 text-xs font-mono rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700 bg-white"
                />
                <button
                  v-if="delegationId"
                  type="button"
                  @click="copyToClipboard(delegationId)"
                  class="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-slate-400 hover:text-slate-700"
                >
                  {{ copied ? 'Tersalin' : 'Salin' }}
                </button>
              </div>
            </div>

            <!-- Requester Delegatee ID -->
            <div>
              <div class="flex items-center justify-between mb-1">
                <label class="block text-xs font-semibold text-slate-700">
                  Requester User ID (Pemohon Akses) <span class="text-rose-600">*</span>
                </label>
                <span class="text-[10px] text-slate-500">ID staf yang mencoba akses</span>
              </div>
              <input
                v-model="requesterDelegateeId"
                type="text"
                required
                placeholder="e.g. USR-DWI-002"
                class="w-full px-3 py-2 text-xs font-mono rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700 bg-white"
              />
              <div class="flex gap-2 mt-1.5">
                <button
                  type="button"
                  @click="requesterDelegateeId = 'USR-DWI-002'"
                  class="text-[10px] px-2 py-0.5 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium"
                >
                  Set ns_dewi
                </button>
                <button
                  type="button"
                  @click="requesterDelegateeId = 'USR-AGS-001'"
                  class="text-[10px] px-2 py-0.5 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium"
                >
                  Set dr_agus
                </button>
                <button
                  type="button"
                  @click="requesterDelegateeId = 'USR-STRANGER-999'"
                  class="text-[10px] px-2 py-0.5 rounded bg-rose-50 hover:bg-rose-100 text-rose-800 font-medium"
                >
                  Set Unknown User
                </button>
              </div>
            </div>

            <!-- Resource ID & Permission Row -->
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">
                  Target Resource / Modul
                </label>
                <input
                  v-model="resourceId"
                  type="text"
                  required
                  placeholder="e.g. Logistik"
                  class="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700 bg-white"
                />
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">
                  Permission
                </label>
                <select
                  v-model="permission"
                  class="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700 bg-white"
                >
                  <option value="READ_WRITE">READ_WRITE</option>
                  <option value="READ_ONLY">READ_ONLY</option>
                  <option value="EXECUTE">EXECUTE</option>
                  <option value="SUPER_ADMIN">SUPER_ADMIN</option>
                </select>
              </div>
            </div>

            <!-- Action Button -->
            <div class="pt-2">
              <button
                type="submit"
                :disabled="isEvaluating"
                class="w-full py-2.5 px-4 bg-teal-800 hover:bg-teal-900 active:bg-teal-950 disabled:bg-teal-400 text-white font-semibold text-xs rounded-lg shadow-xs transition-all flex items-center justify-center"
              >
                <span>{{ isEvaluating ? 'Mengevaluasi ke Fabric Peer...' : 'Evaluasi Akses ke Smart Contract' }}</span>
              </button>
            </div>
          </form>

          <div v-if="evaluationError" class="p-3 bg-rose-50 border border-rose-200 rounded-lg text-rose-800 text-xs">
            <div class="font-bold">Evaluasi Gagal:</div>
            <p class="mt-1 text-[11px] leading-relaxed">{{ evaluationError }}</p>
          </div>
        </div>

        <!-- RIGHT: Evaluation Result Display (7 Cols) -->
        <div class="lg:col-span-7 space-y-4">
          <!-- Main Result Card -->
          <div class="bg-white rounded-xl border border-slate-200 shadow-xs p-5 overflow-hidden">
            <div class="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
              <h3 class="text-xs font-bold text-slate-800 uppercase tracking-wider">
                Hasil Evaluasi Kontrak (Smart Contract Output)
              </h3>
              <span v-if="evaluationResult" class="text-[11px] text-slate-400 font-mono">
                Pukul {{ evaluationResult.timestamp }}
              </span>
            </div>

            <!-- Placeholder State when not evaluated yet -->
            <div
              v-if="!evaluationResult && !isEvaluating"
              class="py-10 px-4 text-center border border-dashed border-slate-300 rounded-lg"
            >
              <h4 class="text-xs font-bold text-slate-700">Belum Ada Pengujian yang Dijalankan</h4>
              <p class="text-[11px] text-slate-500 max-w-md mx-auto mt-1 leading-relaxed">
                Pilih salah satu <strong>Skenario Uji Cepat</strong> di atas atau tentukan parameter pada formulir di sebelah kiri, kemudian klik tombol <strong>Evaluasi Akses</strong>.
              </p>
            </div>

            <!-- Loading State -->
            <div v-if="isEvaluating" class="py-10 text-center space-y-2">
              <p class="text-xs font-semibold text-slate-700">
                Menghubungi Hyperledger Fabric Peer via Gateway...
              </p>
              <p class="text-[11px] text-slate-400 font-mono">
                evaluateTransaction('checkAccess', '{{ delegationId }}', ...)
              </p>
            </div>

            <!-- Result Content -->
            <div v-if="evaluationResult && !isEvaluating" class="space-y-4">
              <!-- STATUS BANNER: GRANTED -->
              <div
                v-if="evaluationResult.isGranted"
                class="p-5 rounded-lg border-2 border-emerald-600 bg-emerald-50 text-emerald-950"
              >
                <div class="text-[11px] font-bold uppercase tracking-wider text-emerald-800">
                  Status Keputusan Smart Contract
                </div>
                <h2 class="text-2xl font-bold tracking-tight text-emerald-800 mt-0.5">
                  ACCESS GRANTED
                </h2>
                <p class="text-xs text-emerald-900 mt-1.5 leading-relaxed">
                  Wewenang terverifikasi sah di Hyperledger Fabric. Status pendelegasian <strong>ACTIVE</strong>, identitas pemohon sesuai, dan waktu saat ini berada di dalam jendela pendelegasian.
                </p>
              </div>

              <!-- STATUS BANNER: DENIED EXPIRED -->
              <div
                v-else-if="evaluationResult.status.toLowerCase().includes('expired')"
                class="p-5 rounded-lg border-2 border-amber-600 bg-amber-50 text-amber-950"
              >
                <div class="text-[11px] font-bold uppercase tracking-wider text-amber-800">
                  Status Keputusan Smart Contract
                </div>
                <h2 class="text-2xl font-bold tracking-tight text-amber-800 mt-0.5">
                  ACCESS DENIED: EXPIRED
                </h2>
                <p class="text-xs text-amber-900 mt-1.5 leading-relaxed">
                  Wewenang darurat telah <strong>kedaluwarsa (Expired)</strong>. Smart contract secara deterministik menolak permintaan karena batas waktu <code class="bg-amber-200/60 px-1 py-0.5 rounded font-mono">validUntil</code> telah terlewati tanpa perlu intervensi manual.
                </p>
              </div>

              <!-- STATUS BANNER: DENIED REVOKED -->
              <div
                v-else-if="evaluationResult.status.toUpperCase().includes('REVOKED')"
                class="p-5 rounded-lg border-2 border-rose-600 bg-rose-50 text-rose-950"
              >
                <div class="text-[11px] font-bold uppercase tracking-wider text-rose-800">
                  Status Keputusan Smart Contract
                </div>
                <h2 class="text-2xl font-bold tracking-tight text-rose-800 mt-0.5">
                  ACCESS DENIED: REVOKED
                </h2>
                <p class="text-xs text-rose-900 mt-1.5 leading-relaxed">
                  Wewenang telah <strong>dicabut (Revoked)</strong> oleh delegator sebelum batas waktu berakhir. Status pembatalan tercatat permanen di blockchain Hyperledger Fabric.
                </p>
              </div>

              <!-- STATUS BANNER: DENIED MISMATCH OR OTHER -->
              <div
                v-else
                class="p-5 rounded-lg border-2 border-rose-600 bg-rose-50 text-rose-950"
              >
                <div class="text-[11px] font-bold uppercase tracking-wider text-rose-800">
                  Status Keputusan Smart Contract
                </div>
                <h2 class="text-2xl font-bold tracking-tight text-rose-800 mt-0.5">
                  ACCESS DENIED
                </h2>
                <div class="text-xs font-mono font-bold text-rose-900 mt-1">
                  {{ evaluationResult.status }}
                </div>
                <p class="text-xs text-rose-900/90 mt-1 leading-relaxed">
                  Akses ditolak karena ketidaksesuaian identitas penerima wewenang (Delegatee Mismatch), modul tidak terdaftar, atau batasan Least Privilege dilanggar.
                </p>
              </div>

              <!-- Evaluation Parameters Table -->
              <div class="border border-slate-200 rounded-lg overflow-hidden text-xs">
                <div class="bg-slate-50 px-4 py-2 font-bold text-slate-700 border-b border-slate-200 flex items-center justify-between">
                  <span>Rincian Pengujian Kontrak</span>
                  <button
                    @click="openBlockchainLedger"
                    class="text-[11px] text-teal-800 hover:text-teal-900 font-semibold"
                  >
                    Periksa Blok Ledger Fabric &rarr;
                  </button>
                </div>
                <div class="divide-y divide-slate-100">
                  <div class="px-4 py-2.5 flex items-center justify-between">
                    <span class="text-slate-500">Delegation ID:</span>
                    <span class="font-mono font-bold text-slate-800">{{ delegationId }}</span>
                  </div>
                  <div class="px-4 py-2.5 flex items-center justify-between">
                    <span class="text-slate-500">Requester ID:</span>
                    <span class="font-mono font-semibold text-slate-800">{{ requesterDelegateeId }}</span>
                  </div>
                  <div class="px-4 py-2.5 flex items-center justify-between">
                    <span class="text-slate-500">Target Modul & Permission:</span>
                    <span class="font-medium text-slate-800">{{ resourceId }} ({{ permission }})</span>
                  </div>
                  <div class="px-4 py-2.5 flex items-center justify-between">
                    <span class="text-slate-500">Smart Contract Engine:</span>
                    <span class="font-mono text-teal-800 font-semibold">Hyperledger Fabric 2.5 (dac:1.0)</span>
                  </div>
                  <div class="px-4 py-2.5 flex items-center justify-between">
                    <span class="text-slate-500">Status Raw String:</span>
                    <span class="font-mono text-slate-800 font-bold">{{ evaluationResult.status }}</span>
                  </div>
                </div>
              </div>

              <!-- Raw API Response -->
              <details class="text-xs bg-slate-50 rounded-lg border border-slate-200 p-3">
                <summary class="font-bold text-slate-700 cursor-pointer hover:text-teal-800">
                  Lihat Raw JSON Response (Bukti Verifikasi Teknis)
                </summary>
                <pre class="mt-2 p-2 bg-slate-900 text-emerald-400 rounded font-mono text-[11px] overflow-x-auto">{{ JSON.stringify(evaluationResult.raw, null, 2) }}</pre>
              </details>
            </div>
          </div>

          <!-- LIVE SIMRS ACTION ENFORCEMENT SIMULATION -->
          <div class="bg-white rounded-xl border border-slate-200 shadow-xs p-5 space-y-3">
            <div class="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 class="text-xs font-bold text-slate-800 uppercase tracking-wider">
                Simulasi Real-World SIMRS: Pengeluaran Obat Darurat
              </h3>
              <span class="text-[10px] px-2 py-0.5 rounded bg-teal-50 text-teal-800 font-semibold border border-teal-200">
                Aksi Terproteksi DAC
              </span>
            </div>

            <p class="text-xs text-slate-600 leading-relaxed">
              Mensimulasikan tindakan nyata di instalasi farmasi/logistik rawat inap di mana perawat melakukan input dispensing obat darurat menggunakan wewenang dokter yang didelegasikan.
            </p>

            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-slate-50 rounded-lg p-3 border border-slate-200">
              <div class="space-y-0.5">
                <div class="text-xs font-bold text-slate-800">Dispensing Amoksisilin 500mg IV</div>
                <div class="text-[11px] text-slate-500">Pasien Darurat Rawat Inap (Bed 302B) &bull; Modul: Logistik</div>
              </div>
              <button
                @click="simulateDispenseMedicine"
                :disabled="isSimulatingAction || !delegationId"
                class="px-4 py-2 bg-teal-800 hover:bg-teal-900 disabled:bg-slate-300 text-white font-semibold text-xs rounded-lg shadow-xs transition-all shrink-0"
              >
                <span>{{ isSimulatingAction ? 'Memvalidasi...' : 'Eksekusi Pengeluaran' }}</span>
              </button>
            </div>

            <!-- Simulation Action Result Notice -->
            <div
              v-if="simulatedActionResult"
              :class="[
                'p-3.5 rounded-lg border text-xs space-y-1.5 transition-all',
                simulatedActionResult.success
                  ? 'bg-emerald-50 border-emerald-300 text-emerald-900'
                  : 'bg-rose-50 border-rose-300 text-rose-900'
              ]"
            >
              <div class="font-bold">
                {{ simulatedActionResult.title }}
              </div>
              <p class="text-[11px] leading-relaxed">{{ simulatedActionResult.message }}</p>
              <div class="text-[10px] text-slate-500 font-mono">
                Waktu eksekusi: {{ simulatedActionResult.timestamp }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- MODAL: Bukti Riwayat Blok Ledger Hyperledger Fabric -->
    <div
      v-if="showHistoryModal"
      class="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4"
    >
      <div class="bg-white rounded-xl max-w-2xl w-full max-h-[85vh] flex flex-col shadow-2xl border border-slate-200 overflow-hidden">
        <!-- Header -->
        <div class="px-5 py-4 bg-slate-900 text-white flex items-center justify-between shrink-0">
          <div>
            <h3 class="font-bold text-sm">Bukti Riwayat Transaksi Blockchain (Ledger)</h3>
            <p class="text-[11px] text-slate-400 font-mono">{{ delegationId }}</p>
          </div>
          <button
            @click="showHistoryModal = false"
            class="text-slate-400 hover:text-white font-bold px-2"
          >
            &times;
          </button>
        </div>

        <!-- Content -->
        <div class="p-5 overflow-y-auto space-y-3">
          <div v-if="isLoadingHistory" class="py-10 text-center text-slate-500 text-xs">
            Mengambil riwayat transaksi langsung dari state database Fabric...
          </div>

          <div v-else-if="blockchainHistory.length === 0" class="py-10 text-center text-slate-400 text-xs">
            Tidak ada riwayat blok tercatat untuk delegasi ini.
          </div>

          <div v-else class="space-y-3">
            <div
              v-for="(h, idx) in blockchainHistory"
              :key="idx"
              class="p-3.5 rounded-lg border border-slate-200 bg-slate-50 space-y-2 text-xs"
            >
              <div class="flex items-center justify-between">
                <span class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-indigo-100 text-indigo-800">
                  Blok Mutasi #{{ idx + 1 }}
                </span>
                <span class="text-[11px] font-mono text-slate-500">{{ h.timestamp }}</span>
              </div>

              <div>
                <span class="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Transaction ID (Fabric):</span>
                <div class="font-mono text-[11px] text-indigo-700 font-bold break-all bg-white p-1.5 rounded border border-slate-200 mt-0.5">
                  {{ h.txId }}
                </div>
              </div>

              <div class="pt-1">
                <span class="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">State Value:</span>
                <pre class="mt-1 p-2 bg-slate-900 text-emerald-400 rounded text-[10px] font-mono overflow-x-auto">{{ h.value }}</pre>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="px-5 py-3 bg-slate-50 border-t border-slate-200 flex justify-end shrink-0">
          <button
            @click="showHistoryModal = false"
            class="px-4 py-1.5 bg-slate-200 hover:bg-slate-300 text-slate-800 font-semibold text-xs rounded transition-colors"
          >
            Tutup
          </button>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
