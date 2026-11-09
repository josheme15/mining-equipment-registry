const hoursFormatter = new Intl.NumberFormat('es', { minimumFractionDigits: 0, maximumFractionDigits: 1 })
const dateFormatter = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' })

export const formatHours = (value) => `${hoursFormatter.format(Number(value))} h`
export const formatDate = (iso) => dateFormatter.format(new Date(iso))
