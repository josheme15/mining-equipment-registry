<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from './api/client'
import { formatHours } from './utils/format'
import EquipmentFormModal from './components/EquipmentFormModal.vue'
import MaintenanceModal from './components/MaintenanceModal.vue'
import StatusBadge from './components/StatusBadge.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import AppIcon from './components/AppIcon.vue'
import TablePagination from './components/TablePagination.vue'
import { availablePageSizes } from './utils/pagination'

const equipments = ref([])
const types = ref([])
const loading = ref(true)
const loadError = ref('')
const waitedSeconds = ref(0)
const notice = ref('')
const noticeType = ref('success') // 'success' | 'error'
const pendingDelete = ref(null) // equipo a eliminar (abre el diálogo de confirmación)
const deleting = ref(false)

// Filtros combinables. `filter` (estado) lo comparten las tarjetas y el select de Estado.
const filter = ref('all') // 'all' | 'due' (requieren mantenimiento) | 'ok' (operativos)
const typeFilter = ref('') // '' = todos; si no, id del tipo
const search = ref('')
const hasFilters = computed(() => filter.value !== 'all' || typeFilter.value !== '' || search.value.trim() !== '')

function clearFilters() {
  filter.value = 'all'
  typeFilter.value = ''
  search.value = ''
}

// Modales: formulario (crear/editar) y mantenimiento
const formOpen = ref(false)
const editing = ref(null)
const maintenanceId = ref(null)
const maintenanceEquipment = computed(() => equipments.value.find((e) => e.id === maintenanceId.value) ?? null)

const dueCount = computed(() => equipments.value.filter((e) => e.requiresMaintenance).length)
const visibleEquipments = computed(() => {
  const term = search.value.trim().toUpperCase()
  return equipments.value.filter(
    (e) =>
      (filter.value === 'all' || (filter.value === 'due') === e.requiresMaintenance) &&
      (typeFilter.value === '' || e.type.id === typeFilter.value) &&
      (!term || e.code.includes(term)),
  )
})

// Paginación (en el cliente: la API devuelve la lista completa, suficiente para una flota).
// preferredPageSize es lo que eligió el usuario; si con los registros actuales esa opción no
// aparece (p. ej. 25 con solo 7 equipos), se usa la mayor disponible, que ya los muestra todos.
const page = ref(1)
const preferredPageSize = ref(10)
const pageSize = computed(() => {
  const sizes = availablePageSizes(visibleEquipments.value.length)
  return sizes.includes(preferredPageSize.value) ? preferredPageSize.value : sizes[sizes.length - 1]
})
const totalPages = computed(() => Math.max(1, Math.ceil(visibleEquipments.value.length / pageSize.value)))
const pagedEquipments = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return visibleEquipments.value.slice(start, start + pageSize.value)
})
// Al cambiar filtro, búsqueda o tamaño se vuelve a la primera página.
watch([filter, typeFilter, search, preferredPageSize], () => (page.value = 1))
// Si al eliminar quedan menos páginas, no quedarse en una página vacía.
watch(totalPages, (n) => {
  if (page.value > n) page.value = n
})

const progress = (e) =>
  Math.min(100, (Number(e.hoursSinceMaintenance) / e.type.maintenanceIntervalHours) * 100)

let flashTimer
function flash(message, type = 'success') {
  notice.value = message
  noticeType.value = type
  clearTimeout(flashTimer)
  flashTimer = setTimeout(() => (notice.value = ''), type === 'error' ? 6000 : 3000)
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

/**
 * Tras una operación que YA se guardó, recarga la lista (y los tipos, que traen el próximo
 * código). Si la recarga falla, el aviso lo dice sin dar a entender que la operación falló.
 */
async function refreshAfter(successMessage) {
  try {
    const [eqs, tps] = await Promise.all([api.listEquipments(), api.listTypes()])
    equipments.value = eqs
    types.value = tps
    flash(successMessage)
  } catch {
    flash(`${successMessage}, pero no se pudo actualizar la lista. Recarga la página.`, 'error')
  }
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
  const previousCode = editing.value?.code
  formOpen.value = false
  let message = `Equipo ${saved.code} actualizado`
  if (!previousCode) message = `Equipo ${saved.code} creado`
  else if (previousCode !== saved.code) message = `Equipo ${previousCode} actualizado: ahora es ${saved.code}`
  await refreshAfter(message)
}

function remove(equipment) {
  pendingDelete.value = equipment
}

async function confirmDelete() {
  const equipment = pendingDelete.value
  deleting.value = true
  try {
    await api.deleteEquipment(equipment.id)
  } catch (err) {
    flash(`No se pudo eliminar ${equipment.code}: ${err.message}`, 'error')
    return
  } finally {
    deleting.value = false
    pendingDelete.value = null
  }
  await refreshAfter(`Equipo ${equipment.code} eliminado`)
}

async function onMaintenanceRegistered() {
  await refreshAfter('Mantenimiento registrado: ciclo reiniciado')
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="container topbar-inner">
        <div>
          <h1>⛏️ Registro de Equipos Mineros v2</h1>
          <p class="subtitle">Control de horómetros y mantenimiento</p>
        </div>
        <button class="btn btn-primary" :disabled="loading || !!loadError" @click="openCreate">
          <AppIcon name="plus" /> Nuevo equipo
        </button>
      </div>
    </header>

    <main class="container">
      <p v-if="notice" class="alert" :class="noticeType === 'error' ? 'alert-error' : 'alert-success'">{{ notice }}</p>

      <div v-if="loading" class="card empty">
        Cargando equipos…
        <p v-if="waitedSeconds >= 5" class="hint">
          Despertando el servidor (plan gratuito, puede tardar ~1 min)… {{ waitedSeconds }} s
        </p>
      </div>

      <div v-else-if="loadError" class="card empty">
        <p class="alert alert-error">{{ loadError }}</p>
        <button class="btn btn-secondary" @click="loadAll"><AppIcon name="refresh" /> Reintentar</button>
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
          <button class="stat card" :class="{ active: filter === 'ok' }" @click="filter = 'ok'">
            <span class="stat-value ok">{{ equipments.length - dueCount }}</span>
            <span class="stat-label">Operativos</span>
          </button>
        </section>

        <section class="card">
          <div class="toolbar">
            <input v-model="search" type="search" placeholder="Buscar por código…" aria-label="Buscar por código" />
            <label class="filter-select">
              <span>Tipo</span>
              <select v-model="typeFilter">
                <option value="">Todos</option>
                <option v-for="t in types" :key="t.id" :value="t.id">{{ t.name }}</option>
              </select>
            </label>
            <label class="filter-select">
              <span>Estado</span>
              <select v-model="filter">
                <option value="all">Todos</option>
                <option value="due">Requiere mantenimiento</option>
                <option value="ok">Operativo</option>
              </select>
            </label>
            <button v-if="hasFilters" class="btn btn-small btn-secondary" @click="clearFilters">
              <AppIcon name="x" size="14" /> Limpiar filtros
            </button>
          </div>

          <p v-if="equipments.length === 0" class="empty">
            Aún no hay equipos. Crea el primero con <strong>+ Nuevo equipo</strong>.
          </p>
          <div v-else-if="visibleEquipments.length === 0" class="empty">
            <p>Ningún equipo coincide con los filtros.</p>
            <button class="btn btn-small btn-secondary" @click="clearFilters">
              <AppIcon name="x" size="14" /> Limpiar filtros
            </button>
          </div>

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
                <tr v-for="e in pagedEquipments" :key="e.id" :class="{ 'row-due': e.requiresMaintenance }">
                  <td><strong>{{ e.code }}</strong></td>
                  <td>{{ e.type.name }}</td>
                  <td class="num">{{ formatHours(e.hourMeter) }}</td>
                  <td>
                    <div class="progress" :title="`${formatHours(e.hoursSinceMaintenance)} de ${formatHours(e.type.maintenanceIntervalHours)}`">
                      <div class="progress-bar" :class="{ due: e.requiresMaintenance }" :style="{ width: progress(e) + '%' }"></div>
                    </div>
                    <small v-if="!e.requiresMaintenance" class="muted">Faltan {{ formatHours(e.hoursUntilMaintenance) }}</small>
                    <small v-else class="overdue">
                      {{ Number(e.overdueHours) > 0 ? `Atrasado ${formatHours(e.overdueHours)}` : 'Le toca ahora' }}
                    </small>
                    <small class="cycle-detail">
                      Último: {{ Number(e.lastMaintenanceHourMeter) > 0 ? formatHours(e.lastMaintenanceHourMeter) : 'sin registro' }}
                      · Próximo: {{ formatHours(e.nextMaintenanceHourMeter) }}
                    </small>
                  </td>
                  <td><StatusBadge :requires-maintenance="e.requiresMaintenance" /></td>
                  <td>
                    <div class="actions">
                      <button class="btn btn-small" :class="e.requiresMaintenance ? 'btn-warning' : 'btn-info'" @click="maintenanceId = e.id">
                        <AppIcon name="wrench" size="14" /> Mantenimiento
                      </button>
                      <button class="btn btn-small btn-edit" @click="openEdit(e)"><AppIcon name="pencil" size="14" /> Editar</button>
                      <button class="btn btn-small btn-danger" @click="remove(e)"><AppIcon name="trash" size="14" /> Eliminar</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <TablePagination
            v-if="visibleEquipments.length > 0"
            v-model:page="page"
            :page-size="pageSize"
            :total="visibleEquipments.length"
            @update:page-size="preferredPageSize = $event"
          />
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
    <ConfirmDialog
      v-if="pendingDelete"
      title="¿Eliminar equipo?"
      :message="`Se borrará ${pendingDelete.code} y su historial.`"
      confirm-text="Eliminar"
      confirm-icon="trash"
      danger
      :busy="deleting"
      @confirm="confirmDelete"
      @cancel="pendingDelete = null"
    />
  </div>
</template>
