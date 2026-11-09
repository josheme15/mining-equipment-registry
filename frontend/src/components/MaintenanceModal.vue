<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/client'
import { formatDate, formatHours } from '../utils/format'
import BaseModal from './BaseModal.vue'
import AppIcon from './AppIcon.vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({ equipment: { type: Object, required: true } })
const emit = defineEmits(['close', 'registered'])

const history = ref([])
const loading = ref(true)
const notes = ref('')
const saving = ref(false)
const error = ref('')

// Si el horómetro no avanzó desde el último mantenimiento, registrar otro solo duplicaría
// el historial (el backend también lo rechaza).
const alreadyRegistered = computed(() => Number(props.equipment.hoursSinceMaintenance) <= 0)

async function loadHistory() {
  loading.value = true
  try {
    history.value = await api.listMaintenances(props.equipment.id)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function register() {
  error.value = ''
  saving.value = true
  try {
    await api.registerMaintenance(props.equipment.id, notes.value)
    notes.value = ''
    emit('registered')
    await loadHistory()
  } catch (err) {
    error.value = err.fieldErrors?.notes || err.message
  } finally {
    saving.value = false
  }
}

onMounted(loadHistory)
</script>

<template>
  <BaseModal :title="`Mantenimiento · ${equipment.code}`" @close="emit('close')">
    <div class="maintenance-summary">
      <StatusBadge :requires-maintenance="equipment.requiresMaintenance" />
      <span>Horómetro: <strong>{{ formatHours(equipment.hourMeter) }}</strong></span>
      <span>Desde el último: <strong>{{ formatHours(equipment.hoursSinceMaintenance) }}</strong>
        / {{ formatHours(equipment.type.maintenanceIntervalHours) }}</span>
      <span>Próximo: <strong>{{ formatHours(equipment.nextMaintenanceHourMeter) }}</strong></span>
      <span v-if="Number(equipment.overdueHours) > 0" class="overdue">
        Atrasado {{ formatHours(equipment.overdueHours) }}
      </span>
    </div>

    <form class="form" @submit.prevent="register">
      <label class="field">
        <span>Notas (opcional)</span>
        <textarea v-model="notes" :disabled="alreadyRegistered" rows="2" maxlength="500" placeholder="Cambio de aceite, filtros…"></textarea>
      </label>
      <p v-if="alreadyRegistered" class="alert alert-info">
        Ya se registró un mantenimiento a las {{ formatHours(equipment.hourMeter) }}.
        Actualiza el horómetro (botón Editar) antes de registrar otro.
      </p>
      <p v-else class="hint">
        Se registrará con el horómetro actual ({{ formatHours(equipment.hourMeter) }}) y se reiniciará el ciclo.
      </p>
      <p v-if="error" class="alert alert-error">{{ error }}</p>
      <div class="form-actions">
        <button type="submit" class="btn btn-primary" :disabled="saving || alreadyRegistered">
          <AppIcon name="wrench" /> {{ saving ? 'Registrando…' : 'Registrar mantenimiento' }}
        </button>
      </div>
    </form>

    <h3 class="section-title">Historial</h3>
    <p v-if="loading" class="muted">Cargando…</p>
    <p v-else-if="history.length === 0" class="muted">Este equipo aún no tiene mantenimientos registrados.</p>
    <ul v-else class="history">
      <li v-for="m in history" :key="m.id">
        <div class="history-head">
          <strong>{{ formatDate(m.performedAt) }}</strong>
          <span>a las {{ formatHours(m.hourMeterAt) }}</span>
        </div>
        <p v-if="m.notes" class="history-notes">{{ m.notes }}</p>
      </li>
    </ul>
  </BaseModal>
</template>
