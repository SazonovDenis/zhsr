import { dictManager } from '@/shared/dict/DictManager'

/**
 * Выбор неисправности — общее для десктопной и мобильной частей: дерево «категория → группа → неисправность» из
 * справочников FaultCategory, FaultGroup и FaultType, поиск словами и значения по умолчанию.
 */

export interface FaultItem {
  id: string
  name: string
}

export interface FaultGroupNode {
  id: string
  name: string
  items: FaultItem[]
}

export interface FaultCategoryNode {
  id: string
  name: string
  /** цвет метки категории */
  color: string
  /** сколько неисправностей в категории без частых — они повторяют неисправности других групп по смыслу */
  count: number
  groups: FaultGroupNode[]
}

/** Группа частых неисправностей: показывается первой. */
export const FREQUENT_GROUP = 'FAV'

/** Поиск начинается с двух букв. */
export function isFaultSearch(query: string): boolean {
  return query.trim().length >= 2
}

/** Дерево неисправностей; при поиске — только совпавшие по названию неисправности или группы. */
export function faultTree(query = ''): FaultCategoryNode[] {
  const q = isFaultSearch(query) ? query.trim().toLowerCase() : ''
  const groups = [...dictManager.getList('FaultGroup')].sort((a, b) => Number(a.sort) - Number(b.sort))
  const types = dictManager.getList('FaultType')
  return dictManager
    .getList('FaultCategory')
    .map((cat) => {
      const nodes = groups
        .map((g) => {
          const groupName = dictManager.getValue('FaultGroup', g.id)
          const items = types
            .filter((f) => f.category === cat.id && f.group === g.id)
            .map((f) => ({ id: String(f.id), name: dictManager.getValue('FaultType', f.id) }))
            .filter((f) => !q || `${f.name} ${groupName}`.toLowerCase().includes(q))
          return { id: String(g.id), name: groupName, items }
        })
        .filter((g) => g.items.length > 0)
      return {
        id: String(cat.id),
        name: dictManager.getValue('FaultCategory', cat.id),
        color: (cat.color as string | undefined) ?? '#62666d',
        count: nodes.filter((g) => g.id !== FREQUENT_GROUP).reduce((n, g) => n + g.items.length, 0),
        groups: nodes,
      }
    })
    .filter((c) => c.groups.length > 0)
}

/** Частые неисправности. */
export function frequentFaults(): FaultItem[] {
  return dictManager
    .getList('FaultType')
    .filter((f) => f.group === FREQUENT_GROUP)
    .map((f) => ({ id: String(f.id), name: dictManager.getValue('FaultType', f.id) }))
}

/**
 * Значения по умолчанию для неисправности (Ф-ЗАЯ-01): критичность и служба-исполнитель из вида неисправности; служба
 * не задана — служба категории неисправности.
 */
export function faultDefaults(fault: string): { severity: string; service: string } {
  const rec = dictManager.getRec('FaultType', fault)
  const category = rec ? dictManager.getRec('FaultCategory', rec.category as string) : null
  return {
    severity: (rec?.severity as string | undefined) ?? 'MEDIUM',
    service: (rec?.service as string | null | undefined) ?? (category?.service as string | undefined) ?? 'TECHNICAL',
  }
}

/** Неисправность с группой — для поиска и строки списка: «Окно дует • Частые неисправности». */
export function faultWithGroup(fault: string): string {
  const rec = dictManager.getRec('FaultType', fault)
  return rec
    ? `${dictManager.getValue('FaultType', fault)} • ${dictManager.getValue('FaultGroup', rec.group as string)}`
    : ''
}
