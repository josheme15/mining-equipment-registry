<script setup>
import { onMounted, onUnmounted } from 'vue'

defineProps({ title: { type: String, required: true } })
const emit = defineEmits(['close'])

const onKey = (e) => e.key === 'Escape' && emit('close')

// Solo se cierra si el clic EMPIEZA y TERMINA en el fondo. Con @click.self a secas, seleccionar
// texto de un input (o elegir una opción/autocompletado) y soltar el mouse fuera del modal
// genera un "click" sobre el fondo y lo cerraba perdiendo lo escrito.
let pressedOnBackdrop = false
const onBackdropDown = (e) => (pressedOnBackdrop = e.target === e.currentTarget)
const onBackdropUp = (e) => {
  if (pressedOnBackdrop && e.target === e.currentTarget) emit('close')
  pressedOnBackdrop = false
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="modal-backdrop" @mousedown="onBackdropDown" @mouseup="onBackdropUp">
    <div class="modal" role="dialog" aria-modal="true" :aria-label="title">
      <header class="modal-header">
        <h2>{{ title }}</h2>
        <button class="btn-icon" type="button" aria-label="Cerrar" @click="emit('close')">✕</button>
      </header>
      <div class="modal-body">
        <slot />
      </div>
    </div>
  </div>
</template>
