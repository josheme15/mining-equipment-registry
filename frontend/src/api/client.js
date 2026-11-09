// Cliente HTTP hacia el backend. La URL se define en VITE_API_URL (se fija al compilar).
const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

export async function getHealth() {
  const res = await fetch(`${API_URL}/health`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export { API_URL }
