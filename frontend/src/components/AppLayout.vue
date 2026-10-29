<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter, useRoute } from 'vue-router'
import { delegationApi } from '@/services/api'
import type { NetworkStatusResponse } from '@/types'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const handleLogout = () => {
  authStore.logout()
  router.push({ name: 'login' })
}

const navItems = computed(() => {
  const items = [
    {
      name: 'Profil',
      route: '/profile',
    },
  ]

  // Menu Pengujian Delegasi: Hanya untuk Admin ATAU user yang memiliki delegasi aktif dari delegator
  if (authStore.isAdmin || authStore.hasActiveDelegation) {
    items.push({
      name: 'Pengujian delegasi',
      route: '/simrs',
    })
  }

  // Menu Pendelegasian Wewenang eksklusif hanya untuk peran Dokter
  if (authStore.isDoctor) {
    items.push({
      name: 'Pendelegasian wewenang',
      route: '/delegations',
    })
  }

  // Menu Uji Akses Kontrak eksklusif hanya untuk peran Admin (Simulator Pengujian Smart Contract)
  if (authStore.isAdmin) {
    items.push({
      name: 'Uji Akses Kontrak',
      route: '/test-access',
    })
  }

  return items
})

// Realtime Fabric Network Status
const networkStatus = ref<NetworkStatusResponse | null>(null)
const isCheckingNetwork = ref(false)
let statusInterval: ReturnType<typeof setInterval> | null = null

const checkNetworkStatus = async () => {
  isCheckingNetwork.value = true
  try {
    const res = await delegationApi.getNetworkStatus()
    if (res.success && res.data) {
      networkStatus.value = res.data
    }
  } catch (err) {
    console.warn('Failed to query network status:', err)
  } finally {
    isCheckingNetwork.value = false
  }
}

onMounted(() => {
  checkNetworkStatus()
  authStore.checkActiveDelegations()
  // Poll every 30 seconds for gentle, non-intrusive updates
  statusInterval = setInterval(() => {
    checkNetworkStatus()
    authStore.checkActiveDelegations()
  }, 30000)
})

onUnmounted(() => {
  if (statusInterval) {
    clearInterval(statusInterval)
  }
})
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col font-sans">
    <!-- Top Header & Navbar -->
    <header class="bg-white border-b border-slate-200 sticky top-0 z-30 shadow-xs">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        <!-- Navigation Menu in Top Navbar -->
        <nav class="flex items-center space-x-1 sm:space-x-2">
          <router-link
            v-for="item in navItems"
            :key="item.route"
            :to="item.route"
            class="px-3.5 py-2 rounded-lg text-xs sm:text-sm font-medium transition-all"
            :class="[
              route.path.startsWith(item.route)
                ? 'bg-teal-50 text-teal-800 font-semibold border border-teal-200 shadow-xs'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-transparent',
            ]"
          >
            {{ item.name }}
          </router-link>
        </nav>

        <!-- Right: Status Pill, User Information & Logout -->
        <div class="flex items-center space-x-3 shrink-0">
          <!-- Realtime Blockchain Connection Pill -->
          <div
            @click="checkNetworkStatus"
            class="group relative cursor-pointer px-2.5 py-1 rounded-full text-[11px] font-medium border transition-all flex items-center space-x-1.5 select-none"
            :class="[
              networkStatus?.connected
                ? 'bg-emerald-50 text-emerald-800 border-emerald-200 hover:bg-emerald-100/70'
                : 'bg-rose-50 text-rose-800 border-rose-200 hover:bg-rose-100/70'
            ]"
            :title="networkStatus?.connected ? 'Klik untuk refresh status' : 'Klik untuk mencoba menghubungkan ulang'"
          >
            <span
              class="w-2 h-2 rounded-full"
              :class="[
                networkStatus?.connected
                  ? 'bg-emerald-500 animate-pulse'
                  : 'bg-rose-500'
              ]"
            ></span>
            <span class="font-semibold">
              {{ isCheckingNetwork ? 'Memeriksa...' : (networkStatus?.connected ? 'Fabric Online' : 'Fabric Offline') }}
            </span>

            <!-- Tooltip details on hover -->
            <div
              class="absolute right-0 top-full mt-2 hidden group-hover:block z-50 w-64 p-3 bg-slate-900 text-slate-100 text-[11px] rounded-xl shadow-xl border border-slate-700 pointer-events-none"
            >
              <div class="font-bold text-white border-b border-slate-700 pb-1 mb-1.5 flex items-center justify-between">
                <span>gRPC Connection State</span>
                <span class="font-mono text-[10px] text-teal-400">{{ networkStatus?.stateName || 'OFFLINE' }}</span>
              </div>
              <div class="space-y-1 text-slate-300">
                <div>Channel: <span class="font-mono font-bold text-white">{{ networkStatus?.channel || 'z-dac' }}</span></div>
                <div>Chaincode: <span class="font-mono font-bold text-white">{{ networkStatus?.chaincode || 'dac' }}</span></div>
                <div>Peer: <span class="font-mono text-slate-400">{{ networkStatus?.peerEndpoint || 'localhost:7051' }}</span></div>
              </div>
            </div>
          </div>

          <!-- User Card -->
          <div class="flex items-center space-x-2.5 bg-slate-100/80 px-3 py-1.5 rounded-lg border border-slate-200">
            <div class="w-7 h-7 rounded bg-teal-700 text-white flex items-center justify-center font-bold text-xs">
              {{ authStore.user?.fullName?.charAt(0) || 'U' }}
            </div>
            <div class="text-left hidden sm:block">
              <div class="text-xs font-semibold text-slate-800 leading-tight">
                {{ authStore.user?.fullName || 'Pengguna' }}
              </div>
              <div class="text-[10px] text-teal-800 font-medium flex items-center space-x-1">
                <span>{{ authStore.user?.roleName }}</span>
                <span>&bull;</span>
                <span class="text-slate-500">{{ authStore.user?.unitCabang }}</span>
              </div>
            </div>
          </div>

          <button
            @click="handleLogout"
            class="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-red-700 hover:bg-red-50 rounded-lg transition-colors border border-slate-200 hover:border-red-200"
            title="Keluar dari sistem"
          >
            Keluar
          </button>
        </div>
      </div>
    </header>

    <!-- Main Content Container (Full Width without Sidebar) -->
    <main class="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <slot />
    </main>
  </div>
</template>
