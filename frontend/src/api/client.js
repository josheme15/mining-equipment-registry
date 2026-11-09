// Cliente HTTP hacia el backend. La URL se define en VITE_API_URL (se fija al compilar).
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

/** Error de la API con el mensaje y los errores por campo que devuelve el backend. */
export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

// Sin límite, una petición puede quedar colgada mientras Render despierta el servidor.
const REQUEST_TIMEOUT_MS = 25_000

async function request(path, { method = 'GET', body, timeoutMs = REQUEST_TIMEOUT_MS } = {}) {
  let res
  try {
    res = await fetch(`${API_URL}${path}`, {
      method,
      headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(timeoutMs),
    })
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor. Si estuvo inactivo, puede tardar ~1 min en despertar.')
  }

  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(res.status, data?.message || `Error HTTP ${res.status}`, data?.fieldErrors || {})
  }
  return data
}

export const api = {
  health: () => request('/health'),
  listTypes: () => request('/api/equipment-types'),
  listEquipments: () => request('/api/equipments'),
  createEquipment: (data) => request('/api/equipments', { method: 'POST', body: data }),
  updateEquipment: (id, data) => request(`/api/equipments/${id}`, { method: 'PUT', body: data }),
  deleteEquipment: (id) => request(`/api/equipments/${id}`, { method: 'DELETE' }),
  listMaintenances: (id) => request(`/api/equipments/${id}/maintenances`),
  registerMaintenance: (id, notes) =>
    request(`/api/equipments/${id}/maintenances`, { method: 'POST', body: { notes } }),
}
