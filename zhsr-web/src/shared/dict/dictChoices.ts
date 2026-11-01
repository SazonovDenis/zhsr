import type { ChoiceItem } from '@/shared/components/choice/choice'
import { dictManager } from './DictManager'
import type { DictName } from './dicts'

/** Записи справочника как значения для CbButton, CbSegment и CbList. Реактивно: читает справочник через getList. */
export function dictChoices(name: DictName): ChoiceItem[] {
  return dictManager.getList(name).map((rec) => ({ id: rec.id, text: dictManager.getValue(name, rec.id) }))
}
