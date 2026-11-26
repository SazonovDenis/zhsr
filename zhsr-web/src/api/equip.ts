import type { TableSort } from '@/shared/components/table/types'
import type { RequestRef, Texts } from './checks'
import { http } from './http'
import type { UserRef, WagonRef } from './requests'
import type { TripRef } from './wagons'

/**
 * API описей экипировки (Ф-ЭКП-01 … Ф-ЭКП-03): сервер zhsr-app, EquipController. Поля — как колонки БД в camelCase.
 */

/** База расчёта нормы — equip_list_item_norm.basis (константы EquipBasis). */
export const EquipBasis = {
  WAGON: 1,
  SEAT: 2,
  COMPARTMENT: 3,
  WINDOW: 4,
} as const

/** Способ сравнения — equip_list_item_norm.quantity_kind (константы EquipQuantityKind). */
export const EquipQuantityKind = {
  EXACT: 1,
  AT_LEAST: 2,
} as const

/** Шаблон описи (equip_list_template). */
export interface EquipTemplate extends Texts {
  id: number
  /** неисправность заявки по недостаче — id справочника FaultType; null — опись без заявки */
  faultType: number | null
  sortOrder: number
  disabled: boolean
}

export type EquipTemplateBody = Omit<EquipTemplate, 'id'>

/** Пункт шаблона описи (equip_list_template_item): раздел (unit = null) или позиция. */
export interface EquipItem extends Texts {
  id: number
  equipListTemplate: number
  parentItem: number | null
  /** единица измерения; null — раздел */
  unit: string | null
  sortOrder: number
  disabled: boolean
}

export type EquipItemBody = Omit<EquipItem, 'id' | 'equipListTemplate'>

/** Норматив позиции (equip_list_item_norm). */
export interface EquipNorm {
  id: number
  equipListTemplateItem: number
  wagonType: number | null
  quantityPerBasis: number
  /** EquipBasis */
  basis: number
  /** EquipQuantityKind */
  quantityKind: number
  headTailExtra: number
  normText: string | null
  critical: boolean
  dbeg: string
  dend: string | null
}

export type EquipNormBody = Omit<EquipNorm, 'id' | 'equipListTemplateItem' | 'dbeg' | 'dend'>

/** Позиция описи для заполнения. */
export interface EquipFormItem extends EquipItem {
  equipListItemNorm: number | null
  normText: string | null
  /** EquipQuantityKind; null — норматива нет */
  quantityKind: number | null
  critical: boolean
  /** требуемое количество; null — норматива нет или у модели вагона нет базы */
  quantityRequired: number | null
  /** количество в прошлой описи; null — прошлой описи нет */
  quantityPrevious: number | null
}

export interface EquipForm {
  template: EquipTemplate
  items: EquipFormItem[]
}

/** Позиция завершённой описи. photos — сохранённые фото, которые остаются при исправлении описи. */
export interface EquipValueDraft {
  equipListTemplateItem: number
  quantityActual: number
  comment: string | null
  photos: number[]
}

/** Завершённая опись (Ф-ЭКП-02). photos — новые фото по id пункта шаблона. При исправлении шаблон и вагон — из описи. */
export interface EquipListDraft {
  equipListTemplate: number
  wagon: number
  /** когда опись начата, ISO */
  createdDt: string
  values: EquipValueDraft[]
  photos: Map<number, Blob[]>
}

/** Шаблон описи в данных. */
export interface EquipTemplateRef extends Texts {
  id: number
}

/** Опись — строка списка (equip_list). */
export interface EquipListRow {
  id: number
  equipListTemplate: EquipTemplateRef
  wagon: WagonRef
  trip: TripRef | null
  createdByUsr: UserRef
  createdDt: string
  completedDt: string
  repairRequest: RequestRef | null
  violationCount: number
}

export interface EquipListValue {
  id: number
  equipListTemplateItem: number
  quantityActual: number
  quantityRequired: number | null
  equipListItemNorm: number | null
  violation: boolean
  comment: string | null
  /** фото — id; файл — equipPhotoUrl(id) */
  photos: number[]
}

export interface EquipListDetail extends EquipListRow {
  items: EquipItem[]
  values: EquipListValue[]
}

/** Фильтр списка описей (Ф-ЭКП-03): даты — 'YYYY-MM-DD'. */
export interface EquipListFilter {
  template: number | null
  /** вагон — id вагона; null — все вагоны */
  wagon: number | null
  query: string
  from: string | null
  to: string | null
}

// --- описи

export async function fetchEquipTemplates(): Promise<EquipTemplate[]> {
  return (await http.get<EquipTemplate[]>('/equip-templates')).data
}

/** Опись для заполнения; exclude — исправляемая опись: количество «было» — из описи до неё. */
export async function fetchEquipForm(
  template: number,
  wagon: number,
  exclude: number | null = null,
): Promise<EquipForm> {
  const params = exclude === null ? { template, wagon } : { template, wagon, exclude }
  return (await http.get<EquipForm>('/equip-lists/form', { params })).data
}

/** Описи по фильтру. sort — поле и направление сортировки; null — сначала новые. */
export async function fetchEquipLists(filter: EquipListFilter, sort: TableSort | null = null): Promise<EquipListRow[]> {
  const params = { ...filter, sort: sort?.field, desc: sort?.desc }
  return (await http.get<EquipListRow[]>('/equip-lists', { params })).data
}

/** Последняя опись вагона — список имущества вагона, без ограничения видимости описей; описей нет — null. */
export async function fetchLastEquipList(wagon: number): Promise<EquipListDetail | null> {
  return (await http.get<EquipListDetail | ''>('/equip-lists/last', { params: { wagon } })).data || null
}

/** Последняя опись вагона по шаблону и недостача в ней. */
export interface EquipListShortage {
  id: number
  equipListTemplate: EquipTemplateRef
  wagon: WagonRef
  createdDt: string
  /** сколько позиций в недостаче; 0 — недостачи нет */
  shortageCount: number
}

/** Последние описи вагонов по шаблонам с недостачей; вагона без описей в ответе нет. */
export async function fetchEquipShortages(wagons: number[]): Promise<EquipListShortage[]> {
  const params = { wagons: wagons.join(',') }
  return (await http.get<EquipListShortage[]>('/equip-lists/shortages', { params })).data
}

export async function fetchEquipList(id: number): Promise<EquipListDetail> {
  return (await http.get<EquipListDetail>(`/equip-lists/${id}`)).data
}

function equipForm(draft: EquipListDraft): FormData {
  const form = new FormData()
  const { photos, ...fields } = draft
  form.append('draft', new Blob([JSON.stringify(fields)], { type: 'application/json' }))
  photos.forEach((files, item) => files.forEach((f) => form.append(`photo_${item}`, f)))
  return form
}

/** Отправить завершённую опись: Система считает нарушения и создаёт одну заявку по недостаче. */
export async function createEquipList(draft: EquipListDraft): Promise<EquipListDetail> {
  return (await http.post<EquipListDetail>('/equip-lists', equipForm(draft))).data
}

/** Исправить опись: автор в течение 10 минут после последнего сохранения; заявка по недостаче пересчитывается. */
export async function updateEquipList(id: number, draft: EquipListDraft): Promise<EquipListDetail> {
  return (await http.put<EquipListDetail>(`/equip-lists/${id}`, equipForm(draft))).data
}

/** Адрес файла фото к значению позиции. */
export function equipPhotoUrl(id: number): string {
  return http.getUri({ url: `/equip-lists/photos/${id}` })
}

// --- администрирование

export async function fetchAdminEquipTemplates(): Promise<EquipTemplate[]> {
  return (await http.get<EquipTemplate[]>('/admin/equip-templates')).data
}

export async function saveEquipTemplate(id: number | null, body: EquipTemplateBody): Promise<EquipTemplate> {
  return id === null
    ? (await http.post<EquipTemplate>('/admin/equip-templates', body)).data
    : (await http.put<EquipTemplate>(`/admin/equip-templates/${id}`, body)).data
}

export async function deleteEquipTemplate(id: number): Promise<void> {
  await http.delete(`/admin/equip-templates/${id}`)
}

export async function fetchEquipItems(template: number): Promise<EquipItem[]> {
  return (await http.get<EquipItem[]>(`/admin/equip-templates/${template}/items`)).data
}

export async function saveEquipItem(template: number, id: number | null, body: EquipItemBody): Promise<EquipItem> {
  return id === null
    ? (await http.post<EquipItem>(`/admin/equip-templates/${template}/items`, body)).data
    : (await http.put<EquipItem>(`/admin/equip-items/${id}`, body)).data
}

export async function deleteEquipItem(id: number): Promise<void> {
  await http.delete(`/admin/equip-items/${id}`)
}

export async function fetchEquipNorms(item: number): Promise<EquipNorm[]> {
  return (await http.get<EquipNorm[]>(`/admin/equip-items/${item}/norms`)).data
}

export async function saveEquipNorm(item: number, id: number | null, body: EquipNormBody): Promise<EquipNorm> {
  return id === null
    ? (await http.post<EquipNorm>(`/admin/equip-items/${item}/norms`, body)).data
    : (await http.put<EquipNorm>(`/admin/equip-norms/${id}`, body)).data
}

export async function deleteEquipNorm(id: number): Promise<void> {
  await http.delete(`/admin/equip-norms/${id}`)
}
