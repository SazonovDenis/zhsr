import type { ChoiceItem } from '@/shared/components/choice/choice'
import type { Loadable } from '@/shared/loadable'
import { dictManager } from './DictManager'
import type { DictName } from './dicts'

/** Записи справочника как значения для CbButton, CbSegment и CbList. Реактивно: читает справочник через getList. */
export function dictChoices(name: DictName): ChoiceItem[] {
  return dictManager.getList(name).map((rec) => ({ id: rec.id, text: dictManager.getValue(name, rec.id) }))
}

/**
 * Источник вариантов из справочника для контрола выбора: контрол сам загружает справочник (ensure — prepareDict, когда
 * контрол создан); загрузка и ошибка — состояние справочника в DictManager, общее для всех контролов этого
 * справочника; retry — загрузить справочник заново после ошибки. Системные справочники в коде: у них загрузки и ошибки
 * не бывает.
 *
 *   <CbList :values="dictChoices('Station')" :source="dictSource('Station')" />
 */
export function dictSource(name: DictName): Loadable {
  return {
    get loading() {
      return dictManager.loadState(name).loading
    },
    get error() {
      return dictManager.loadState(name).error
    },
    retry() {
      // ошибка повторной загрузки — в состоянии справочника, её покажет контрол
      if (dictManager.loadState(name).error) dictManager.prepareDict(name).catch(() => undefined)
    },
    ensure() {
      // справочник загружен, грузится или упал (повтор — retry) — ничего не делать; ошибка — в состоянии справочника
      const state = dictManager.loadState(name)
      if (!state.loading && !state.error) dictManager.prepareDict(name).catch(() => undefined)
    },
  }
}
