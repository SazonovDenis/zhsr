import { CheckObject, CheckValueKind } from '@/api/checks'
import { EquipBasis, EquipQuantityKind } from '@/api/equip'
import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'

/**
 * Подписи мелких перечислений чек-листов и описей (константы в коде, без справочника): объект проверки, вид значения
 * пункта, база расчёта и способ сравнения нормы.
 */

export const CHECK_OBJECTS: ChoiceItem[] = [
  { id: CheckObject.WAGON, text: 'Вагон' },
  { id: CheckObject.USR, text: 'Сотрудник' },
  { id: CheckObject.TRIP, text: 'Рейс' },
]

export const VALUE_KINDS: ChoiceItem[] = [
  { id: CheckValueKind.STRING, text: 'Строка' },
  { id: CheckValueKind.NUMBER, text: 'Число' },
  { id: CheckValueKind.DICT_VALUE, text: 'Вариант ответа' },
]

export const EQUIP_BASES: ChoiceItem[] = [
  { id: EquipBasis.WAGON, text: 'на вагон' },
  { id: EquipBasis.SEAT, text: 'на место' },
  { id: EquipBasis.COMPARTMENT, text: 'на купе' },
  { id: EquipBasis.WINDOW, text: 'на окно' },
]

export const EQUIP_QUANTITY_KINDS: ChoiceItem[] = [
  { id: EquipQuantityKind.EXACT, text: 'Ровно' },
  { id: EquipQuantityKind.AT_LEAST, text: 'Не менее' },
]

function labelOf(items: ChoiceItem[], id: number | null | undefined): string {
  const item = items.find((i) => i.id === id)
  return item?.text ? t(item.text) : ''
}

export const checkObjectText = (id: number | null | undefined) => labelOf(CHECK_OBJECTS, id)
export const valueKindText = (id: number | null | undefined) => labelOf(VALUE_KINDS, id)
export const equipBasisText = (id: number | null | undefined) => labelOf(EQUIP_BASES, id)
export const equipQuantityKindText = (id: number | null | undefined) => labelOf(EQUIP_QUANTITY_KINDS, id)
