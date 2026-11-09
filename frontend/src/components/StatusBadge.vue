<script setup>
defineProps({ requiresMaintenance: { type: Boolean, required: true } })
</script>

<template>
  <span class="badge" :class="requiresMaintenance ? 'badge-danger' : 'badge-ok'">
    {{ requiresMaintenance ? 'Requiere mantenimiento' : 'Operativo' }}
  </span>
</template>
