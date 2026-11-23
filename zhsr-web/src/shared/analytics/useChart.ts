import type { ComputedRef, Ref, ShallowRef } from 'vue'
import { ref, shallowRef } from 'vue'

import { provideBusy } from '@/shared/busy'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'

/** Состояние диаграммы: данные, ожидание, ошибка и загрузка. Показывает его ChartView. */
export interface ChartState<T> {
  data: ShallowRef<T | null>
  active: ComputedRef<boolean>
  error: Ref<ShownError | null>
  load: () => Promise<void>
}

/**
 * Общая загрузка диаграммы: ожидание, ошибка и закономерность. Ответ на устаревший запрос (отбор успели сменить) не
 * показывается. Первую загрузку запускает сама диаграмма (load).
 *
 * @param fetch  загрузить данные и справочники диаграммы
 * @param onFind закономерность — владельцу диаграммы; после ошибки пусто
 * @param find   текст закономерности по данным; вызывается, когда data уже заполнено
 */
export function useChart<T>(
  fetch: () => Promise<T>,
  onFind: (text: string) => void,
  find: (data: T) => string,
): ChartState<T> {
  const busy = provideBusy()
  const data = shallowRef<T | null>(null) as ShallowRef<T | null>
  const error = ref<ShownError | null>(null)
  let loadNo = 0

  async function load() {
    const no = ++loadNo
    error.value = null
    try {
      const loaded = await busy.run(fetch)
      if (no !== loadNo) return
      data.value = loaded
      onFind(find(loaded))
    } catch (e) {
      if (no !== loadNo) return
      error.value = shownError(e)
      onFind('')
    }
  }

  return { data, active: busy.active, error, load }
}
