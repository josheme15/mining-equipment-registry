<script setup>
import { computed } from 'vue'
import { availablePageSizes, pageNumbers } from '../utils/pagination'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  total: { type: Number, required: true },
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
})
const emit = defineEmits(['update:page', 'update:pageSize'])

const sizes = computed(() => availablePageSizes(props.total))
const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const from = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.pageSize + 1))
const to = computed(() => Math.min(props.page * props.pageSize, props.total))
const pages = computed(() => pageNumbers(props.page, totalPages.value))

const goTo = (p) => emit('update:page', Math.min(Math.max(1, p), totalPages.value))
</script>

<template>
  <nav class="pagination" aria-label="Paginación">
    <label class="page-size">
      Mostrar
      <select :value="pageSize" @change="emit('update:pageSize', Number($event.target.value))">
        <option v-for="s in sizes" :key="s" :value="s">{{ s }}</option>
      </select>
      por página
    </label>

    <span class="page-range muted">{{ from }}–{{ to }} de {{ total }}</span>

    <div v-if="totalPages > 1" class="page-buttons">
      <button class="page-btn" :disabled="page === 1" aria-label="Página anterior" @click="goTo(page - 1)">
        <AppIcon name="chevron-left" size="14" />
      </button>
      <template v-for="(p, i) in pages" :key="`${p}-${i}`">
        <span v-if="p === '…'" class="page-gap">…</span>
        <button
          v-else
          class="page-btn"
          :class="{ current: p === page }"
          :aria-current="p === page ? 'page' : undefined"
          @click="goTo(p)"
        >
          {{ p }}
        </button>
      </template>
      <button class="page-btn" :disabled="page === totalPages" aria-label="Página siguiente" @click="goTo(page + 1)">
        <AppIcon name="chevron-right" size="14" />
      </button>
    </div>
  </nav>
</template>
