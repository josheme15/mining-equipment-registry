<script setup>
import { computed, reactive, ref } from 'vue'
import { api } from '../api/client'
import { formatHours } from '../utils/format'
import BaseModal from './BaseModal.vue'
import ConfirmDialog from './ConfirmDialog.vue'

const props = defineProps({
  types: { type: Array, required: true },
  equipment: { type: Object, default: null }, // null = crear
})
const emit = defineEmits(['close', 'saved'])

const isEdit = computed(() => props.equipment !== null)
const form = reactive({
  code: props.equipment?.code ?? '',
  typeId: props.equipment?.type.id ?? props.types[0]?.id ?? null,
  hourMeter: props.equipment ? String(props.equipment.hourMeter) : '', // texto: se filtra al escribir
})
// Copia inicial para saber si hay cambios sin guardar.
const initial = { ...form }
const isDirty = computed(() => Object.keys(initial).some((k) => String(form[k] ?? '') !== String(initial[k] ?? '')))

const fieldErrors = ref({}) // errores devueltos por el backend
const touched = reactive({ code: false, typeId: false, hourMeter: false })
const confirmingExit = ref(false)
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

  const raw = form.hourMeter.trim()
  const h = Number(raw)
  if (raw === '' || raw === '.') errors.hourMeter = 'El horómetro es obligatorio'
  else if (!/^\d*\.?\d?$/.test(raw) || Number.isNaN(h)) errors.hourMeter = 'El horómetro admite como máximo 1 decimal'
  else if (h <= 0) errors.hourMeter = 'El horómetro debe ser mayor a 0'
  else if (h > MAX_HOUR_METER) errors.hourMeter = 'El horómetro es demasiado alto'
  else if (isEdit.value && h < Number(props.equipment.hourMeter))
    errors.hourMeter = `El horómetro no puede disminuir (actual: ${formatHours(props.equipment.hourMeter)})`
  return errors
}

// Validación en vivo: el botón de guardar se habilita solo si todo es válido.
const clientErrors = computed(() => validate())
const isValid = computed(() => Object.keys(clientErrors.value).length === 0)

// Un error de validación se muestra cuando el usuario ya pasó por el campo;
// si no, se muestra el que haya devuelto el backend.
const errorFor = (field) => (touched[field] && clientErrors.value[field]) || fieldErrors.value[field] || ''

function onFieldInput(field) {
  delete fieldErrors.value[field] // el error del backend ya no aplica al valor nuevo
}

// Filtra lo que se escribe o pega en el horómetro: solo dígitos y un punto decimal
// (la coma se toma como punto), máximo 9 enteros y 1 decimal. Sin signo ni notación "e".
function sanitizeHourMeter(value) {
  const clean = value.replace(/,/g, '.').replace(/[^\d.]/g, '')
  const dot = clean.indexOf('.')
  if (dot === -1) return clean.slice(0, 9)
  const intPart = clean.slice(0, dot).slice(0, 9)
  const decimal = clean.slice(dot + 1).replace(/\./g, '').slice(0, 1)
  return `${intPart}.${decimal}`
}

function onHourMeterInput(event) {
  const clean = sanitizeHourMeter(event.target.value)
  event.target.value = clean // corrige el campo aunque el valor del modelo no cambie
  form.hourMeter = clean
  onFieldInput('hourMeter')
}

// Cancelar, ✕, Escape o clic en el fondo: si hay cambios, pide confirmación.
function requestClose() {
  if (saving.value || confirmingExit.value) return
  if (isDirty.value) confirmingExit.value = true
  else emit('close')
}

async function submit() {
  generalError.value = ''
  Object.keys(touched).forEach((k) => (touched[k] = true))
  if (!isValid.value) return
  saving.value = true
  try {
    const payload = { code: form.code.trim(), typeId: form.typeId, hourMeter: Number(form.hourMeter) }
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
  <BaseModal :title="isEdit ? `Editar ${equipment.code}` : 'Nuevo equipo'" @close="requestClose">
    <form class="form" novalidate @submit.prevent="submit">
      <label class="field">
        <span>Código</span>
        <input
          v-model="form.code"
          type="text"
          maxlength="50"
          placeholder="CAM-001"
          required
          autofocus
          :aria-invalid="!!errorFor('code')"
          @input="onFieldInput('code')"
          @blur="touched.code = true"
        />
        <small v-if="errorFor('code')" class="error">{{ errorFor('code') }}</small>
      </label>

      <label class="field">
        <span>Tipo</span>
        <select v-model="form.typeId" required @change="onFieldInput('typeId'); touched.typeId = true">
          <option v-for="t in types" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
        <small v-if="selectedType" class="hint">
          Mantenimiento cada {{ formatHours(selectedType.maintenanceIntervalHours) }}
        </small>
        <small v-if="errorFor('typeId')" class="error">{{ errorFor('typeId') }}</small>
      </label>

      <label class="field">
        <span>Horómetro (horas de uso)</span>
        <input
          :value="form.hourMeter"
          type="text"
          inputmode="decimal"
          autocomplete="off"
          placeholder="Ej. 1250.5"
          required
          :aria-invalid="!!errorFor('hourMeter')"
          @input="onHourMeterInput"
          @blur="touched.hourMeter = true"
        />
        <small v-if="!isEdit" class="hint">Mayor a 0, hasta 1 decimal (ej. 1.2)</small>
        <small v-if="isEdit" class="hint">No puede ser menor a {{ formatHours(equipment.hourMeter) }}</small>
        <small v-if="errorFor('hourMeter')" class="error">{{ errorFor('hourMeter') }}</small>
      </label>

      <p v-if="generalError" class="alert alert-error">{{ generalError }}</p>

      <div class="form-actions">
        <button type="button" class="btn btn-secondary" :disabled="saving" @click="requestClose">Cancelar</button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="saving || !isValid"
          :title="!isValid ? 'Completa correctamente todos los campos' : undefined"
        >
          {{ saving ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear equipo' }}
        </button>
      </div>
    </form>

    <ConfirmDialog
      v-if="confirmingExit"
      title="¿Salir sin guardar?"
      :message="isEdit ? `Los cambios en ${equipment.code} se perderán.` : 'Los datos del nuevo equipo se perderán.'"
      confirm-text="Salir sin guardar"
      cancel-text="Seguir editando"
      danger
      @confirm="emit('close')"
      @cancel="confirmingExit = false"
    />
  </BaseModal>
</template>
