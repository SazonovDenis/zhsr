import type { ChoiceItem } from '@/shared/components/choice/choice'
import type { Loadable } from '@/shared/loadable'
import { dictManager } from './DictManager'
import type { DictDef, DictName, DictRec } from './dicts'
import { dictDefs } from './dicts'
import { compareText } from '@/shared/format'

/**
 * Записи справочника в порядке показа: системный — в порядке записей в коде; пополняемый — по порядку показа
 * (sortOrder), если он есть, затем по названию на языке интерфейса. Реактивно: читает справочник через getList.
 */
export function dictRecords(name: DictName): DictRec[] {
  const list = dictManager.getList(name)
  if ((dictDefs[name] as DictDef).system) return list
  const order = (rec: DictRec) => Number(rec.sortOrder ?? 0)
  const texts = new Map(list.map((rec) => [rec.id, dictManager.getValue(name, rec.id)]))
  return [...list].sort((a, b) => order(a) - order(b) || compareText(texts.get(a.id)!, texts.get(b.id)!))
}

/** Записи справочника в порядке показа (dictRecords) как значения для CbButton, CbSegment и CbList. */
export function dictChoices(name: DictName): ChoiceItem[] {
  return dictRecords(name).map((rec) => ({ id: rec.id, text: dictManager.getValue(name, rec.id) }))
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
