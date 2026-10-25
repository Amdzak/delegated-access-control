<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/AppLayout.vue'
import { delegationApi, roleApi } from '@/services/api'
import type {
  DelegationResponse,
  StaffUser,
  RolePrivilegeResponse,
  BlockchainHistoryEntry,
} from '@/types'

const authStore = useAuthStore()
const router = useRouter()

const goToTestAccess = (del: DelegationResponse) => {
  router.push({
    name: 'test-access',
    query: {
      delegationId: del.delegationId,
      delegateeId: del.delegateeId,
      resourceId: del.targetModule,
      permission: del.permission,
    },
  })
}

// State
const delegations = ref<DelegationResponse[]>([])
const staffList = ref<StaffUser[]>([])
const isLoadingList = ref(false)
const isSubmitting = ref(false)
const successMessage = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const lastCreatedDelegation = ref<DelegationResponse | null>(null)

// Blockchain History Modal State
const showHistoryModal = ref(false)
const selectedDelegationId = ref<string | null>(null)
const blockchainHistory = ref<BlockchainHistoryEntry[]>([])
const isLoadingHistory = ref(false)

// Revoke Dialog State
const showRevokeDialog = ref(false)
const targetRevokeId = ref<string | null>(null)
const revokeReason = ref('Emergency duty concluded by delegator')
const isRevoking = ref(false)

// Form State
const selectedDelegateeId = ref('')
const selectedModule = ref('Logistik')
const selectedPermId = ref<number>(2899)
const selectedPermission = ref('READ_WRITE')
const validFrom = ref('')
const validUntil = ref('')
const durationOption = ref<'1' | '2' | '4' | '8' | '12' | '24' | 'custom'>('4')
const customDate = ref('')
const customHour = ref('04')
const customMinute = ref('00')
const justification = ref('Pendelegasian darurat penanganan pasien rawat inap')
const maxDepth = ref(2)

const hoursList = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))
const minutesList = ['00', '05', '10', '15', '20', '25', '30', '35', '40', '45', '50', '55']

// Helper: Format Date to YYYY-MM-DD HH:mm:ss (Format 24 Jam)
const formatToCustomDate = (d: Date): string => {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// Populate custom date and time inputs from validUntil (24h format)
const populateCustomFromValidUntil = () => {
  if (validUntil.value && validUntil.value.includes(' ')) {
    const [d, t] = validUntil.value.split(' ')
    customDate.value = d || ''
    if (t) {
      const parts = t.split(':')
      customHour.value = parts[0] || '00'
      customMinute.value = parts[1] || '00'
    }
  } else {
    const now = new Date()
    const dStr = formatToCustomDate(now)
    const [d, t] = dStr.split(' ')
    customDate.value = d || ''
    if (t) {
      const parts = t.split(':')
      customHour.value = parts[0] || '00'
      customMinute.value = parts[1] || '00'
    }
    validUntil.value = dStr
  }
}

// Sync custom date & time picker to validUntil (24h format)
const syncCustomDateTime = () => {
  if (customDate.value) {
    validUntil.value = `${customDate.value} ${customHour.value}:${customMinute.value}:00`
  }
}

// Quick shortcut: Set validUntil to current time in 24h format
const setValidUntilToNow = () => {
  const now = new Date()
  validUntil.value = formatToCustomDate(now)
  populateCustomFromValidUntil()
}

// Calculate validUntil based on durationOption and a base start date
const calculateValidUntil = (baseDate?: Date) => {
  if (durationOption.value === 'custom') {
    populateCustomFromValidUntil()
    return
  }

  let start = baseDate
  if (!start) {
    const parsed = new Date(validFrom.value.replace(' ', 'T'))
    start = isNaN(parsed.getTime()) ? new Date() : parsed
  }

  const hours = parseInt(durationOption.value, 10)
  const end = new Date(start.getTime() + hours * 60 * 60 * 1000)
  validUntil.value = formatToCustomDate(end)
}

// Set Start Time to Now and recalculate validUntil
const setNowAsValidFrom = () => {
  const now = new Date()
  validFrom.value = formatToCustomDate(now)
  if (durationOption.value !== 'custom') {
    calculateValidUntil(now)
  }
}

// Duration change handler
const handleDurationChange = () => {
  if (durationOption.value === 'custom') {
    populateCustomFromValidUntil()
  } else {
    calculateValidUntil()
  }
}

// Filter staff: Don't show myself
const availableDelegatees = computed(() => {
  return staffList.value.filter((u) => u.userId !== authStore.user?.userId)
})

onMounted(async () => {
  setNowAsValidFrom()
  await loadStaff()
  await loadDelegations()
})

const loadStaff = async () => {
  try {
    const res = await roleApi.getStaffUsers()
    if (res.success && res.data) {
      staffList.value = res.data
      const firstDelegatee = availableDelegatees.value[0]
      if (firstDelegatee) {
        selectedDelegateeId.value = firstDelegatee.userId
      }
    }
  } catch (err) {
    console.error('Failed to load staff list:', err)
  }
}

const loadDelegations = async () => {
  isLoadingList.value = true
  try {
    const res = await delegationApi.getMyDelegations()
    if (res.success && res.data) {
      delegations.value = res.data
    }
  } catch (err) {
    console.error('Failed to load delegations:', err)
  } finally {
    isLoadingList.value = false
  }
}

const handleCreateDelegation = async () => {
  if (!selectedDelegateeId.value || !validFrom.value || !validUntil.value) {
    errorMessage.value = 'Harap lengkapi semua kolom formulir pendelegasian'
    return
  }

  isSubmitting.value = true
  errorMessage.value = null
  successMessage.value = null

  try {
    const res = await delegationApi.createDelegation({
      delegateeId: selectedDelegateeId.value,
      targetModule: selectedModule.value,
      permId: selectedPermId.value,
      permission: selectedPermission.value,
      validFrom: validFrom.value,
      validUntil: validUntil.value,
      justification: justification.value,
      maxDepth: maxDepth.value,
    })

    if (res.success && res.data) {
      lastCreatedDelegation.value = res.data
      successMessage.value = 'Wewenang delegasi berhasil dikomit ke Ledger Hyperledger Fabric!'
      setNowAsValidFrom()
      await loadDelegations()
    }
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || 'Gagal mendelegasikan wewenang.'
  } finally {
    isSubmitting.value = false
  }
}

// Revoke Handler
const openRevokeConfirm = (id: string) => {
  targetRevokeId.value = id
  revokeReason.value = 'Tugas darurat penanganan pasien telah selesai'
  showRevokeDialog.value = true
}

const executeRevoke = async () => {
  if (!targetRevokeId.value) return
  isRevoking.value = true
  try {
    const res = await delegationApi.revokeDelegation(targetRevokeId.value, revokeReason.value)
    if (res.success) {
      showRevokeDialog.value = false
      await loadDelegations()
    }
  } catch (err: any) {
    alert('Gagal mencabut delegasi: ' + (err.response?.data?.message || err.message))
  } finally {
    isRevoking.value = false
  }
}

// Blockchain Modal
const openBlockchainHistory = async (id: string) => {
  selectedDelegationId.value = id
  showHistoryModal.value = true
  isLoadingHistory.value = true
  blockchainHistory.value = []

  try {
    const res = await delegationApi.getBlockchainHistory(id)
    if (res.success && res.data) {
      blockchainHistory.value = res.data
    }
  } catch (err) {
    console.error('Failed to get blockchain history:', err)
  } finally {
    isLoadingHistory.value = false
  }
}

const formatJsonValue = (raw: string): string => {
  if (!raw) return ''
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return JSON.stringify(parsed, null, 2)
  } catch {
    return raw
  }
}

// Dynamic check for status
const getDelegationStatus = (del: DelegationResponse): 'ACTIVE' | 'REVOKED' | 'EXPIRED' => {
  if (del.status === 'REVOKED') return 'REVOKED'
  if (del.validUntil) {
    const until = new Date(del.validUntil.replace(' ', 'T'))
    if (!isNaN(until.getTime()) && new Date() > until) {
      return 'EXPIRED'
    }
  }
  return (del.status as any) || 'ACTIVE'
}
</script>

<template>
  <AppLayout>
    <div class="space-y-6">
      <!-- Section 1: Form Pemberian Delegasi (Khusus Dokter / Delegator) -->
      <div v-if="authStore.isDoctor" class="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
        <div class="border-b border-slate-100 pb-3">
          <h3 class="text-sm font-bold text-slate-800 uppercase tracking-wide">DELEGASIKAN HAK AKSES</h3>
        </div>

        <!-- Success Alert with TxID -->
        <div
          v-if="successMessage && lastCreatedDelegation"
          class="p-4 bg-emerald-50 border border-emerald-200 rounded-lg space-y-2 text-xs text-emerald-900"
        >
          <div class="font-bold text-emerald-800">
            {{ successMessage }}
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-[11px] font-mono bg-white/80 p-2.5 rounded border border-emerald-100">
            <div>Delegation ID: <span class="font-bold text-slate-800">{{ lastCreatedDelegation.delegationId }}</span></div>
            <div>Status: <span class="font-bold text-emerald-700">{{ lastCreatedDelegation.status }}</span></div>
            <div class="md:col-span-2 truncate">
              Fabric TxID: <span class="font-bold text-indigo-700">{{ lastCreatedDelegation.txId }}</span>
            </div>
          </div>
        </div>

        <!-- Error Alert -->
        <div
          v-if="errorMessage"
          class="p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg"
        >
          {{ errorMessage }}
        </div>

        <!-- Form Elements Grid -->
        <form @submit.prevent="handleCreateDelegation" class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <!-- Delegatee Selection -->
            <div>
              <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                PENERIMA HAK AKSES (Delegatee)
              </label>
              <select
                v-model="selectedDelegateeId"
                class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              >
                <option v-for="user in availableDelegatees" :key="user.userId" :value="user.userId">
                  {{ user.fullName }} ({{ user.role?.roleName || 'Staf Medis' }} - {{ user.userId }})
                </option>
              </select>
            </div>

            <!-- Target Module Selection -->
            <div>
              <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                MODUL TARGET (Resource)
              </label>
              <select
                v-model="selectedModule"
                class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              >
                <option value="Logistik">Logistik & Obat Darurat (Perm ID: 2899)</option>
                <option value="Rekam Medis">Rekam Medis & E-Resep (Perm ID: 2901)</option>
                <option value="Laboratorium">Laboratorium & Diagnostik (Perm ID: 2905)</option>
              </select>
            </div>

            <!-- Permission Scope -->
            <div>
              <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                HAK AKSES (Permission Scope)
              </label>
              <select
                v-model="selectedPermission"
                class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              >
                <option value="READ_WRITE">READ_WRITE (Membaca & Memvalidasi Tindakan)</option>
                <option value="READ_ONLY">READ_ONLY (Melihat Catatan Klinis)</option>
                <option value="EXECUTE">EXECUTE (Menyetujui Obat Terbatas)</option>
              </select>
            </div>

            <!-- Chain Delegation Depth -->
            <div>
              <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                BATAS PENDELEGASIAN (Apakah hak akses ini bisa didelegasikan ulang)
              </label>
              <select
                v-model="maxDepth"
                class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              >
                <option :value="1">1 Tingkat (Tidak dapat didelegasikan ulang)</option>
                <option :value="2">2 Tingkat (Boleh didelegasikan ke perawat jaga berikutnya)</option>
              </select>
            </div>

            <!-- Waktu Mulai -->
            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  WAKTU MULAI (validFrom)
                </label>
                <button
                  type="button"
                  @click="setNowAsValidFrom"
                  class="text-[11px] text-teal-700 hover:text-teal-900 font-medium hover:underline cursor-pointer"
                >
                  Set Waktu Sekarang
                </button>
              </div>
              <input
                v-model="validFrom"
                type="text"
                readonly
                class="w-full px-3 py-2 bg-slate-100 border border-slate-300 rounded-lg font-mono text-xs text-slate-700 cursor-default focus:outline-none"
              />
              <p class="text-[11px] text-slate-400 mt-1">
                Otomatis terisi waktu saat ini
              </p>
            </div>

            <!-- Waktu Berakhir / Durasi -->
            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  WAKTU BERAKHIR (validUntil)
                </label>
                <button
                  v-if="durationOption === 'custom'"
                  type="button"
                  @click="setValidUntilToNow"
                  class="text-[11px] text-teal-700 hover:text-teal-900 font-medium hover:underline cursor-pointer"
                >
                  Set Waktu Sekarang
                </button>
              </div>

              <select
                v-model="durationOption"
                @change="handleDurationChange"
                class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              >
                <option value="1">1 Jam</option>
                <option value="2">2 Jam</option>
                <option value="4">4 Jam</option>
                <option value="8">8 Jam</option>
                <option value="12">12 Jam</option>
                <option value="24">24 Jam</option>
                <option value="custom">Kustom (Tentukan Tanggal & Jam Sendiri)</option>
              </select>

              <!-- Pilihan Kustom Format 24 Jam Murni -->
              <div v-if="durationOption === 'custom'" class="mt-2 space-y-2">
                <div class="grid grid-cols-3 gap-2">
                  <!-- Tanggal -->
                  <div>
                    <label class="block text-[10px] font-semibold text-slate-600 mb-1">Tanggal</label>
                    <input
                      type="date"
                      v-model="customDate"
                      @change="syncCustomDateTime"
                      class="w-full px-2.5 py-1.5 bg-slate-50 border border-slate-300 rounded-lg text-xs font-mono focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
                    />
                  </div>

                  <!-- Jam (00 - 23) -->
                  <div>
                    <label class="block text-[10px] font-semibold text-slate-600 mb-1">Jam (00 - 23)</label>
                    <select
                      v-model="customHour"
                      @change="syncCustomDateTime"
                      class="w-full px-2.5 py-1.5 bg-slate-50 border border-slate-300 rounded-lg text-xs font-mono focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
                    >
                      <option v-for="h in hoursList" :key="h" :value="h">
                        {{ h }}:00
                      </option>
                    </select>
                  </div>

                  <!-- Menit (00 - 55) -->
                  <div>
                    <label class="block text-[10px] font-semibold text-slate-600 mb-1">Menit</label>
                    <select
                      v-model="customMinute"
                      @change="syncCustomDateTime"
                      class="w-full px-2.5 py-1.5 bg-slate-50 border border-slate-300 rounded-lg text-xs font-mono focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
                    >
                      <option v-for="m in minutesList" :key="m" :value="m">
                        :{{ m }}
                      </option>
                    </select>
                  </div>
                </div>

                <p class="text-[11px] text-slate-600 font-mono">
                  Berakhir: <span class="font-bold text-slate-800">{{ validUntil }}</span>
                </p>
              </div>

              <!-- Preview jika durasi preset aktif -->
              <p v-else class="text-[11px] text-slate-500 mt-1 font-mono">
                Berakhir: <span class="font-bold text-slate-700">{{ validUntil }}</span>
              </p>
            </div>

            <!-- Justification Note -->
            <div class="md:col-span-2">
              <label class="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                ALASAN MELAKUKAN DELEGASI
              </label>
              <input
                v-model="justification"
                type="text"
                placeholder="Misal: Penanganan pasien darurat bangsal rawat inap saat dokter sedang visite ICU"
                class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-teal-700/20 focus:border-teal-700"
              />
            </div>
          </div>

          <div class="flex justify-end pt-2">
            <button
              type="submit"
              :disabled="isSubmitting"
              class="px-5 py-2 bg-teal-800 hover:bg-teal-900 active:bg-teal-950 text-white font-semibold text-xs rounded-lg shadow-xs transition-all disabled:opacity-50"
            >
              {{ isSubmitting ? 'Memproses...' : 'Submit' }}
            </button>
          </div>
        </form>
      </div>

      <!-- Section 2: Tabel Monitoring Delegasi -->
      <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-3">
          <div>
            <h3 class="text-sm font-bold text-slate-800">
              DAFTAR DELEGASI YANG SAYA BERIKAN
            </h3>
          </div>

          <button
            @click="loadDelegations"
            class="px-3 py-1.5 text-xs text-teal-800 bg-teal-50 hover:bg-teal-100 border border-teal-200 rounded font-semibold transition-all self-start cursor-pointer"
          >
            Segarkan Data
          </button>
        </div>

        <!-- Table with Horizontal Scroll -->
        <div class="overflow-x-auto">
          <table class="w-full min-w-[1000px] text-left text-xs text-slate-600">
            <thead class="bg-slate-50 text-slate-700 uppercase tracking-wider text-[11px] font-semibold border-y border-slate-200">
              <tr>
                <th class="py-2.5 px-4 whitespace-nowrap">Delegation ID</th>
                <th class="py-2.5 px-4 whitespace-nowrap">Pemberi</th>
                <th class="py-2.5 px-4 whitespace-nowrap">Penerima</th>
                <th class="py-2.5 px-4 whitespace-nowrap">Modul</th>
                <th class="py-2.5 px-4 whitespace-nowrap">Izin</th>
                <th class="py-2.5 px-4 whitespace-nowrap">Waktu Mulai</th>
                <th class="py-2.5 px-4 whitespace-nowrap">Waktu Berakhir</th>
                <th class="py-2.5 px-4 text-center whitespace-nowrap">Status</th>
                <th class="py-2.5 px-4 text-center whitespace-nowrap">Aksi</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr
                v-for="del in delegations"
                :key="del.delegationId"
                class="hover:bg-slate-50/70 transition-colors"
              >
                <!-- ID & Tx -->
                <td class="py-3 px-4 font-mono whitespace-nowrap">
                  <div class="font-bold text-slate-800">{{ del.delegationId }}</div>
                  <div v-if="del.txId" class="text-[10px] text-slate-400 truncate max-w-[140px]">Tx: {{ del.txId }}</div>
                </td>

                <!-- Pemberi -->
                <td class="py-3 px-4 whitespace-nowrap">
                  <div class="font-medium text-slate-800">{{ del.delegatorName || del.delegatorId }}</div>
                </td>

                <!-- Penerima -->
                <td class="py-3 px-4 whitespace-nowrap">
                  <div class="font-medium text-slate-800">{{ del.delegateeName || del.delegateeId }}</div>
                </td>

                <!-- Modul -->
                <td class="py-3 px-4 whitespace-nowrap">
                  <span class="inline-block px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-medium text-xs">
                    {{ del.targetModule }}
                  </span>
                </td>

                <!-- Izin -->
                <td class="py-3 px-4 font-mono whitespace-nowrap">
                  <span class="font-bold text-teal-800">{{ del.permission }}</span>
                </td>

                <!-- Waktu Mulai -->
                <td class="py-3 px-4 font-mono text-[11px] text-slate-600 whitespace-nowrap">
                  {{ del.validFrom }}
                </td>

                <!-- Waktu Berakhir -->
                <td class="py-3 px-4 font-mono text-[11px] text-slate-600 whitespace-nowrap">
                  {{ del.validUntil }}
                </td>

                <!-- Status Badge -->
                <td class="py-3 px-4 text-center whitespace-nowrap">
                  <span
                    v-if="getDelegationStatus(del) === 'ACTIVE'"
                    class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200"
                  >
                    ACTIVE
                  </span>
                  <span
                    v-else-if="getDelegationStatus(del) === 'REVOKED'"
                    class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200"
                  >
                    REVOKED
                  </span>
                  <span
                    v-else
                    class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-600 border border-slate-200"
                  >
                    EXPIRED
                  </span>
                </td>

                <!-- Actions -->
                <td class="py-3 px-4 text-center whitespace-nowrap">
                  <div class="flex items-center justify-center space-x-1.5">
                    <!-- Detail Ledger Button -->
                    <button
                      @click="openBlockchainHistory(del.delegationId)"
                      class="px-2.5 py-1 text-[11px] font-medium text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded transition-colors cursor-pointer"
                      title="Lihat Detail Riwayat Blockchain"
                    >
                      DETAIL
                    </button>

                    <!-- Revoke Button (only active) -->
                    <button
                      v-if="getDelegationStatus(del) === 'ACTIVE' && authStore.isDoctor"
                      @click="openRevokeConfirm(del.delegationId)"
                      class="px-2.5 py-1 text-[11px] font-medium text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded transition-colors cursor-pointer"
                      title="Cabut wewenang delegasi sekarang"
                    >
                      REVOKE
                    </button>
                  </div>
                </td>
              </tr>

              <tr v-if="delegations.length === 0 && !isLoadingList">
                <td colspan="9" class="py-8 text-center text-slate-400">
                  Belum ada catatan pendelegasian wewenang aktif.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- MODAL 1: Bukti Riwayat Blok Ledger Hyperledger Fabric -->
    <div
      v-if="showHistoryModal"
      class="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4"
    >
      <div class="bg-white rounded-xl max-w-2xl w-full p-5 shadow-2xl border border-slate-200 space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h4 class="font-bold text-sm text-slate-800">
            Riwayat Mutasi Ledger Blockchain (GetHistoryForKey)
          </h4>
          <button @click="showHistoryModal = false" class="text-slate-400 hover:text-slate-600 font-bold px-2">
            &times;
          </button>
        </div>

        <div class="text-xs text-slate-500">
          ID Delegasi: <span class="font-mono font-bold text-slate-800">{{ selectedDelegationId }}</span>
        </div>

        <div v-if="isLoadingHistory" class="py-8 text-center text-xs text-slate-400">
          Membaca block iterator dari peer Hyperledger Fabric...
        </div>

        <div v-else class="space-y-3 max-h-96 overflow-y-auto pr-1">
          <div
            v-for="(hist, idx) in blockchainHistory"
            :key="idx"
            class="p-3.5 rounded-lg border border-indigo-100 bg-indigo-50/30 space-y-2 text-xs"
          >
            <div class="flex items-center justify-between">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-100 text-indigo-800">
                Blok Mutasi #{{ idx + 1 }}
              </span>
              <span class="font-mono text-[11px] text-slate-500">{{ hist.timestamp }}</span>
            </div>

            <div class="font-mono text-[11px] break-all bg-white p-2 rounded border border-slate-200">
              <span class="text-slate-400">TxID:</span> <span class="font-bold text-indigo-700">{{ hist.txId }}</span>
            </div>

            <div class="space-y-1">
              <div class="text-[10px] font-semibold text-slate-600 uppercase tracking-wider">On-Chain State Value:</div>
              <pre class="font-mono text-[11px] bg-slate-900 text-emerald-400 p-3 rounded-lg overflow-x-auto leading-relaxed border border-slate-800"><code>{{ formatJsonValue(hist.value) }}</code></pre>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-2">
          <button
            @click="showHistoryModal = false"
            class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs rounded transition-all"
          >
            Tutup
          </button>
        </div>
      </div>
    </div>

    <!-- MODAL 2: Konfirmasi Revocation -->
    <div
      v-if="showRevokeDialog"
      class="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4"
    >
      <div class="bg-white rounded-xl max-w-md w-full p-5 shadow-2xl border border-slate-200 space-y-4">
        <div class="border-b border-slate-100 pb-2">
          <h4 class="font-bold text-sm text-slate-800">Konfirmasi Pencabutan Akses (Revoke)</h4>
        </div>

        <p class="text-xs text-slate-600 leading-relaxed">
          Pencabutan ini akan mengubah status wewenang menjadi <span class="font-bold text-rose-600">REVOKED</span> di ledger blockchain. Perawat tidak akan dapat lagi mengakses modul terkait.
        </p>

        <div>
          <label class="block text-xs font-semibold text-slate-700 mb-1">Alasan Pencabutan</label>
          <input
            v-model="revokeReason"
            type="text"
            required
            class="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded text-xs focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
          />
        </div>

        <div class="flex justify-end space-x-2 pt-2">
          <button
            @click="showRevokeDialog = false"
            class="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs rounded"
          >
            Batal
          </button>
          <button
            @click="executeRevoke"
            :disabled="isRevoking"
            class="px-4 py-1.5 bg-rose-700 hover:bg-rose-800 text-white font-semibold text-xs rounded shadow-xs disabled:opacity-50"
          >
            {{ isRevoking ? 'Mencabut di Blockchain...' : 'Cabut Wewenang Sekarang' }}
          </button>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
