<script setup>
import { computed, reactive, ref } from 'vue'
import { api } from '../api/client'
import { formatHours } from '../utils/format'
import BaseModal from './BaseModal.vue'

const props = defineProps({
  types: { type: Array, required: true },
  equipment: { type: Object, default: null }, // null = crear
})
const emit = defineEmits(['close', 'saved'])

const isEdit = computed(() => props.equipment !== null)
const form = reactive({
  code: props.equipment?.code ?? '',
  typeId: props.equipment?.type.id ?? props.types[0]?.id ?? null,
  hourMeter: props.equipment?.hourMeter ?? 0,
})
const fieldErrors = ref({})
const generalError = ref('')
const saving = ref(false)

const selectedType = computed(() => props.types.find((t) => t.id === form.typeId))

async function submit() {
  fieldErrors.value = {}
  generalError.value = ''
  saving.value = true
  try {
    const hourMeter = form.hourMeter === '' ? null : form.hourMeter // campo vacío → "obligatorio"
    const payload = { code: form.code, typeId: form.typeId, hourMeter }
    const saved = isEdit.value
      ? await api.updateEquipment(props.equipment.id, payload)
      : await api.createEquipment(payload)
    emit('saved', saved)
  } catch (err) {
    fieldErrors.value = err.fieldErrors || {}
    generalError.value = Object.keys(fieldErrors.value).length ? '' : err.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseModal :title="isEdit ? `Editar ${equipment.code}` : 'Nuevo equipo'" @close="emit('close')">
    <form class="form" novalidate @submit.prevent="submit">
      <label class="field">
        <span>Código</span>
        <input v-model="form.code" type="text" maxlength="50" placeholder="CAM-001" required autofocus />
        <small v-if="fieldErrors.code" class="error">{{ fieldErrors.code }}</small>
      </label>

      <label class="field">
        <span>Tipo</span>
        <select v-model="form.typeId" required>
          <option v-for="t in types" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
        <small v-if="selectedType" class="hint">
          Mantenimiento cada {{ formatHours(selectedType.maintenanceIntervalHours) }}
        </small>
        <small v-if="fieldErrors.typeId" class="error">{{ fieldErrors.typeId }}</small>
      </label>

      <label class="field">
        <span>Horómetro (horas de uso)</span>
        <input v-model.number="form.hourMeter" type="number" min="0" step="0.1" required />
        <small v-if="isEdit" class="hint">No puede ser menor a {{ formatHours(equipment.hourMeter) }}</small>
        <small v-if="fieldErrors.hourMeter" class="error">{{ fieldErrors.hourMeter }}</small>
      </label>

      <p v-if="generalError" class="alert alert-error">{{ generalError }}</p>

      <div class="form-actions">
        <button type="button" class="btn btn-secondary" @click="emit('close')">Cancelar</button>
        <button type="submit" class="btn btn-primary" :disabled="saving">
          {{ saving ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear equipo' }}
        </button>
      </div>
    </form>
  </BaseModal>
</template>
