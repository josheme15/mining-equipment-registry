<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

// Diálogo de confirmación propio (reemplaza window.confirm). Se monta sobre cualquier modal.
const props = defineProps({
  title: { type: String, required: true },
  message: { type: String, required: true },
  confirmText: { type: String, default: 'Confirmar' },
  cancelText: { type: String, default: 'Cancelar' },
  danger: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
})
const emit = defineEmits(['confirm', 'cancel'])

const cancelButton = ref(null)

// Fase de captura + stopImmediatePropagation: el Escape cierra solo este diálogo,
// no el modal que está debajo.
function onKey(e) {
  if (e.key !== 'Escape') return
  e.stopImmediatePropagation()
  if (!props.busy) emit('cancel')
}
onMounted(() => {
  window.addEventListener('keydown', onKey, true)
  cancelButton.value?.focus() // foco en la opción segura
})
onUnmounted(() => window.removeEventListener('keydown', onKey, true))
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop confirm-backdrop">
      <div class="modal confirm" role="alertdialog" aria-modal="true" :aria-label="title">
        <div class="modal-body">
          <h2 class="confirm-title">{{ title }}</h2>
          <p class="confirm-message">{{ message }}</p>
          <div class="form-actions">
            <button ref="cancelButton" type="button" class="btn btn-secondary" :disabled="busy" @click="emit('cancel')">
              {{ cancelText }}
            </button>
            <button
              type="button"
              class="btn"
              :class="danger ? 'btn-danger-solid' : 'btn-primary'"
              :disabled="busy"
              @click="emit('confirm')"
            >
              {{ busy ? 'Procesando…' : confirmText }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
