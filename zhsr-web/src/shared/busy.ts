import type { ComputedRef, InjectionKey } from 'vue'
import { computed, inject, provide, ref } from 'vue'

/**
 * Общее ожидание фрейма (страницы, диалога, правой панели): цепочка загрузок, без которых главное показывающее фрейма
 * не может себя показать, — справочники для строк таблицы и сами строки, данные формы диалога, содержимое карточки. Один
 * спиннер — на главном показывающем: на первой таблице страницы, в подвале диалога, на месте содержимого.
 *
 * Варианты выпадающих списков, счётчики и другие независимые загрузки в ожидание фрейма не входят: у них свой спиннер
 * и своя ошибка на своём месте (useLoad, dictSource в shared/loadable.ts).
 *
 * Фрейм создаёт ожидание (provideBusy) и отдаёт busy.active своему главному показывающему. Цепочку фрейм запускает
 * прямо в setup — тогда спиннер виден с первой отрисовки.
 */
export interface Busy {
  /** идёт хотя бы одна загрузка фрейма */
  active: ComputedRef<boolean>
  /** выполнить загрузку в счёт ожидания фрейма; ошибка уходит вызывающему */
  run<T>(work: Promise<T> | (() => Promise<T>)): Promise<T>
}

const BUSY: InjectionKey<Busy> = Symbol('busy')

function createBusy(): Busy {
  const count = ref(0)
  return {
    active: computed(() => count.value > 0),
    async run<T>(work: Promise<T> | (() => Promise<T>)): Promise<T> {
      count.value++
      try {
        return await (typeof work === 'function' ? work() : work)
      } finally {
        count.value--
      }
    },
  }
}

/** Ожидание фрейма: создать и отдать вложенным компонентам. Вызывается в setup страницы или диалога. */
export function provideBusy(): Busy {
  const busy = createBusy()
  provide(BUSY, busy)
  return busy
}

/** Ожидание фрейма, в котором стоит компонент; вне фрейма — своё, ни на чём не показывается. */
export function useBusy(): Busy {
  return inject(BUSY, null) ?? createBusy()
}
