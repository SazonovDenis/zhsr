import { reactive } from 'vue'

import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'

/**
 * Источник вариантов контрола выбора (CbList, CbButton, CbSegment): как идёт загрузка вариантов. Контрол получает его
 * свойством source и показывает загрузку и ошибку вместо вариантов; после ошибки, когда пользователь снова
 * обращается к контролу (открывает список), контрол сам вызывает retry. Когда контрол создаётся, он вызывает ensure:
 * источник, который грузит контрол (справочник), начинает загрузку; источник владельца (useLoad) владелец запускает сам.
 *
 * Источники:
 * - useLoad — варианты грузит сам владелец контрола (страница, компонент): состояние — у владельца;
 * - dictSource — варианты из справочника: состояние — в DictManager, одно на всё приложение.
 */
export interface Loadable {
  /** варианты грузятся */
  readonly loading: boolean
  /** последняя загрузка упала; null — ошибки нет */
  readonly error: ShownError | null
  /** загрузить заново, если последняя загрузка упала */
  retry(): void
  /** контрол создан: начать загрузку, если её грузит контрол и она ещё не начиналась */
  ensure(): void
}

/** Загрузка владельца: источник вариантов и запуск загрузки. */
export interface Load extends Loadable {
  /**
   * Запустить загрузку; идёт загрузка — дождаться её. Ошибка не бросается: она в error и видна в контроле.
   *
   * @return true — загрузка прошла
   */
  run(): Promise<boolean>
}

/**
 * Загрузка, которую владелец контрола запускает сам. work — как грузятся варианты: результат work кладёт в данные
 * владельца. Загрузка вариантов независима от главного показывающего фрейма (таблицы, карточки): в ожидание фрейма она
 * не входит, её спиннер и ошибка — только в самом контроле. Состояние живёт, пока жив владелец.
 *
 *   const tripLoad = useLoad(async () => { trips.value = await fetchTripOptions(...) })
 *   void tripLoad.run()
 *   <CbList :values="tripChoices" :source="tripLoad" />
 */
export function useLoad(work: () => Promise<void>): Load {
  const state = reactive({ loading: false, error: null as ShownError | null })
  let running: Promise<boolean> | null = null

  function run(): Promise<boolean> {
    running ??= (async () => {
      state.loading = true
      state.error = null
      try {
        await work()
        return true
      } catch (e) {
        state.error = shownError(e)
        return false
      } finally {
        state.loading = false
        running = null
      }
    })()
    return running
  }

  return {
    get loading() {
      return state.loading
    },
    get error() {
      return state.error
    },
    run,
    retry() {
      if (state.error) void run()
    },
    // загрузку запускает владелец (run), не контрол
    ensure() {},
  }
}
