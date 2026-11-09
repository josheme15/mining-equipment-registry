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
  hourMeter: props.equipment?.hourMeter ?? '',
})
const fieldErrors = ref({})
const generalError = ref('')
const saving = ref(false)

const selectedType = computed(() => props.types.find((t) => t.id === form.typeId))

const CODE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9 _-]*$/
const MAX_HOUR_METER = 999_999_999.9

// Mismas reglas que el backend (EquipmentRequest); el backend sigue validando de todos modos.
function validate() {
  const errors = {}
  const code = form.code.trim()
  if (!code) errors.code = 'El código es obligatorio'
  else if (!CODE_PATTERN.test(code))
    errors.code = 'El código solo admite letras, números, espacios, guiones y guiones bajos'

  if (!form.typeId) errors.typeId = 'El tipo de equipo es obligatorio'

  const h = form.hourMeter // v-model.number: '' si está vacío o no es un número válido
  if (h === '' || h === null || Number.isNaN(h)) errors.hourMeter = 'El horómetro es obligatorio'
  else if (h <= 0) errors.hourMeter = 'El horómetro debe ser mayor a 0'
  else if (h > MAX_HOUR_METER) errors.hourMeter = 'El horómetro es demasiado alto'
  else if (!/^\d+(\.\d)?$/.test(String(h))) errors.hourMeter = 'El horómetro admite como máximo 1 decimal'
  else if (isEdit.value && h < Number(props.equipment.hourMeter))
    errors.hourMeter = `El horómetro no puede disminuir (actual: ${formatHours(props.equipment.hourMeter)})`
  return errors
}

async function submit() {
  generalError.value = ''
  fieldErrors.value = validate()
  if (Object.keys(fieldErrors.value).length) return
  saving.value = true
  try {
    const payload = { code: form.code.trim(), typeId: form.typeId, hourMeter: form.hourMeter }
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
        <input v-model.number="form.hourMeter" type="number" min="0.1" step="0.1" placeholder="Ej. 1250.5" required />
        <small v-if="!isEdit" class="hint">Mayor a 0, hasta 1 decimal (ej. 1.2)</small>
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
