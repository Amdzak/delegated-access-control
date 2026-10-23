<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/AppLayout.vue'
import { delegationApi } from '@/services/api'
import type { DelegationResponse } from '@/types'

const authStore = useAuthStore()
const router = useRouter()

// Delegation Stats State
const myDelegations = ref<DelegationResponse[]>([])
const isLoadingDelegations = ref(false)

const activeDelegationsCount = computed(() => {
  return myDelegations.value.filter((d) => d.status === 'ACTIVE').length
})

const totalDelegationsCount = computed(() => {
  return myDelegations.value.length
})

onMounted(async () => {
  await authStore.fetchProfile()
  isLoadingDelegations.value = true
  try {
    const res = await delegationApi.getMyDelegations()
    if (res.success && res.data) {
      myDelegations.value = res.data
    }
  } catch (err) {
    console.warn('Failed to fetch delegation stats for profile:', err)
  } finally {
    isLoadingDelegations.value = false
  }
})
</script>

<template>
  <AppLayout>
    <div class="space-y-6">
      <!-- Title & Action (Tanpa Background Color) -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-1">
        <h2 class="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
          Selamat datang, {{ authStore.user?.fullName }}
        </h2>

        <button
          @click="router.push('/delegations')"
          class="self-start sm:self-center px-3.5 py-2 bg-teal-700 hover:bg-teal-800 text-white font-semibold text-xs rounded-lg transition-all shadow-xs shrink-0"
        >
          Buka Menu Pendelegasian &rarr;
        </button>
      </div>

      <!-- Identity Cards Grid (Option C - Tanpa Email) -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <!-- Card 1: Data Akun -->
        <div class="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-1.5 text-xs sm:text-sm">
          <div>
            <span class="text-slate-500 font-medium">User ID:</span>
            <span class="font-mono font-bold text-slate-800 ml-1.5">{{ authStore.user?.userId }}</span>
          </div>
          <div>
            <span class="text-slate-500 font-medium">Username:</span>
            <span class="font-semibold text-slate-900 ml-1.5">{{ authStore.user?.username }}</span>
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
  </AppLayout>
</template>
