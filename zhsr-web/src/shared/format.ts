const pad = (n: number) => String(n).padStart(2, '0')

/** Дата и время для показа: «28.10 21:00»; год показывается, только если он не текущий. Пусто — прочерк. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  const year = d.getFullYear() !== new Date().getFullYear() ? `.${d.getFullYear()}` : ''
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}${year} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
