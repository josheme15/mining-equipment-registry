<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from './api/client'
import { formatHours } from './utils/format'
import EquipmentFormModal from './components/EquipmentFormModal.vue'
import MaintenanceModal from './components/MaintenanceModal.vue'
import StatusBadge from './components/StatusBadge.vue'

const equipments = ref([])
const types = ref([])
const loading = ref(true)
const loadError = ref('')
const waitedSeconds = ref(0)
const notice = ref('')

const filter = ref('all') // 'all' | 'due'
const search = ref('')

// Modales: formulario (crear/editar) y mantenimiento
const formOpen = ref(false)
const editing = ref(null)
const maintenanceId = ref(null)
const maintenanceEquipment = computed(() => equipments.value.find((e) => e.id === maintenanceId.value) ?? null)

const dueCount = computed(() => equipments.value.filter((e) => e.requiresMaintenance).length)
const visibleEquipments = computed(() => {
  const term = search.value.trim().toUpperCase()
  return equipments.value.filter(
    (e) => (filter.value === 'all' || e.requiresMaintenance) && (!term || e.code.includes(term)),
  )
})

const progress = (e) =>
  Math.min(100, (Number(e.hoursSinceMaintenance) / e.type.maintenanceIntervalHours) * 100)

function flash(message) {
  notice.value = message
  setTimeout(() => (notice.value = ''), 3000)
}

// El plan gratuito de Render duerme el backend; al despertar, las primeras peticiones
// pueden fallar o colgarse. Se reintenta solo los errores de conexión durante ~2 min.
const WAKE_UP_LIMIT_MS = 120_000
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function loadAll() {
  loading.value = true
  loadError.value = ''
  const start = Date.now()
  const timer = setInterval(() => (waitedSeconds.value = Math.round((Date.now() - start) / 1000)), 1000)
  try {
    for (;;) {
      try {
        const [eqs, tps] = await Promise.all([api.listEquipments(), api.listTypes()])
        equipments.value = eqs
        types.value = tps
        return
      } catch (err) {
        if (err.status !== 0 || Date.now() - start > WAKE_UP_LIMIT_MS) {
          loadError.value = err.message
          return
        }
        await sleep(3000)
      }
    }
  } finally {
    clearInterval(timer)
    waitedSeconds.value = 0
    loading.value = false
  }
}

async function reloadEquipments() {
  equipments.value = await api.listEquipments()
}

function openCreate() {
  editing.value = null
  formOpen.value = true
}

function openEdit(equipment) {
  editing.value = equipment
  formOpen.value = true
}

async function onSaved(saved) {
  const wasEdit = editing.value !== null
  formOpen.value = false
  await reloadEquipments()
  flash(wasEdit ? `Equipo ${saved.code} actualizado` : `Equipo ${saved.code} creado`)
}

async function remove(equipment) {
  if (!confirm(`¿Eliminar el equipo ${equipment.code}? También se borrará su historial de mantenimientos.`)) return
  try {
    await api.deleteEquipment(equipment.id)
    await reloadEquipments()
    flash(`Equipo ${equipment.code} eliminado`)
  } catch (err) {
    alert(err.message)
  }
}

async function onMaintenanceRegistered() {
  await reloadEquipments()
  flash('Mantenimiento registrado: ciclo reiniciado')
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="container topbar-inner">
        <div>
          <h1>⛏️ Registro de Equipos Mineros</h1>
          <p class="subtitle">Control de horómetros y mantenimiento</p>
        </div>
        <button class="btn btn-primary" :disabled="loading || !!loadError" @click="openCreate">+ Nuevo equipo</button>
      </div>
    </header>

    <main class="container">
      <p v-if="notice" class="alert alert-success">{{ notice }}</p>

      <div v-if="loading" class="card empty">
        Cargando equipos…
        <p v-if="waitedSeconds >= 5" class="hint">
          Despertando el servidor (plan gratuito, puede tardar ~1 min)… {{ waitedSeconds }} s
        </p>
      </div>

      <div v-else-if="loadError" class="card empty">
        <p class="alert alert-error">{{ loadError }}</p>
        <button class="btn btn-secondary" @click="loadAll">Reintentar</button>
      </div>

      <template v-else>
        <section class="stats">
          <button class="stat card" :class="{ active: filter === 'all' }" @click="filter = 'all'">
            <span class="stat-value">{{ equipments.length }}</span>
            <span class="stat-label">Equipos</span>
          </button>
          <button class="stat card" :class="{ active: filter === 'due' }" @click="filter = 'due'">
            <span class="stat-value danger">{{ dueCount }}</span>
            <span class="stat-label">Requieren mantenimiento</span>
          </button>
          <div class="stat card">
            <span class="stat-value ok">{{ equipments.length - dueCount }}</span>
            <span class="stat-label">Operativos</span>
          </div>
        </section>

        <section class="card">
          <div class="toolbar">
            <input v-model="search" type="search" placeholder="Buscar por código…" />
            <span class="muted">{{ filter === 'due' ? 'Mostrando solo los que requieren mantenimiento' : '' }}</span>
          </div>

          <p v-if="equipments.length === 0" class="empty">
            Aún no hay equipos. Crea el primero con <strong>+ Nuevo equipo</strong>.
          </p>
          <p v-else-if="visibleEquipments.length === 0" class="empty">Ningún equipo coincide con el filtro.</p>

          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Tipo</th>
                  <th class="num">Horómetro</th>
                  <th>Ciclo de mantenimiento</th>
                  <th>Estado</th>
                  <th class="actions-col">Acciones</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in visibleEquipments" :key="e.id" :class="{ 'row-due': e.requiresMaintenance }">
                  <td><strong>{{ e.code }}</strong></td>
                  <td>{{ e.type.name }}</td>
                  <td class="num">{{ formatHours(e.hourMeter) }}</td>
                  <td>
                    <div class="progress" :title="`${formatHours(e.hoursSinceMaintenance)} de ${formatHours(e.type.maintenanceIntervalHours)}`">
                      <div class="progress-bar" :class="{ due: e.requiresMaintenance }" :style="{ width: progress(e) + '%' }"></div>
                    </div>
                    <small class="muted">
                      {{ e.requiresMaintenance ? 'Intervalo alcanzado' : `Faltan ${formatHours(e.hoursUntilMaintenance)}` }}
                    </small>
                  </td>
                  <td><StatusBadge :requires-maintenance="e.requiresMaintenance" /></td>
                  <td>
                    <div class="actions">
                      <button class="btn btn-small" :class="e.requiresMaintenance ? 'btn-warning' : 'btn-secondary'" @click="maintenanceId = e.id">
                        🔧 Mantenimiento
                      </button>
                      <button class="btn btn-small btn-secondary" @click="openEdit(e)">Editar</button>
                      <button class="btn btn-small btn-danger" @click="remove(e)">Eliminar</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="legend muted">
          Intervalos:
          <span v-for="t in types" :key="t.id">{{ t.name }} cada {{ formatHours(t.maintenanceIntervalHours) }}</span>
        </section>
      </template>
    </main>

    <EquipmentFormModal
      v-if="formOpen"
      :types="types"
      :equipment="editing"
      @close="formOpen = false"
      @saved="onSaved"
    />
    <MaintenanceModal
      v-if="maintenanceEquipment"
      :equipment="maintenanceEquipment"
      @close="maintenanceId = null"
      @registered="onMaintenanceRegistered"
    />
  </div>
</template>
