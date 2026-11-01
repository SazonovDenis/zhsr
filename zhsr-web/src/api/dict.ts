import type { DictData, DictId, DictName, DictRec } from '@/shared/dict/dicts'
// import { http } from './http'

/**
 * API справочников. Сервер пока не готов: функции отдают фиктивные данные, а над ними закомментирован будущий вызов
 * REST.
 */

export async function loadDict(name: DictName): Promise<DictData> {
  // return (await http.get<DictData>(`/dict/${name}`)).data
  return Promise.resolve({ version: fakeVersion(name), records: FAKE_DICTS[name] })
}

/** Одна запись справочника; record = null — записи больше нет. */
export async function loadDictValue(name: DictName, id: DictId): Promise<{ version: string; record: DictRec | null }> {
  // return (await http.get<{ version: string; record: DictRec | null }>(`/dict/${name}/${id}`)).data
  return Promise.resolve({ version: fakeVersion(name), record: FAKE_DICTS[name].find((r) => r.id === id) ?? null })
}

/** Текущие версии справочников. */
export async function loadDictVersions(names: DictName[]): Promise<Partial<Record<DictName, string>>> {
  // return (await http.post<Partial<Record<DictName, string>>>('/dict/versions', names)).data
  return Promise.resolve(Object.fromEntries(names.map((name) => [name, fakeVersion(name)])))
}

// --- фиктивные данные: значения из макета ---

/**
 * Версия фиктивного справочника — от его содержимого: при любой правке данных версия меняется, и копия справочника,
 * сохранённая в браузере (справочники offline), обновляется при сверке версий сама.
 */
function fakeVersion(name: DictName): string {
  let hash = 0
  for (const ch of JSON.stringify(FAKE_DICTS[name])) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return String(hash)
}

const FAKE_DICTS: Record<DictName, DictRec[]> = {
  WagonState: [
    { id: 'IN_IDLE', text: 'В простое', bg: '#ece6fa', fg: '#5b3a9e' },
    { id: 'IN_TRIP', text: 'В пути', bg: '#dbe8f5', fg: '#0b3a63' },
    { id: 'IN_REPAIR', text: 'В ремонте', bg: '#fbe9cf', fg: '#8a4b05' },
    { id: 'RESERVE', text: 'Резерв', bg: '#e6e9f5', fg: '#3a4378' },
    { id: 'OUT_OF_SERVICE', text: 'Отставлен', bg: '#f7dcd8', fg: '#8f2416' },
  ],
  WagonType: [
    { id: 'COMPARTMENT', text: 'Купейный' },
    { id: 'PLATZKART', text: 'Плацкартный' },
    { id: 'SLEEPER', text: 'СВ' },
    { id: 'LUX', text: 'Люкс' },
    { id: 'SEATING', text: 'Сидячий' },
    { id: 'RESTAURANT', text: 'Вагон-ресторан' },
    { id: 'STAFF', text: 'Штабной' },
  ],
  WagonModel: [
    { id: 'TULPAR_TALGO', text: 'Тулпар-Тальго' },
    { id: 'TULPAR_TMH', text: 'Тулпар (ТВЗ/ТМХ)' },
    { id: 'STADLER_SLEEPER', text: 'Stadler, спальный' },
    { id: 'STADLER_COUCHETTE', text: 'Stadler, кушетный' },
    { id: 'STADLER_STAFF', text: 'Stadler, штабной' },
    { id: 'STADLER_GENERATOR', text: 'Stadler, вагон-генератор' },
  ],
  LocationKind: [
    { id: 'STATION', text: 'Станция' },
    { id: 'DEPOT', text: 'Депо' },
    { id: 'TRIP', text: 'В рейсе' },
    { id: 'YARD', text: 'Парк отстоя' },
  ],
  TripState: [
    { id: 'PLANNED', text: 'План', bg: '#ece6fa', fg: '#5b3a9e' },
    { id: 'IN_PROGRESS', text: 'Выполняется', bg: '#dbe8f5', fg: '#0b3a63' },
    { id: 'DONE', text: 'Выполнен', bg: '#dff0e2', fg: '#1d5e2c' },
    { id: 'CANCELLED', text: 'Отменён', bg: '#f1f1ef', fg: '#8a8f8b' },
  ],
  RouteKind: [
    { id: 'REGULAR', text: 'Регулярный' },
    { id: 'ONE_TIME', text: 'Разовый' },
  ],
  Depot: [
    { id: 'D1', text: { ru: 'Вагонное депо Астана', kk: 'Астана вагон депосы', en: 'Astana car depot' } },
    { id: 'D2', text: { ru: 'Вагонное депо Алматы', kk: 'Алматы вагон депосы', en: 'Almaty car depot' } },
    { id: 'D4', text: { ru: 'Вагонное депо Караганды', kk: 'Қарағанды вагон депосы', en: 'Karaganda car depot' } },
  ],
  // hub — узловая станция (со своим депо): с неё может начинаться и на ней заканчиваться маршрут
  Station: [
    { id: 'AST', text: 'Астана-1', hub: true },
    { id: 'NURLY', text: 'Нурлы Жол', hub: false },
    { id: 'KRG', text: 'Караганды', hub: true },
    { id: 'MOYINTY', text: 'Мойынты', hub: false },
    { id: 'SARYSHAGAN', text: 'Сары-Шаган', hub: false },
    { id: 'SHU', text: 'Шу', hub: false },
    { id: 'OTAR', text: 'Отар', hub: false },
    { id: 'ALA1', text: 'Алматы-1', hub: false },
    { id: 'ALA', text: 'Алматы-2', hub: true },
    { id: 'KOK', text: 'Кокшетау', hub: false },
    { id: 'PTR', text: 'Петропавловск', hub: true },
  ],
  Yard: [
    { id: 'Y1', text: 'Астана-Северный' },
    { id: 'Y2', text: 'Алматы-1' },
    { id: 'Y3', text: 'Караганды-Сортировочная' },
  ],
}
