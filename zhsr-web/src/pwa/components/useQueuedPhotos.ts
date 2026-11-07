import { computed, onBeforeUnmount } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import type { OutboxItem } from '@/shared/offline/outbox'

/**
 * Фото записей очереди для показа: адреса файлов в памяти. Адрес живёт, пока жив компонент; запись ушла из очереди —
 * её фото просто больше нет в списке.
 */
export function useQueuedPhotos(items: () => OutboxItem[]) {
  const urls = new Map<string, string>()
  onBeforeUnmount(() => urls.forEach((url) => URL.revokeObjectURL(url)))

  return computed<RequestPhoto[]>(() =>
    items().flatMap((item) => {
      if (!item.photo) return []
      if (!urls.has(item.id)) urls.set(item.id, URL.createObjectURL(item.photo))
      return [{ id: item.id, url: urls.get(item.id)! }]
    }),
  )
}
