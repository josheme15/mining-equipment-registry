export const PAGE_SIZES = [5, 10, 25, 50, 100]

/**
 * Tamaños de página útiles para `total` registros: los menores que el total y el primero que
 * alcanza para verlos todos. 5 → [5] · 10 → [5, 10] · 15 → [5, 10, 25] · 60 → [5, 10, 25, 50, 100].
 */
export function availablePageSizes(total) {
  const firstThatFitsAll = PAGE_SIZES.findIndex((size) => size >= total)
  return firstThatFitsAll === -1 ? PAGE_SIZES : PAGE_SIZES.slice(0, firstThatFitsAll + 1)
}

/** Números de página a mostrar, con '…' cuando hay muchas: 1 … 4 5 6 … 20 */
export function pageNumbers(current, totalPages) {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1)
  const pages = new Set([1, totalPages, current - 1, current, current + 1])
  const sorted = [...pages].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b)
  return sorted.flatMap((p, i) => (i > 0 && p - sorted[i - 1] > 1 ? ['…', p] : [p]))
}
