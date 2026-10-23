<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')

const handleLogin = async () => {
  if (!username.value || !password.value) return
  const success = await authStore.login(username.value, password.value)
  if (success) {
    router.push({ name: 'profile' })
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4 font-sans">
    <div class="w-full max-w-sm">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-8">
        <!-- Title: Pure LOGIN -->
        <h1 class="text-2xl font-bold text-slate-900 text-center tracking-tight mb-6">
          LOGIN
        </h1>

        <!-- Error Alert -->
        <div
          v-if="authStore.errorMessage"
          class="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg text-center"
        >
          {{ authStore.errorMessage }}
        </div>

        <form @submit.prevent="handleLogin" class="space-y-4">
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1.5">
              Username
            </label>
            <input
              v-model="username"
              type="text"
              required
              autocomplete="username"
              placeholder="Username"
              class="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-lg text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-700/15 focus:border-teal-700 transition-all"
            />
          </div>

          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1.5">
              Password
            </label>
            <input
              v-model="password"
              type="password"
              required
              autocomplete="current-password"
              placeholder="Password"
              class="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-lg text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-700/15 focus:border-teal-700 transition-all"
            />
          </div>

          <button
            type="submit"
            :disabled="authStore.isLoading"
            class="w-full mt-2 py-2.5 px-4 bg-teal-700 hover:bg-teal-800 active:bg-teal-900 text-white font-medium text-sm rounded-lg transition-all shadow-xs disabled:opacity-50"
          >
            <span v-if="authStore.isLoading">Memverifikasi...</span>
            <span v-else>Masuk</span>
          </button>
        </form>
      </div>
    </div>
  </div>
</template>
