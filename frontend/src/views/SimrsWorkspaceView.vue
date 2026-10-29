<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/AppLayout.vue'
import { delegationApi, testAccessApi } from '@/services/api'
import type { DelegationResponse } from '@/types'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

// Selected Module Tab ('Master Data', 'Rekam Medis', 'Logistik', 'Transaksi', dll)
const selectedModule = ref<string>('Master Data')

// Known module catalog mapping (icons, default description, and permIds)
const moduleCatalog: Record<string, { permId: number; icon: string; description: string }> = {
  'Master Data': {
    permId: 2899,
    icon: 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4',
    description: 'Master data pasien, paket obat, dan registrasi pelayanan rumah sakit',
  },
  'Rekam Medis': {
    permId: 1420,
    icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z',
    description: 'Catatan rekam medis elektronik (EMR), diagnosa SOAP, dan riwayat tindakan pasien',
  },
  'Logistik': {
    permId: 31,
    icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4',
    description: 'Permintaan dan pengeluaran obat darurat dari depo farmasi rumah sakit',
  },
  'Transaksi': {
    permId: 1202,
    icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01',
    description: 'Transaksi tindakan poliklinik rawat jalan dan billing pemeriksaan',
  },
}

const defaultModuleMeta = {
  permId: 0,
  icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2',
  description: 'Modul pelayanan terotorisasi melalui Smart Contract Hyperledger Fabric',
}

// Delegations State
const receivedDelegations = ref<DelegationResponse[]>([])
const allActiveDelegations = ref<DelegationResponse[]>([])
const isLoadingDelegations = ref(false)

// Timer for dynamic countdown update
const currentTime = ref(new Date())
let timerInterval: ReturnType<typeof setInterval> | null = null

// Helper to determine true cascading active state
const isDelegationTrulyActive = (del: DelegationResponse): boolean => {
  if (del.status !== 'ACTIVE') return false
  const now = currentTime.value.getTime()
  const until = new Date(del.validUntil).getTime()
  if (isNaN(until) || now > until) return false

  if (del.parentDelegationId && del.parentDelegationId.trim() !== '') {
    const parent = allActiveDelegations.value.find((d) => d.delegationId === del.parentDelegationId)
    if (!parent) return false
    return isDelegationTrulyActive(parent)
  }
  return true
}

// Dynamic Modules: Query & generate options purely from Hyperledger Fabric Ledger delegations
const dynamicModules = computed(() => {
  if (authStore.isAdmin) {
    return Object.entries(moduleCatalog).map(([name, meta]) => ({
      name,
      permId: meta.permId,
      icon: meta.icon,
      description: meta.description,
      delegation: null as DelegationResponse | null,
    }))
  }

  // For Staff / Perawat: ONLY show modules that have an active delegation in Fabric World State!
  const activeDels = receivedDelegations.value.filter(isDelegationTrulyActive)
  const map = new Map<string, {
    name: string
    permId: number
    icon: string
    description: string
    delegation: DelegationResponse
  }>()

  for (const del of activeDels) {
    const modName = del.targetModule
    if (!map.has(modName)) {
      const meta = moduleCatalog[modName] || defaultModuleMeta
      map.set(modName, {
        name: modName,
        permId: meta.permId,
        icon: meta.icon,
        description: meta.description,
        delegation: del,
      })
    }
  }

  return Array.from(map.values())
})

// Auto-select first active dynamic module if currently selected module is not present
watch(
  dynamicModules,
  (newMods) => {
    if (newMods.length > 0) {
      const exists = newMods.some((m) => m.name.toLowerCase() === selectedModule.value.toLowerCase())
      if (!exists && newMods[0]) {
        selectedModule.value = newMods[0].name
      }
    }
  },
  { immediate: true }
)

// Calculate countdown string
const getCountdown = (validUntil: string): string => {
  const diff = new Date(validUntil).getTime() - currentTime.value.getTime()
  if (diff <= 0) return 'Telah Berakhir'
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  const seconds = Math.floor((diff % (1000 * 60)) / 1000)
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}

// Active delegation matching currently selected module
const currentModuleDelegation = computed(() => {
  return receivedDelegations.value.find(
    (d) =>
      d.targetModule.toLowerCase() === selectedModule.value.toLowerCase() &&
      isDelegationTrulyActive(d)
  )
})

// Two-Tier Authorization Evaluation for currently logged-in user & selected module
const authorizationEvaluation = computed(() => {
  const user = authStore.user
  const mod = selectedModule.value

  // Admin has static Tier 1
  if (authStore.isAdmin) {
    return {
      isGranted: true,
      permission: 'READ_WRITE (ADMIN)',
      source: 'Role Administrator (Role ID 1)',
      badgeClass: 'bg-emerald-100 text-emerald-800 border-emerald-300',
    }
  }

  // Doctor has static Tier 1 for Master Data, Rekam Medis, Transaksi
  if (authStore.isDoctor) {
    if (mod === 'Master Data' || mod === 'Rekam Medis' || mod === 'Transaksi') {
      return {
        isGranted: true,
        permission: 'READ_WRITE',
        source: `Peran ${user?.roleName || 'Dokter'} (Role ID ${user?.roleId})`,
        badgeClass: 'bg-emerald-100 text-emerald-800 border-emerald-300',
      }
    }
  }

  // Tier 2: Check active Fabric DAC delegation
  const activeDel = currentModuleDelegation.value
  if (activeDel) {
    return {
      isGranted: true,
      permission: activeDel.permission || 'READ_WRITE',
      source: `Kuasa dari ${activeDel.delegatorName || activeDel.delegatorId}`,
      delegationId: activeDel.delegationId,
      parentDelegationId: activeDel.parentDelegationId,
      depth: activeDel.depth ?? 0,
      validUntil: activeDel.validUntil,
      countdown: getCountdown(activeDel.validUntil),
      badgeClass: 'bg-teal-100 text-teal-900 border-teal-300 font-mono',
    }
  }

  // If neither: Access Denied
  return {
    isGranted: false,
    permission: 'TIDAK ADA AKSES (403)',
    source: `Peran ${user?.roleName || 'Staff'} Tidak Berwenang`,
    badgeClass: 'bg-rose-100 text-rose-800 border-rose-300',
  }
})

// Realistic Mock Hospital Data for Master Data Patients
interface PatientRecord {
  noRm: string
  nik: string
  name: string
  age: number
  gender: string
  room: string
  dpjp: string
  primaryDiagnosis: string
  medicalOrders: string
  status: string
  verifiedAt?: string
  verifiedBy?: string
}

const patientList = ref<PatientRecord[]>([
  {
    noRm: 'RM-2026-0042',
    nik: '3578012405700001',
    name: 'Tn. Bambang Santoso',
    age: 54,
    gender: 'Laki-laki',
    room: 'Bangsal Melati Bed 3A',
    dpjp: 'dr. Agus Prasetyo, Sp.A',
    primaryDiagnosis: 'Bronkopneumonia Akut & Hipertensi Grade 2',
    medicalOrders: 'Cefotaxime 1g IV / 12 jam, Amlodipine 10mg PO pagi, Nebulisasi Ventolin 2.5mg / 8 jam',
    status: 'Menunggu Verifikasi Medis',
  },
  {
    noRm: 'RM-2026-0088',
    nik: '3578016408930002',
    name: 'Ny. Dewi Sartika',
    age: 31,
    gender: 'Perempuan',
    room: 'Bangsal Melati Bed 3B',
    dpjp: 'dr. Agus Prasetyo, Sp.A',
    primaryDiagnosis: 'Gastroenteritis Akut dengan Dehidrasi Sedang',
    medicalOrders: 'Infus RL 20 tpm, Ondansetron 4mg IV k/p mual, Ranitidine 50mg IV / 12 jam',
    status: 'Menunggu Verifikasi Medis',
  },
  {
    noRm: 'RM-2026-0105',
    nik: '3578011202170004',
    name: 'An. Kevin Nugraha',
    age: 7,
    gender: 'Laki-laki',
    room: 'Bangsal Anak Flamboyan 1',
    dpjp: 'dr. Agus Prasetyo, Sp.A',
    primaryDiagnosis: 'Dengue Hemorrhagic Fever (DHF) Grade I',
    medicalOrders: 'Infus D5 1/4 NS 12 tpm mikro, Paracetamol syrup 3x1 cth k/p demam, Observasi TTV / 4 jam',
    status: 'Menunggu Verifikasi Medis',
  },
])

// Realistic Mock Data for Rekam Medis
const emrRecord = ref({
  noRm: 'RM-2026-0042',
  patientName: 'Tn. Bambang Santoso (54 th)',
  dpjp: 'dr. Agus Prasetyo, Sp.A',
  admittedDate: '2026-10-24 14:20:00',
  soapSubjective: 'Pasien mengeluhkan sesak nafas memberat sejak kemarin sore disertai batuk berdahak kental kekuningan dan demam naik turun.',
  soapObjective: 'TD: 150/95 mmHg, Nadi: 98 x/mnt, RR: 26 x/mnt, Suhu: 38.4 C, SpO2: 94% room air. Ronkhi basah kasar di kedua basal paru.',
  soapAssessment: 'Bronkopneumonia Akut komunitas (CAP) dd/ HAP + Hipertensi Stage II terkontrol parsial.',
  soapPlan: 'Tirah baring semi-fowler, O2 nasal kanul 3 lpm, Cefotaxime 1g IV / 12 jam, Amlodipine 10mg PO, Rontgen Thorax ulang H+3.',
  isEmrOpened: false,
})

// Realistic Mock Data for Logistik Farmasi
const pharmacyOrders = ref([
  {
    orderId: 'ORD-FAR-202610-001',
    item: 'Cefotaxime Injeksi 1000 mg (Vial)',
    category: 'Antibiotik Darurat',
    requestedQty: 10,
    unit: 'Vial',
    depo: 'Depo Farmasi Rawat Inap',
    status: 'Menunggu Persetujuan Pengeluaran',
  },
  {
    orderId: 'ORD-FAR-202610-002',
    item: 'Ringer Lactate Infus 500 ml (Kolf)',
    category: 'Cairan Resusitasi',
    requestedQty: 24,
    unit: 'Kolf',
    depo: 'Depo Farmasi IGD/Rawat Inap',
    status: 'Menunggu Persetujuan Pengeluaran',
  },
  {
    orderId: 'ORD-FAR-202610-003',
    item: 'Amoksisilin 500 mg (Kaplet)',
    category: 'Antibiotik Oral',
    requestedQty: 30,
    unit: 'Kaplet',
    depo: 'Depo Farmasi Rawat Inap',
    status: 'Menunggu Persetujuan Pengeluaran',
  },
])

// Action Execution State
const isExecuting = ref(false)
const selectedPatientForModal = ref<PatientRecord | null>(null)
const customActionNote = ref('Pemberian antibiotik dan monitoring TTV sesuai instruksi DPJP')
const showActionModal = ref(false)

// Result Feedback State (Modal/Alert)
const actionFeedback = ref<{
  isOpen: boolean
  success: boolean
  title: string
  message: string
  executedBy: string
  role: string
  operation: string
  authorizationTier: string
  permId: number
  timestamp: string
  rawError?: string
} | null>(null)

// Open Action Modal for Patient
const openPatientActionModal = (patient: PatientRecord) => {
  selectedPatientForModal.value = patient
  customActionNote.value = `Verifikasi asuhan klinis & order resep untuk ${patient.name} (${patient.noRm})`
  showActionModal.value = true
}

// Execute Real Backend Request for Master Data
const handleExecuteMasterData = async () => {
  if (!selectedPatientForModal.value) return
  isExecuting.value = true
  showActionModal.value = false

  const patient = selectedPatientForModal.value

  try {
    const res = await testAccessApi.executeMasterDataPasien({
      patientId: patient.noRm,
      actionNote: customActionNote.value,
    })

    const data = res.data || {}
    const tier = data.authorizationTier || (authStore.isDoctor ? 'Tier 1 (Static RBAC)' : 'Tier 2 (Fabric Smart Contract DAC)')
    const nowStr = data.timestamp || new Date().toLocaleString('id-ID')

    // Update patient table
    patient.status = 'Telah Diverifikasi'
    patient.verifiedAt = nowStr
    patient.verifiedBy = `${authStore.user?.fullName} (${authStore.user?.roleName})`

    actionFeedback.value = {
      isOpen: true,
      success: true,
      title: 'AKSI BERHASIL DILAKUKAN (200 OK)',
      message: res.message || 'Pembaruan data rekam medis dan pelayanan pasien berhasil diproses oleh SIMRS.',
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Update Master Data Pasien & Pelayanan',
      authorizationTier: tier,
      permId: 2899,
      timestamp: nowStr,
    }
  } catch (err: any) {
    const status = err.response?.status
    const errMsg = err.response?.data?.message || err.message || 'Akses ditolak.'

    actionFeedback.value = {
      isOpen: true,
      success: false,
      title: `AKSES DITOLAK (${status || 403} FORBIDDEN)`,
      message: status === 403
        ? 'Sistem SIMRS menolak operasi ini karena Anda tidak memiliki hak akses statis dan tidak ada delegasi wewenang aktif dari Dokter Penanggung Jawab di Smart Contract Hyperledger Fabric.'
        : errMsg,
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Update Master Data Pasien (Perm ID 2899)',
      authorizationTier: 'Two-Tier Security Check: GAGAL',
      permId: 2899,
      timestamp: new Date().toLocaleString('id-ID'),
      rawError: errMsg,
    }
  } finally {
    isExecuting.value = false
  }
}

// Execute Real Backend Request for Rekam Medis
const handleExecuteRekamMedis = async () => {
  isExecuting.value = true
  try {
    const res = await testAccessApi.executeBacaRekamMedis()
    emrRecord.value.isEmrOpened = true

    actionFeedback.value = {
      isOpen: true,
      success: true,
      title: 'BERKAS REKAM MEDIS DIBUKA (200 OK)',
      message: res.message || 'Akses membaca berkas rekam medis elektronik pasien berhasil disetujui.',
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Baca Rekam Medis (Perm ID 1420)',
      authorizationTier: authStore.isDoctor ? 'Tier 1 (Static RBAC)' : 'Tier 2 (Fabric Smart Contract DAC)',
      permId: 1420,
      timestamp: new Date().toLocaleString('id-ID'),
    }
  } catch (err: any) {
    actionFeedback.value = {
      isOpen: true,
      success: false,
      title: 'AKSES DITOLAK (403 FORBIDDEN)',
      message: 'Akses rekam medis ditolak oleh Smart Contract Fabric. Anda tidak memiliki wewenang aktif untuk modul ini.',
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Baca Rekam Medis (Perm ID 1420)',
      authorizationTier: 'Two-Tier Security Check: GAGAL',
      permId: 1420,
      timestamp: new Date().toLocaleString('id-ID'),
      rawError: err.response?.data?.message || err.message,
    }
  } finally {
    isExecuting.value = false
  }
}

// Execute Real Backend Request for Logistik
const handleExecuteLogistik = async (order: any) => {
  isExecuting.value = true
  try {
    const res = await testAccessApi.executeLogistikPemesanan()
    order.status = 'Disetujui & Dikeluarkan'

    actionFeedback.value = {
      isOpen: true,
      success: true,
      title: 'PENGELUARAN OBAT DISETUJUI (200 OK)',
      message: res.message || `Permintaan ${order.item} (${order.requestedQty} ${order.unit}) berhasil diproses oleh Depo Farmasi.`,
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Logistik Pemesanan Obat (Perm ID 31)',
      authorizationTier: authStore.isAdmin ? 'Tier 1 (Static RBAC)' : 'Tier 2 (Fabric Smart Contract DAC)',
      permId: 31,
      timestamp: new Date().toLocaleString('id-ID'),
    }
  } catch (err: any) {
    actionFeedback.value = {
      isOpen: true,
      success: false,
      title: 'AKSES DITOLAK (403 FORBIDDEN)',
      message: 'Pengeluaran obat darurat dibatalkan. Anda tidak memiliki wewenang delegasi logistik farmasi aktif di Smart Contract.',
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Logistik Pemesanan Obat (Perm ID 31)',
      authorizationTier: 'Two-Tier Security Check: GAGAL',
      permId: 31,
      timestamp: new Date().toLocaleString('id-ID'),
      rawError: err.response?.data?.message || err.message,
    }
  } finally {
    isExecuting.value = false
  }
}

// Execute Real Backend Request for Transaksi Rawat Jalan
const handleExecuteTransaksi = async () => {
  isExecuting.value = true
  try {
    const res = await testAccessApi.executeRawatJalan()
    actionFeedback.value = {
      isOpen: true,
      success: true,
      title: 'TRANSAKSI RAWAT JALAN BERHASIL (200 OK)',
      message: res.message || 'Transaksi billing dan tindakan poliklinik berhasil disimpan di sistem.',
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Transaksi Rawat Jalan (Perm ID 1202)',
      authorizationTier: authStore.isDoctor ? 'Tier 1 (Static RBAC)' : 'Tier 2 (Fabric Smart Contract DAC)',
      permId: 1202,
      timestamp: new Date().toLocaleString('id-ID'),
    }
  } catch (err: any) {
    actionFeedback.value = {
      isOpen: true,
      success: false,
      title: 'AKSES DITOLAK (403 FORBIDDEN)',
      message: 'Transaksi rawat jalan ditolak oleh Smart Contract. Tidak ada wewenang aktif yang ditemukan.',
      executedBy: authStore.user?.fullName || 'Pengguna',
      role: authStore.user?.roleName || 'Staff',
      operation: 'Transaksi Rawat Jalan (Perm ID 1202)',
      authorizationTier: 'Two-Tier Security Check: GAGAL',
      permId: 1202,
      timestamp: new Date().toLocaleString('id-ID'),
      rawError: err.response?.data?.message || err.message,
    }
  } finally {
    isExecuting.value = false
  }
}

// Fetch user profile & active delegations
const loadData = async () => {
  isLoadingDelegations.value = true
  try {
    if (!authStore.user) {
      await authStore.fetchProfile()
    }

    // Fetch all delegations to check parent cascading state
    const allRes = await delegationApi.getMyDelegations()
    if (allRes.success && allRes.data) {
      allActiveDelegations.value = allRes.data
    }

    // Fetch delegations received by logged-in user
    if (authStore.user?.userId) {
      const recRes = await delegationApi.getDelegationsByDelegatee(authStore.user.userId)
      if (recRes.success && recRes.data) {
        receivedDelegations.value = recRes.data
      }
    }

    // Evaluate active received delegation state
    const activeCount = receivedDelegations.value.filter(isDelegationTrulyActive).length
    authStore.hasActiveDelegation = activeCount > 0
    localStorage.setItem('dac_has_active_del', activeCount > 0 ? 'true' : 'false')

    // Access restriction: only accessible by user with active received delegation or Admin
    if (!authStore.isAdmin && activeCount === 0) {
      router.replace({ name: 'profile' })
      return
    }
  } catch (err) {
    console.error('Failed to load delegation context in SIMRS workspace:', err)
  } finally {
    isLoadingDelegations.value = false
  }
}

onMounted(async () => {
  // Sync selected module from query parameter if provided
  if (route.query.module) {
    selectedModule.value = String(route.query.module)
  }

  await loadData()

  // Realtime live ticker for clock and countdowns
  timerInterval = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
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
      <!-- Two-Tier Authorization Status Banner -->
      <div
        class="rounded-xl border p-4 sm:p-5 shadow-xs transition-all space-y-3"
        :class="[
          authorizationEvaluation.isGranted
            ? 'bg-gradient-to-r from-emerald-50 via-teal-50/70 to-white border-emerald-300'
            : 'bg-rose-50/90 border-rose-300'
        ]"
      >
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b pb-2.5"
             :class="authorizationEvaluation.isGranted ? 'border-emerald-200' : 'border-rose-200'">
          <div class="flex items-center space-x-2.5">
            <span class="flex h-3 w-3 relative">
              <span
                v-if="authorizationEvaluation.isGranted"
                class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"
              ></span>
              <span
                class="relative inline-flex rounded-full h-3 w-3"
                :class="authorizationEvaluation.isGranted ? 'bg-emerald-600' : 'bg-rose-600'"
              ></span>
            </span>
            <span class="text-xs font-bold uppercase tracking-wider text-slate-700">
              Status Otorisasi Modul:
            </span>
            <span class="text-xs font-mono font-bold px-2.5 py-0.5 rounded border shadow-2xs"
                  :class="authorizationEvaluation.badgeClass">
              {{ authorizationEvaluation.permission }}
            </span>
          </div>

          <div v-if="authorizationEvaluation.countdown" class="flex items-center space-x-2 text-xs font-mono font-bold text-teal-900 bg-white/90 px-2.5 py-1 rounded border border-teal-200">
            <svg class="w-4 h-4 text-teal-700 animate-spin" style="animation-duration: 8s;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <span>Sisa Waktu Wewenang: {{ authorizationEvaluation.countdown }}</span>
          </div>
        </div>

        <!-- Evaluation Details Grid -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
          <div class="bg-white/80 p-3 rounded-lg border border-slate-200 shadow-2xs">
            <span class="text-[10px] text-slate-500 font-bold block uppercase tracking-wider mb-1">
              Sumber Otorisasi:
            </span>
            <span class="font-bold text-slate-900">
              {{ authorizationEvaluation.source }}
            </span>
          </div>

          <div class="bg-white/80 p-3 rounded-lg border border-slate-200 shadow-2xs">
            <span class="text-[10px] text-slate-500 font-bold block uppercase tracking-wider mb-1">
              Modul Aktif:
            </span>
            <span class="font-bold text-teal-800">
              {{ selectedModule }} (Perm ID: {{ moduleCatalog[selectedModule]?.permId || 2899 }})
            </span>
          </div>

          <div class="bg-white/80 p-3 rounded-lg border border-slate-200 shadow-2xs">
            <span class="text-[10px] text-slate-500 font-bold block uppercase tracking-wider mb-1">
              Verifikasi Smart Contract:
            </span>
            <span v-if="authorizationEvaluation.delegationId" class="font-mono font-semibold text-emerald-800 truncate block">
              ID: {{ authorizationEvaluation.delegationId }}
            </span>
            <span v-else-if="authStore.isDoctor || authStore.isAdmin" class="font-semibold text-slate-700">
              Bawaan Kepegawaian (Static RBAC)
            </span>
            <span v-else class="font-semibold text-rose-700">
              Tidak Ada Wewenang Aktif (Ditolak)
            </span>
          </div>
        </div>
      </div>

      <!-- 2. Dynamic Module Selector Tabs (Purely Driven by Hyperledger Fabric Ledger) -->
      <div v-if="dynamicModules.length > 0" class="bg-white rounded-xl border border-slate-200 p-2 shadow-xs">
        <div class="flex items-center justify-between px-3 py-1.5 border-b border-slate-100 mb-2">
          <div class="flex items-center space-x-2">
            <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span class="text-[11px] font-bold text-slate-700 uppercase tracking-wider">
              Modul Terotorisasi di Hyperledger Fabric:
            </span>
          </div>
          <span class="text-[10px] font-mono font-semibold text-emerald-800 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
            {{ dynamicModules.length }} Modul Aktif
          </span>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
          <button
            v-for="mod in dynamicModules"
            :key="mod.name"
            @click="selectedModule = mod.name"
            class="flex items-center space-x-3 p-3 rounded-lg text-left transition-all border cursor-pointer"
            :class="[
              selectedModule.toLowerCase() === mod.name.toLowerCase()
                ? 'bg-teal-50 border-teal-300 text-teal-950 font-bold shadow-2xs'
                : 'bg-white border-transparent text-slate-600 hover:bg-slate-50 hover:text-slate-900'
            ]"
          >
            <div
              class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
              :class="selectedModule.toLowerCase() === mod.name.toLowerCase() ? 'bg-teal-700 text-white' : 'bg-slate-100 text-slate-500'"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="mod.icon"/>
              </svg>
            </div>
            <div class="min-w-0">
              <div class="text-xs truncate flex items-center space-x-1.5">
                <span>{{ mod.name }}</span>
                <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              </div>
              <div class="text-[10px] text-slate-400 font-mono truncate">
                Perm #{{ mod.permId || '-' }} &bull; {{ mod.delegation?.permission || 'READ_WRITE' }}
              </div>
            </div>
          </button>
        </div>
      </div>

      <!-- 4. Module Workspace Content Panels -->

      <!-- TAB 1: MASTER DATA PASIEN & PELAYANAN (Primary for Sidang Skripsi) -->
      <div v-if="selectedModule === 'Master Data'" class="space-y-4">
        <!-- Top Action & Search Bar -->
        <div class="bg-white rounded-xl border border-slate-200 p-4 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 class="text-sm sm:text-base font-bold text-slate-800">
              Daftar Pasien Rawat Inap & Instruksi Medis DPJP
            </h2>
            <p class="text-xs text-slate-500">
              Setiap verifikasi tindakan medis memerlukan hak akses wewenang dokter atau pendelegasian aktif.
            </p>
          </div>

          <div class="flex items-center space-x-2">
            <span class="text-xs font-semibold text-slate-600 bg-slate-100 px-3 py-1.5 rounded-lg border border-slate-200">
              Total: {{ patientList.length }} Pasien
            </span>
          </div>
        </div>

        <!-- Realistic Patient Records Table -->
        <div class="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs text-slate-600">
              <thead class="bg-slate-100/80 text-slate-700 uppercase font-bold text-[10px] tracking-wider border-b border-slate-200">
                <tr>
                  <th class="py-3 px-4">No. RM & Pasien</th>
                  <th class="py-3 px-4">Ruang Rawat</th>
                  <th class="py-3 px-4">DPJP & Diagnosa</th>
                  <th class="py-3 px-4">Instruksi Medis / Terapi</th>
                  <th class="py-3 px-4">Status Verifikasi</th>
                  <th class="py-3 px-4 text-center">Aksi Pelayanan</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="patient in patientList" :key="patient.noRm" class="hover:bg-slate-50/60 transition-colors">
                  <!-- Patient & No RM -->
                  <td class="py-3 px-4">
                    <div class="font-mono font-bold text-slate-900">{{ patient.noRm }}</div>
                    <div class="font-semibold text-slate-800">{{ patient.name }}</div>
                    <div class="text-[10px] text-slate-400">{{ patient.age }} th &bull; {{ patient.gender }}</div>
                  </td>

                  <!-- Room -->
                  <td class="py-3 px-4 whitespace-nowrap">
                    <span class="inline-block px-2.5 py-1 rounded bg-slate-100 text-slate-800 font-medium text-[11px] border border-slate-200">
                      {{ patient.room }}
                    </span>
                  </td>

                  <!-- DPJP & Diagnosis -->
                  <td class="py-3 px-4">
                    <div class="font-bold text-teal-800">{{ patient.dpjp }}</div>
                    <div class="text-[11px] text-slate-700 mt-0.5">{{ patient.primaryDiagnosis }}</div>
                  </td>

                  <!-- Medical Orders -->
                  <td class="py-3 px-4 max-w-xs">
                    <div class="font-mono text-[11px] text-slate-700 bg-slate-50 p-2 rounded border border-slate-200 leading-relaxed">
                      {{ patient.medicalOrders }}
                    </div>
                  </td>

                  <!-- Status -->
                  <td class="py-3 px-4 whitespace-nowrap">
                    <span
                      class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold border"
                      :class="[
                        patient.status.includes('Telah Diverifikasi')
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                          : 'bg-amber-50 text-amber-800 border-amber-300'
                      ]"
                    >
                      <span class="w-1.5 h-1.5 rounded-full mr-1.5"
                            :class="patient.status.includes('Telah Diverifikasi') ? 'bg-emerald-500' : 'bg-amber-500'"></span>
                      {{ patient.status }}
                    </span>
                    <div v-if="patient.verifiedAt" class="text-[10px] text-slate-500 mt-1 font-mono">
                      {{ patient.verifiedAt }}
                    </div>
                  </td>

                  <!-- Action Button -->
                  <td class="py-3 px-4 text-center whitespace-nowrap">
                    <button
                      @click="openPatientActionModal(patient)"
                      :disabled="isExecuting"
                      class="inline-flex items-center px-3 py-1.5 text-xs font-bold text-white bg-teal-800 hover:bg-teal-900 disabled:opacity-50 rounded-lg shadow-2xs transition-colors cursor-pointer"
                    >
                      <svg class="w-3.5 h-3.5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                      </svg>
                      Verifikasi Tindakan
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- TAB 2: REKAM MEDIS ELEKTRONIK (EMR) -->
      <div v-else-if="selectedModule === 'Rekam Medis'" class="space-y-4">
        <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h2 class="text-sm sm:text-base font-bold text-slate-800">
                Berkas Rekam Medis Pasien: {{ emrRecord.patientName }}
              </h2>
              <p class="text-xs text-slate-500">
                Akses modul rekam medis memerlukan wewenang klinis (Perm ID 1420).
              </p>
            </div>
            <button
              @click="handleExecuteRekamMedis"
              :disabled="isExecuting"
              class="inline-flex items-center px-3.5 py-2 text-xs font-bold text-white bg-teal-800 hover:bg-teal-900 disabled:opacity-50 rounded-lg shadow-xs transition-colors cursor-pointer"
            >
              <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
              </svg>
              Buka Berkas Rekam Medis (Verifikasi Fabric)
            </button>
          </div>

          <!-- EMR Clinical Notes (SOAP) -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="space-y-3">
              <div class="bg-slate-50 p-3.5 rounded-lg border border-slate-200">
                <span class="text-[10px] font-bold text-teal-800 uppercase tracking-wider block mb-1">
                  [S] SUBJEKTIF (ANAMNESIS)
                </span>
                <p class="text-xs text-slate-700 leading-relaxed">{{ emrRecord.soapSubjective }}</p>
              </div>

              <div class="bg-slate-50 p-3.5 rounded-lg border border-slate-200">
                <span class="text-[10px] font-bold text-teal-800 uppercase tracking-wider block mb-1">
                  [O] OBJEKTIF (PEMERIKSAAN FISIK & TTV)
                </span>
                <p class="text-xs text-slate-700 font-mono leading-relaxed">{{ emrRecord.soapObjective }}</p>
              </div>
            </div>

            <div class="space-y-3">
              <div class="bg-slate-50 p-3.5 rounded-lg border border-slate-200">
                <span class="text-[10px] font-bold text-teal-800 uppercase tracking-wider block mb-1">
                  [A] ASESMEN / DIAGNOSA KERJA
                </span>
                <p class="text-xs text-slate-800 font-semibold leading-relaxed">{{ emrRecord.soapAssessment }}</p>
              </div>

              <div class="bg-slate-50 p-3.5 rounded-lg border border-slate-200">
                <span class="text-[10px] font-bold text-teal-800 uppercase tracking-wider block mb-1">
                  [P] PLANNING & INSTRUKSI TERAPI
                </span>
                <p class="text-xs text-slate-700 leading-relaxed">{{ emrRecord.soapPlan }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 3: LOGISTIK FARMASI -->
      <div v-else-if="selectedModule === 'Logistik'" class="space-y-4">
        <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
          <div class="border-b border-slate-100 pb-3">
            <h2 class="text-sm sm:text-base font-bold text-slate-800">
              Permintaan Pengeluaran Obat Darurat & Resep Rawat Inap
            </h2>
            <p class="text-xs text-slate-500">
              Persetujuan pengeluaran logistik farmasi memerlukan hak akses wewenang farmasi/delegasi (Perm ID 31).
            </p>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs text-slate-600">
              <thead class="bg-slate-100/80 text-slate-700 uppercase font-bold text-[10px] tracking-wider border-b border-slate-200">
                <tr>
                  <th class="py-3 px-4">No. Requisition</th>
                  <th class="py-3 px-4">Nama Obat / Alkes</th>
                  <th class="py-3 px-4">Kategori</th>
                  <th class="py-3 px-4">Jumlah Diminta</th>
                  <th class="py-3 px-4">Depo Pengambil</th>
                  <th class="py-3 px-4">Status</th>
                  <th class="py-3 px-4 text-center">Aksi Verifikasi</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="order in pharmacyOrders" :key="order.orderId" class="hover:bg-slate-50/60">
                  <td class="py-3 px-4 font-mono font-bold text-slate-800">{{ order.orderId }}</td>
                  <td class="py-3 px-4 font-semibold text-slate-900">{{ order.item }}</td>
                  <td class="py-3 px-4 text-slate-600">{{ order.category }}</td>
                  <td class="py-3 px-4 font-mono font-bold text-teal-800">{{ order.requestedQty }} {{ order.unit }}</td>
                  <td class="py-3 px-4 text-slate-600">{{ order.depo }}</td>
                  <td class="py-3 px-4">
                    <span
                      class="inline-block px-2 py-0.5 rounded text-[10px] font-bold border"
                      :class="order.status.includes('Disetujui') ? 'bg-emerald-50 text-emerald-800 border-emerald-300' : 'bg-amber-50 text-amber-800 border-amber-300'"
                    >
                      {{ order.status }}
                    </span>
                  </td>
                  <td class="py-3 px-4 text-center">
                    <button
                      @click="handleExecuteLogistik(order)"
                      :disabled="isExecuting"
                      class="px-3 py-1.5 text-xs font-bold text-white bg-teal-800 hover:bg-teal-900 disabled:opacity-50 rounded-lg shadow-2xs transition-colors cursor-pointer"
                    >
                      Setujui Pengeluaran
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- TAB 4: TRANSAKSI RAWAT JALAN -->
      <div v-else-if="selectedModule === 'Transaksi'" class="space-y-4">
        <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-xs space-y-4">
          <div class="border-b border-slate-100 pb-3">
            <h2 class="text-sm sm:text-base font-bold text-slate-800">
              Transaksi Pelayanan Poliklinik & Konsultasi Rawat Jalan
            </h2>
            <p class="text-xs text-slate-500">
              Validasi transaksi tindakan rawat jalan (Perm ID 1202).
            </p>
          </div>

          <div class="p-4 bg-slate-50 rounded-lg border border-slate-200 text-xs space-y-2">
            <div class="font-bold text-slate-800">Uji Billing Pelayanan Rawat Jalan:</div>
            <p class="text-slate-600 leading-relaxed">
              Tekan tombol di bawah untuk mengeksekusi pencatatan biaya dan billing tindakan klinis pasien poliklinik rawat jalan.
            </p>
            <div class="pt-2">
              <button
                @click="handleExecuteTransaksi"
                :disabled="isExecuting"
                class="px-4 py-2 text-xs font-bold text-white bg-teal-800 hover:bg-teal-900 disabled:opacity-50 rounded-lg shadow-xs transition-colors cursor-pointer"
              >
                Eksekusi Transaksi Rawat Jalan (Verifikasi Fabric)
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- MODAL: Verifikasi & Entri Tindakan Medis Pasien -->
      <div
        v-if="showActionModal && selectedPatientForModal"
        class="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4"
      >
        <div class="bg-white rounded-xl max-w-lg w-full p-5 shadow-2xl border border-slate-200 space-y-4">
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h3 class="text-sm font-bold text-slate-900">
                Formulir Verifikasi Tindakan Medis SIMRS
              </h3>
              <p class="text-[11px] text-slate-500">
                Validasi Otorisasi Two-Tier (Static RBAC & Fabric Smart Contract DAC)
              </p>
            </div>
            <button
              @click="showActionModal = false"
              class="text-slate-400 hover:text-slate-600 text-lg leading-none cursor-pointer"
            >
              &times;
            </button>
          </div>

          <!-- Patient Summary Card in Modal -->
          <div class="p-3 bg-teal-50/70 border border-teal-200 rounded-lg text-xs space-y-1.5">
            <div class="flex justify-between items-center">
              <span class="font-bold text-teal-950">{{ selectedPatientForModal.name }}</span>
              <span class="font-mono text-[11px] font-bold text-teal-800 bg-white px-2 py-0.5 rounded border border-teal-200">
                {{ selectedPatientForModal.noRm }}
              </span>
            </div>
            <div class="text-[11px] text-slate-600">
              Bangsal: <strong>{{ selectedPatientForModal.room }}</strong> &bull; DPJP: <strong>{{ selectedPatientForModal.dpjp }}</strong>
            </div>
            <div class="text-[11px] text-slate-700 bg-white/80 p-2 rounded border border-teal-100">
              <strong>Order Terapi DPJP:</strong> {{ selectedPatientForModal.medicalOrders }}
            </div>
          </div>

          <!-- Action Note Input -->
          <div class="space-y-1.5">
            <label class="block text-xs font-semibold text-slate-700">
              Catatan Tindakan Klinis Pelaksana:
            </label>
            <textarea
              v-model="customActionNote"
              rows="3"
              class="w-full text-xs p-2.5 rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-teal-700 focus:border-teal-700 leading-relaxed font-sans"
              placeholder="Tuliskan catatan verifikasi tindakan klinis..."
            ></textarea>
          </div>

          <!-- Security Notice in Modal -->
          <div class="p-2.5 rounded-lg text-[11px] border"
               :class="authorizationEvaluation.isGranted ? 'bg-emerald-50 text-emerald-900 border-emerald-200' : 'bg-rose-50 text-rose-900 border-rose-200'">
            <div class="font-bold">Otorisasi Pengguna: {{ authStore.user?.fullName }} ({{ authStore.user?.roleName }})</div>
            <div class="text-[10px] mt-0.5 leading-tight font-mono">
              Wewenang: {{ authorizationEvaluation.permission }} &bull; {{ authorizationEvaluation.source }}
            </div>
          </div>

          <!-- Buttons -->
          <div class="flex justify-end space-x-2 pt-2 border-t border-slate-100">
            <button
              @click="showActionModal = false"
              class="px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
            >
              Batal
            </button>
            <button
              @click="handleExecuteMasterData"
              :disabled="isExecuting"
              class="px-4 py-2 text-xs font-bold text-white bg-teal-800 hover:bg-teal-900 disabled:opacity-50 rounded-lg shadow-xs transition-colors cursor-pointer flex items-center"
            >
              <svg v-if="isExecuting" class="animate-spin -ml-1 mr-2 h-3.5 w-3.5 text-white" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              <span>{{ isExecuting ? 'Memproses ke Fabric...' : 'Eksekusi & Simpan Tindakan' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- MODAL: Action Execution Feedback (Success 200 or Denied 403) -->
      <div
        v-if="actionFeedback?.isOpen"
        class="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4"
      >
        <div class="bg-white rounded-xl max-w-lg w-full p-5 shadow-2xl border border-slate-200 space-y-4">
          <!-- Header with icon -->
          <div class="flex items-start space-x-3">
            <div
              class="w-10 h-10 rounded-full flex items-center justify-center shrink-0"
              :class="actionFeedback.success ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'"
            >
              <svg v-if="actionFeedback.success" class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
              </svg>
              <svg v-else class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </div>
            <div class="flex-1 min-w-0">
              <h3 class="text-sm font-black" :class="actionFeedback.success ? 'text-emerald-950' : 'text-rose-950'">
                {{ actionFeedback.title }}
              </h3>
              <p class="text-xs text-slate-600 mt-1 leading-relaxed">
                {{ actionFeedback.message }}
              </p>
            </div>
          </div>

          <!-- Transaction Audit Verification Details -->
          <div class="bg-slate-50 p-3.5 rounded-lg border border-slate-200 space-y-2 text-xs font-mono">
            <div class="flex justify-between border-b border-slate-200/80 pb-1">
              <span class="text-slate-500 font-sans">Mekanisme Otorisasi:</span>
              <strong :class="actionFeedback.success ? 'text-emerald-800' : 'text-rose-800'">
                {{ actionFeedback.authorizationTier }}
              </strong>
            </div>

            <div class="flex justify-between border-b border-slate-200/80 pb-1">
              <span class="text-slate-500 font-sans">Pelaksana Tindakan:</span>
              <span class="font-bold text-slate-800">{{ actionFeedback.executedBy }} ({{ actionFeedback.role }})</span>
            </div>

            <div class="flex justify-between border-b border-slate-200/80 pb-1">
              <span class="text-slate-500 font-sans">Operasi SIMRS:</span>
              <span class="text-slate-800">{{ actionFeedback.operation }}</span>
            </div>

            <div class="flex justify-between border-b border-slate-200/80 pb-1">
              <span class="text-slate-500 font-sans">Kode Hak Akses (Perm ID):</span>
              <span class="text-teal-800 font-bold">#{{ actionFeedback.permId }}</span>
            </div>

            <div class="flex justify-between">
              <span class="text-slate-500 font-sans">Waktu Verifikasi:</span>
              <span class="text-slate-700">{{ actionFeedback.timestamp }}</span>
            </div>
          </div>

          <!-- Raw error details if denied -->
          <div v-if="!actionFeedback.success && actionFeedback.rawError" class="p-2.5 bg-rose-50 border border-rose-200 rounded-lg text-[11px] text-rose-800 font-mono break-all">
            <strong>Detail Penolakan Spring Security / Fabric:</strong>
            <div class="mt-0.5">{{ actionFeedback.rawError }}</div>
          </div>

          <!-- Close Button -->
          <div class="flex justify-end pt-2 border-t border-slate-100">
            <button
              @click="actionFeedback = null"
              class="px-4 py-2 text-xs font-bold text-white rounded-lg shadow-xs transition-colors cursor-pointer"
              :class="actionFeedback.success ? 'bg-emerald-700 hover:bg-emerald-800' : 'bg-slate-700 hover:bg-slate-800'"
            >
              Tutup & Kembali ke Pengujian Delegasi
            </button>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
