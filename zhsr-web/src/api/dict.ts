import type { DictData, DictId, DictName, DictRec } from '@/shared/dict/dicts'
import { http } from './http'

/**
 * API пополняемых справочников (их ведёт администрирование). Системные справочники с сервера не загружаются: их
 * записи зашиты в код (shared/constants).
 */

export async function loadDict(name: DictName): Promise<DictData> {
  return (await http.get<DictData>(`/dict/${name}`)).data
}

/** Одна запись справочника; record = null — записи больше нет. */
export async function loadDictValue(name: DictName, id: DictId): Promise<{ version: string; record: DictRec | null }> {
  return (await http.get<{ version: string; record: DictRec | null }>(`/dict/${name}/${id}`)).data
}

/** Текущие версии справочников. */
export async function loadDictVersions(names: DictName[]): Promise<Partial<Record<DictName, string>>> {
  return (await http.post<Partial<Record<DictName, string>>>('/dict/versions', names)).data
}
