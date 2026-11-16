import { http } from './http'
import type { UserRef, WagonRef } from './requests'
import type { TripRef } from './wagons'

/**
 * API чек-листов (Ф-ЧЕК-01 … Ф-ЧЕК-04): сервер zhsr-app, CheckController. Завершённый чек-лист — multipart: часть draft
 * (JSON) и фото пунктов частями photo_<id пункта шаблона>. Поля — как колонки БД в camelCase.
 */

/** Название на трёх языках: на русском обязательно, на казахском и английском — null, если не задано. */
export interface Texts {
  textRu: string
  textKk: string | null
  textEn: string | null
}

/** Что проверяют по чек-листу — check_list_template.check_object (константы CheckObject). */
export const CheckObject = {
  WAGON: 1,
  USR: 2,
  TRIP: 3,
} as const

/** Вид значения пункта — check_list_template_item.value_kind (константы CheckValueKind). */
export const CheckValueKind = {
  STRING: 1,
  NUMBER: 2,
  DICT_VALUE: 3,
} as const

/** Шаблон ответов (check_dict). */
export interface CheckDict extends Texts {
  id: number
  sortOrder: number
  disabled: boolean
}

export type CheckDictBody = Omit<CheckDict, 'id'>

/** Вариант ответа (check_dict_value). */
export interface CheckDictValue extends Texts {
  id: number
  checkDict: number
  sortOrder: number
  disabled: boolean
}

export type CheckDictValueBody = Omit<CheckDictValue, 'id' | 'checkDict'>

/** Шаблон чек-листа (check_list_template). */
export interface CheckTemplate extends Texts {
  id: number
  /** что проверяют — CheckObject */
  checkObject: number
  sortOrder: number
  disabled: boolean
}

export type CheckTemplateBody = Omit<CheckTemplate, 'id'>

/** Пункт шаблона чек-листа (check_list_template_item): раздел (valueKind = null) или пункт со значением. */
export interface CheckItem extends Texts {
  id: number
  checkListTemplate: number
  /** раздел, в который входит пункт; null — верхний уровень */
  parentItem: number | null
  /** вид значения — CheckValueKind; null — раздел */
  valueKind: number | null
  /** шаблон ответов — у пункта с вариантом ответа */
  checkDict: number | null
  sortOrder: number
  disabled: boolean
}

export type CheckItemBody = Omit<CheckItem, 'id' | 'checkListTemplate'>

/** Норматив пункта чек-листа (check_list_item_norm). Числа — строкой или числом, как пришло (numeric). */
export interface CheckNorm {
  id: number
  checkListTemplateItem: number
  /** тип вагона — id справочника WagonType; null — для всех типов вагона */
  wagonType: number | null
  required: boolean
  numMin: number | null
  numMax: number | null
  critical: boolean
  /** неисправность заявки — id справочника FaultType; null — нарушение без заявки */
  faultType: number | null
  dbeg: string
  dend: string | null
  /** допустимые варианты ответа */
  checkDictValues: number[]
}

export type CheckNormBody = Omit<CheckNorm, 'id' | 'checkListTemplateItem' | 'dbeg' | 'dend'>

/** Пункт чек-листа для заполнения: пункт шаблона и подсказки норматива для объекта проверки. */
export interface CheckFormItem extends CheckItem {
  /** норматив для объекта проверки; null — норматива нет */
  checkListItemNorm: number | null
  required: boolean
  numMin: number | null
  numMax: number | null
  critical: boolean
  checkDictValues: number[]
  /** неисправность заявки по нарушению; null — без заявки */
  faultType: number | null
}

/** Чек-лист для заполнения. */
export interface CheckForm {
  template: CheckTemplate
  items: CheckFormItem[]
  dictValues: CheckDictValue[]
}

/** Значение пункта в завершённом чек-листе. */
export interface CheckValueDraft {
  checkListTemplateItem: number
  valueString: string | null
  valueNumber: number | null
  checkDictValue: number | null
  comment: string | null
  /** сохранённые фото, которые остаются при исправлении чек-листа */
  photos: number[]
}

/** Завершённый чек-лист (Ф-ЧЕК-03). photos — новые фото по id пункта шаблона; при исправлении шаблон и объект — из чек-листа. */
export interface CheckListDraft {
  checkListTemplate: number
  wagon: number | null
  trip: number | null
  checkedUsr: number | null
  /** когда проверка начата, ISO */
  createdDt: string
  values: CheckValueDraft[]
  photos: Map<number, Blob[]>
}

/** Шаблон чек-листа в данных. */
export interface CheckTemplateRef extends Texts {
  id: number
}

/** Заявка в данных. */
export interface RequestRef {
  id: number
  number: string
}

/** Проверка — строка списка (check_list). */
export interface CheckListRow {
  id: number
  checkListTemplate: CheckTemplateRef
  wagon: WagonRef | null
  trip: TripRef | null
  checkedUsr: UserRef | null
  createdByUsr: UserRef
  createdDt: string
  completedDt: string
  violationCount: number
  requestCount: number
}

/** Значение пункта проверки. */
export interface CheckListValue {
  id: number
  checkListTemplateItem: number
  valueString: string | null
  valueNumber: number | null
  checkDictValue: number | null
  comment: string | null
  checkListItemNorm: number | null
  violation: boolean
  repairRequest: RequestRef | null
  /** фото — id; файл — photoUrl(id) */
  photos: number[]
}

/** Карточка проверки. */
export interface CheckListDetail extends CheckListRow {
  items: CheckItem[]
  values: CheckListValue[]
  dictValues: CheckDictValue[]
}

/** Фильтр списка проверок (Ф-ЧЕК-04): даты — 'YYYY-MM-DD'. */
export interface CheckListFilter {
  template: number | null
  /** вагон — id вагона; null — все вагоны */
  wagon: number | null
  query: string
  from: string | null
  to: string | null
}

// --- проверки

export async function fetchCheckTemplates(): Promise<CheckTemplate[]> {
  return (await http.get<CheckTemplate[]>('/check-templates')).data
}

/** Чек-лист для заполнения; wagon — при проверке вагона. */
export async function fetchCheckForm(template: number, wagon: number | null): Promise<CheckForm> {
  return (await http.get<CheckForm>('/check-lists/form', { params: { template, wagon } })).data
}

export async function fetchCheckLists(filter: CheckListFilter): Promise<CheckListRow[]> {
  return (await http.get<CheckListRow[]>('/check-lists', { params: filter })).data
}

export async function fetchCheckList(id: number): Promise<CheckListDetail> {
  return (await http.get<CheckListDetail>(`/check-lists/${id}`)).data
}

/** Отправить завершённый чек-лист: Система проверяет значения и создаёт заявки по нарушениям. */
function checkForm(draft: CheckListDraft): FormData {
  const form = new FormData()
  const { photos, ...fields } = draft
  form.append('draft', new Blob([JSON.stringify(fields)], { type: 'application/json' }))
  photos.forEach((files, item) => files.forEach((f) => form.append(`photo_${item}`, f)))
  return form
}

export async function createCheckList(draft: CheckListDraft): Promise<CheckListDetail> {
  return (await http.post<CheckListDetail>('/check-lists', checkForm(draft))).data
}

/** Исправить чек-лист: автор в течение 10 минут после последнего сохранения; заявки по пунктам пересчитываются. */
export async function updateCheckList(id: number, draft: CheckListDraft): Promise<CheckListDetail> {
  return (await http.put<CheckListDetail>(`/check-lists/${id}`, checkForm(draft))).data
}

/** Адрес файла фото к значению пункта. */
export function checkPhotoUrl(id: number): string {
  return http.getUri({ url: `/check-lists/photos/${id}` })
}

// --- администрирование

export async function fetchCheckDicts(): Promise<CheckDict[]> {
  return (await http.get<CheckDict[]>('/admin/check-dicts')).data
}

export async function saveCheckDict(id: number | null, body: CheckDictBody): Promise<CheckDict> {
  return id === null
    ? (await http.post<CheckDict>('/admin/check-dicts', body)).data
    : (await http.put<CheckDict>(`/admin/check-dicts/${id}`, body)).data
}

export async function deleteCheckDict(id: number): Promise<void> {
  await http.delete(`/admin/check-dicts/${id}`)
}

export async function fetchCheckDictValues(dict: number): Promise<CheckDictValue[]> {
  return (await http.get<CheckDictValue[]>(`/admin/check-dicts/${dict}/values`)).data
}

/** Сохранить вариант ответа: id = null — новый вариант шаблона ответов dict. */
export async function saveCheckDictValue(
  dict: number,
  id: number | null,
  body: CheckDictValueBody,
): Promise<CheckDictValue> {
  return id === null
    ? (await http.post<CheckDictValue>(`/admin/check-dicts/${dict}/values`, body)).data
    : (await http.put<CheckDictValue>(`/admin/check-dict-values/${id}`, body)).data
}

export async function deleteCheckDictValue(id: number): Promise<void> {
  await http.delete(`/admin/check-dict-values/${id}`)
}

export async function fetchAdminCheckTemplates(): Promise<CheckTemplate[]> {
  return (await http.get<CheckTemplate[]>('/admin/check-templates')).data
}

export async function saveCheckTemplate(id: number | null, body: CheckTemplateBody): Promise<CheckTemplate> {
  return id === null
    ? (await http.post<CheckTemplate>('/admin/check-templates', body)).data
    : (await http.put<CheckTemplate>(`/admin/check-templates/${id}`, body)).data
}

export async function deleteCheckTemplate(id: number): Promise<void> {
  await http.delete(`/admin/check-templates/${id}`)
}

export async function fetchCheckItems(template: number): Promise<CheckItem[]> {
  return (await http.get<CheckItem[]>(`/admin/check-templates/${template}/items`)).data
}

/** Сохранить пункт: id = null — новый пункт шаблона template. */
export async function saveCheckItem(template: number, id: number | null, body: CheckItemBody): Promise<CheckItem> {
  return id === null
    ? (await http.post<CheckItem>(`/admin/check-templates/${template}/items`, body)).data
    : (await http.put<CheckItem>(`/admin/check-items/${id}`, body)).data
}

export async function deleteCheckItem(id: number): Promise<void> {
  await http.delete(`/admin/check-items/${id}`)
}

export async function fetchCheckNorms(item: number): Promise<CheckNorm[]> {
  return (await http.get<CheckNorm[]>(`/admin/check-items/${item}/norms`)).data
}

/** Сохранить норматив: id = null — новый норматив пункта item. По нормативу проверены значения — сервер заводит новый. */
export async function saveCheckNorm(item: number, id: number | null, body: CheckNormBody): Promise<CheckNorm> {
  return id === null
    ? (await http.post<CheckNorm>(`/admin/check-items/${item}/norms`, body)).data
    : (await http.put<CheckNorm>(`/admin/check-norms/${id}`, body)).data
}

export async function deleteCheckNorm(id: number): Promise<void> {
  await http.delete(`/admin/check-norms/${id}`)
}
