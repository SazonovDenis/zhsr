import { ServiceType, Severity } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Выбор неисправности — общее для десктопной и мобильной частей: дерево «категория → группа → неисправность» из
 * справочников FaultCategory, FaultGroup и FaultType, поиск словами и значения по умолчанию.
 */

export interface FaultItem {
  /** id справочника FaultType */
  id: number
  name: string
}

export interface FaultGroupNode {
  /** id справочника FaultGroup */
  id: number
  name: string
  items: FaultItem[]
}

export interface FaultCategoryNode {
  /** id справочника FaultCategory */
  id: number
  name: string
  /** цвет метки категории */
  color: string
  /** сколько неисправностей в категории без частых — они повторяют неисправности других групп по смыслу */
  count: number
  groups: FaultGroupNode[]
}

/** Код группы частых неисправностей (FaultGroup — пополняемый справочник, привязка по коду): показывается первой. */
export const FREQUENT_GROUP_CODE = 'FAV'

/** id группы частых неисправностей; группы нет — null. */
export function frequentGroupId(): number | null {
  return dictManager.getList('FaultGroup').find((g) => g.code === FREQUENT_GROUP_CODE)?.id ?? null
}

/** Неисправности, которые можно выбрать: отключённые не предлагаются. */
function activeFaults() {
  return dictManager.getList('FaultType').filter((f) => f.disabled !== true)
}

/** Поиск начинается с двух букв. */
export function isFaultSearch(query: string): boolean {
  return query.trim().length >= 2
}

/** Дерево неисправностей; при поиске — только совпавшие по названию неисправности или группы. */
export function faultTree(query = ''): FaultCategoryNode[] {
  const q = isFaultSearch(query) ? query.trim().toLowerCase() : ''
  const groups = [...dictManager.getList('FaultGroup')].sort((a, b) => Number(a.sortOrder) - Number(b.sortOrder))
  const types = activeFaults()
  const frequent = frequentGroupId()
  return dictManager
    .getList('FaultCategory')
    .map((cat) => {
      const nodes = groups
        .map((g) => {
          const groupName = dictManager.getValue('FaultGroup', g.id)
          const items = types
            .filter((f) => f.faultCategoryType === cat.id && f.faultGroupType === g.id)
            .map((f) => ({ id: f.id, name: dictManager.getValue('FaultType', f.id) }))
            .filter((f) => !q || `${f.name} ${groupName}`.toLowerCase().includes(q))
          return { id: g.id, name: groupName, items }
        })
        .filter((g) => g.items.length > 0)
      return {
        id: cat.id,
        name: dictManager.getValue('FaultCategory', cat.id),
        color: (cat.color as string | undefined) ?? '#62666d',
        count: nodes.filter((g) => g.id !== frequent).reduce((n, g) => n + g.items.length, 0),
        groups: nodes,
      }
    })
    .filter((c) => c.groups.length > 0)
}

/** Частые неисправности. */
export function frequentFaults(): FaultItem[] {
  const frequent = frequentGroupId()
  return activeFaults()
    .filter((f) => f.faultGroupType === frequent)
    .map((f) => ({ id: f.id, name: dictManager.getValue('FaultType', f.id) }))
}

/**
 * Значения по неисправности (Ф-ЗАЯ-01): критичность по умолчанию и служба-исполнитель из вида неисправности; служба не
 * задана — служба категории неисправности.
 */
export function faultDefaults(fault: number): { severity: number; service: number } {
  const rec = dictManager.getRec('FaultType', fault)
  const category = rec ? dictManager.getRec('FaultCategory', rec.faultCategoryType as number) : null
  return {
    severity: (rec?.severityType as number | undefined) ?? Severity.MEDIUM,
    service:
      (rec?.service as number | null | undefined) ?? (category?.service as number | undefined) ?? ServiceType.TECHNICAL,
  }
}

/** Неисправность с группой — для поиска и строки списка: «Окно дует • Частые неисправности». */
export function faultWithGroup(fault: number): string {
  const rec = dictManager.getRec('FaultType', fault)
  return rec
    ? `${dictManager.getValue('FaultType', fault)} • ${dictManager.getValue('FaultGroup', rec.faultGroupType as number)}`
    : ''
}
