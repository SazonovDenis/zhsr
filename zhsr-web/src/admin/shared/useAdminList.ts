import type { Ref, ShallowRef } from 'vue'
import { reactive, ref, shallowRef, watch } from 'vue'

import type { AdminPage, AdminQuery } from '@/api/admin'
import type { Busy } from '@/shared/busy'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'

/** Строк на странице списка администрирования. */
export const ADMIN_PAGE_SIZE = 50

/** Пауза после ввода в поиск перед запросом, мс. */
const SEARCH_DELAY = 300

export interface AdminList<T> {
  query: AdminQuery
  rows: ShallowRef<T[]>
  total: Ref<number>
  /** ошибка загрузки; null — ошибки нет */
  error: Ref<ShownError | null>
  load: () => Promise<void>
}

/**
 * Список администрирования со страницами, поиском и сортировкой на сервере. Смена поиска или сортировки — снова первая
 * страница; поиск отправляется после паузы в вводе. Загрузка — в счёт ожидания страницы busy: спиннер страницы.
 */
export function useAdminList<T>(fetch: (query: AdminQuery) => Promise<AdminPage<T>>, busy: Busy): AdminList<T> {
  const query = reactive<AdminQuery>({ search: '', page: 0, size: ADMIN_PAGE_SIZE, sort: null })
  const rows = shallowRef<T[]>([])
  const total = ref(0)
  const error = ref<ShownError | null>(null)

  // ответ на устаревший запрос (поиск успели изменить) не показывается
  let seq = 0

  async function load() {
    const current = ++seq
    try {
      const page = await busy.run(fetch({ ...query }))
      if (current !== seq) return
      rows.value = page.rows
      total.value = page.total
      error.value = null
    } catch (e) {
      if (current !== seq) return
      rows.value = []
      total.value = 0
      error.value = shownError(e)
    }
  }

  let timer: ReturnType<typeof setTimeout> | undefined
  watch(
    () => query.search,
    () => {
      clearTimeout(timer)
      timer = setTimeout(() => {
        query.page = 0
        void load()
      }, SEARCH_DELAY)
    },
  )
  watch(
    () => query.sort,
    () => {
      query.page = 0
      void load()
    },
  )
  watch(() => query.page, load)

  return { query, rows, total, error, load }
}
