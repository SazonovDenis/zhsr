import { dictRecords } from '@/shared/dict/dictChoices'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { Severity } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Выбор неисправности — общее для десктопной и мобильной частей: дерево «служба-исполнитель → группа → неисправность»
 * из справочников Service, FaultGroup и FaultType, поиск словами и значения по умолчанию.
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

export interface FaultServiceNode {
  /** id справочника Service */
  id: number
  name: string
  /** цвет метки службы */
  color: string
  /** сколько неисправностей у службы */
  count: number
  groups: FaultGroupNode[]
}

/** Неисправности, которые можно выбрать: отключённые не предлагаются. */
function activeFaults() {
  return dictRecords('FaultType').filter((f) => f.disabled !== true)
}

/** Поиск начинается с двух букв. */
export function isFaultSearch(query: string): boolean {
  return query.trim().length >= 2
}

/** Дерево неисправностей; при поиске — только совпавшие по названию неисправности или группы. */
export function faultTree(query = ''): FaultServiceNode[] {
  const q = isFaultSearch(query) ? query.trim().toLowerCase() : ''
  const groups = dictRecords('FaultGroup')
  const types = activeFaults()
  return dictManager
    .getList('Service')
    .map((service) => {
      const nodes = groups
        .map((g) => {
          const groupName = dictManager.getValue('FaultGroup', g.id)
          const items = types
            .filter((f) => f.service === service.id && f.faultGroupType === g.id)
            .map((f) => ({ id: f.id, name: dictManager.getValue('FaultType', f.id) }))
            .filter((f) => !q || `${f.name} ${groupName}`.toLowerCase().includes(q))
          return { id: g.id, name: groupName, items }
        })
        .filter((g) => g.items.length > 0)
      return {
        id: service.id,
        name: dictManager.getValue('Service', service.id),
        color: (service.color as string | undefined) ?? '#62666d',
        count: nodes.reduce((n, g) => n + g.items.length, 0),
        groups: nodes,
      }
    })
    .filter((s) => s.groups.length > 0)
}

/**
 * Дерево неисправностей вариантами CbList — для десктопа: строки службы и группы — заголовки (выбрать их нельзя,
 * курсор с клавиатуры их пропускает), за ними неисправности. Поиск в CbList: неисправность находится и по названию
 * группы и службы, заголовок виден, пока виден хоть один его пункт. Вид строки — data.kind: service, group, fault.
 */
export function faultChoices(): ChoiceItem[] {
  const res: ChoiceItem[] = []
  for (const s of faultTree()) {
    const serviceNames = s.groups.flatMap((g) => g.items.map((f) => f.name))
    res.push({
      id: `s${s.id}`,
      text: s.name,
      disabled: true,
      search: `${serviceNames.join(' ')} ${s.groups.map((g) => g.name).join(' ')}`,
      data: { kind: 'service', color: s.color, count: s.count },
    })
    for (const g of s.groups) {
      res.push({
        id: `g${g.id}-${s.id}`,
        text: g.name,
        disabled: true,
        search: `${g.items.map((f) => f.name).join(' ')} ${s.name}`,
        data: { kind: 'group', count: g.items.length },
      })
      for (const f of g.items) {
        res.push({ id: f.id, text: f.name, search: `${g.name} ${s.name}`, data: { kind: 'fault' } })
      }
    }
  }
  return res
}

/** Частые неисправности: неисправности в избранном (fault_type_favorite) по порядку показа. */
export function frequentFaults(): FaultItem[] {
  return activeFaults()
    .filter((f) => f.favoriteOrder !== null && f.favoriteOrder !== undefined)
    .sort((a, b) => Number(a.favoriteOrder) - Number(b.favoriteOrder))
    .map((f) => ({ id: f.id, name: dictManager.getValue('FaultType', f.id) }))
}

/**
 * Значения по неисправности (Ф-ЗАЯ-01): критичность по умолчанию и служба-исполнитель из вида неисправности; вида
 * неисправности нет в справочнике — служба пуста.
 */
export function faultDefaults(fault: number): { severity: number; service: number | null } {
  const rec = dictManager.getRec('FaultType', fault)
  return {
    severity: (rec?.severityType as number | undefined) ?? Severity.MEDIUM,
    service: (rec?.service as number | undefined) ?? null,
  }
}

/** Неисправность с группой — для поиска и строки списка: «Окно дует • Кузов». */
export function faultWithGroup(fault: number): string {
  const rec = dictManager.getRec('FaultType', fault)
  return rec
    ? `${dictManager.getValue('FaultType', fault)} • ${dictManager.getValue('FaultGroup', rec.faultGroupType as number)}`
    : ''
}
