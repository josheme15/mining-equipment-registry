<script setup>
import { onMounted, ref } from 'vue'
import { getHealth, API_URL } from './api/client'

// 'checking' | 'up' | 'down'
const backendStatus = ref('checking')

onMounted(async () => {
  try {
    const health = await getHealth()
    backendStatus.value = health.status === 'UP' ? 'up' : 'down'
  } catch {
    backendStatus.value = 'down'
  }
})
</script>

<template>
  <main class="container">
    <h1>Registro de Equipos Mineros</h1>
    <p class="subtitle">Control de horómetros y mantenimiento</p>

    <div class="status" :class="backendStatus">
      <span class="dot"></span>
      <span v-if="backendStatus === 'checking'">Conectando con el backend…</span>
      <span v-else-if="backendStatus === 'up'">Backend conectado</span>
      <span v-else>Backend no disponible (puede estar despertando, espera ~1 min)</span>
    </div>
    <p class="api-url">API: {{ API_URL }}</p>
  </main>
</template>
