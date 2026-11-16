import { computed, reactive, ref, shallowRef, watch } from 'vue'

import type { CheckForm, CheckFormItem, CheckListDetail, CheckTemplate } from '@/api/checks'
import {
  CheckObject,
  CheckValueKind,
  createCheckList,
  fetchCheckForm,
  fetchCheckTemplates,
  updateCheckList,
} from '@/api/checks'
import type { CrewMember, TripOption } from '@/api/trips'
import { fetchTripCrew, fetchTripOptions } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { Busy } from '@/shared/busy'
import { TripStatus } from '@/shared/constants/systemDicts'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import { checkReason } from './rights'
import { treeRows } from './tree'

/**
 * Значение пункта в памяти страницы, пока проверка не окончена. Число — строкой, как введено. photos — новые фото,
 * stored — сохранённые фото (исправление чек-листа).
 */
export interface CheckFillValue {
  valueString: string
  valueNumber: string
  checkDictValue: number | null
  comment: string
  photos: Blob[]
  stored: number[]
}

/** Вагон проверки: выбран в списке вагонов. */
export interface CheckFillWagon {
  id: number
  number: string
  wagonType: number
}

/**
 * Проведение проверки по чек-листу (Ф-ЧЕК-03) — общее для десктопа и мобильной части. Шаблоны — только те, которые
 * пользователь может провести по объекту проверки. Выбраны шаблон и объект проверки — загружается чек-лист для
 * заполнения; значения живут в памяти страницы и уходят на сервер один раз по «Проверка окончена». Без связи проверка
 * не работает: ошибка запроса показывается. Исправление чек-листа (startEdit): шаблон и объект проверки — из
 * чек-листа, значения, описания и фото — сохранённые.
 *
 * @param busy ожидание фрейма: в него входит загрузка шаблонов и чек-листа
 */
export function useCheckFill(busy: Busy) {
  const templates = shallowRef<CheckTemplate[]>([])
  const templateId = ref<number | null>(null)
  const wagon = shallowRef<CheckFillWagon | null>(null)
  const trip = ref<number | null>(null)
  const checkedUsr = ref<number | null>(null)
  /** исправляемый чек-лист; null — новый чек-лист */
  const editing = shallowRef<CheckListDetail | null>(null)
  const form = shallowRef<CheckForm | null>(null)
  const values = reactive(new Map<number, CheckFillValue>())
  /** ошибка загрузки шаблонов или чек-листа */
  const loadError = ref<ShownError | null>(null)
  /** ошибка отправки или проверки перед отправкой */
  const sendError = ref<ShownError | null>(null)
  const sending = ref(false)
  let startedAt = ''

  const template = computed(() => templates.value.find((x) => x.id === templateId.value) ?? null)
  const checkObject = computed(() => template.value?.checkObject ?? null)

  // рейсы для проверки сотрудника и рейса: выполняются и запланированы
  const trips = shallowRef<TripOption[]>([])
  const tripLoad = useLoad(async () => {
    trips.value = await fetchTripOptions({
      route: null,
      station: null,
      from: null,
      to: null,
      states: [TripStatus.IN_PROGRESS, TripStatus.PLANNED],
    })
  })
  // бригада выбранного рейса — для проверки сотрудника
  const crew = shallowRef<CrewMember[]>([])
  const crewLoad = useLoad(async () => {
    crew.value = trip.value === null ? [] : (await fetchTripCrew(trip.value)).crew
  })

  async function loadTemplates() {
    loadError.value = null
    try {
      const all = await busy.run(fetchCheckTemplates())
      templates.value = all.filter((x) => checkReason(x.checkObject) === '')
    } catch (e) {
      loadError.value = shownError(e)
    }
  }

  /** Выбран ли объект проверки для текущего шаблона. */
  const objectChosen = computed(() => {
    switch (checkObject.value) {
      case CheckObject.WAGON:
        return wagon.value !== null
      case CheckObject.USR:
        return trip.value !== null && checkedUsr.value !== null
      case CheckObject.TRIP:
        return trip.value !== null
      default:
        return false
    }
  })

  async function loadForm() {
    form.value = null
    values.clear()
    sendError.value = null
    if (templateId.value === null || !objectChosen.value) return
    loadError.value = null
    try {
      const f = await busy.run(fetchCheckForm(templateId.value, wagon.value?.id ?? null))
      for (const item of f.items) {
        if (item.valueKind !== null) {
          const saved = editing.value?.values.find((v) => v.checkListTemplateItem === item.id)
          values.set(item.id, {
            valueString: saved?.valueString ?? '',
            valueNumber:
              saved?.valueNumber === null || saved?.valueNumber === undefined ? '' : String(saved.valueNumber),
            checkDictValue: saved?.checkDictValue ?? null,
            comment: saved?.comment ?? '',
            photos: [],
            stored: [...(saved?.photos ?? [])],
          })
        }
      }
      startedAt = isoDateTime(new Date())
      form.value = f
    } catch (e) {
      loadError.value = shownError(e)
    }
  }

  watch(checkObject, (kind) => {
    if ((kind === CheckObject.USR || kind === CheckObject.TRIP) && trips.value.length === 0) void tripLoad.run()
  })
  watch(trip, () => {
    // исправление: сотрудник задан вместе с рейсом — не сбрасывается
    if (!editing.value || editing.value.trip?.id !== trip.value) checkedUsr.value = null
    if (checkObject.value === CheckObject.USR) void crewLoad.run()
  })

  /** Исправить сохранённый чек-лист: шаблон, объект проверки и значения — из чек-листа. */
  function startEdit(detail: CheckListDetail) {
    editing.value = detail
    templateId.value = detail.checkListTemplate.id
    if (detail.wagon) {
      wagon.value = { id: detail.wagon.id, number: detail.wagon.number, wagonType: detail.wagon.wagonType }
    } else {
      trip.value = detail.trip?.id ?? null
      checkedUsr.value = detail.checkedUsr?.id ?? null
    }
  }
  watch([templateId, () => wagon.value?.id, trip, checkedUsr], loadForm)

  /** Пункты чек-листа в порядке дерева с глубиной. */
  const rows = computed(() => (form.value ? treeRows(form.value.items) : []))

  /** Варианты ответа шаблона ответов пункта. */
  function dictValuesOf(item: CheckFormItem) {
    return (form.value?.dictValues ?? []).filter((v) => v.checkDict === item.checkDict)
  }

  /** Число из строки ввода: запятая — как точка; пусто — null; не число — NaN. */
  function parseNumber(s: string): number | null {
    const v = s.trim().replace(',', '.')
    return v === '' ? null : Number(v)
  }

  /** Нарушение по введённому значению — то же правило, что на сервере; сервер проверяет сам. */
  function violation(item: CheckFormItem): boolean {
    const v = values.get(item.id)
    if (!v || item.checkListItemNorm === null) return false
    if (item.valueKind === CheckValueKind.NUMBER) {
      const n = parseNumber(v.valueNumber)
      if (n === null || Number.isNaN(n)) return false
      return (item.numMin !== null && n < Number(item.numMin)) || (item.numMax !== null && n > Number(item.numMax))
    }
    if (item.valueKind === CheckValueKind.DICT_VALUE) {
      return (
        v.checkDictValue !== null && item.checkDictValues.length > 0 && !item.checkDictValues.includes(v.checkDictValue)
      )
    }
    return false
  }

  const violationCount = computed(() => (form.value?.items ?? []).filter((i) => violation(i)).length)

  /**
   * «Проверка окончена»: проверить обязательные пункты и числа, отправить чек-лист.
   *
   * @return сохранённая проверка; null — не отправлено (ошибка в sendError)
   */
  async function submit(): Promise<CheckListDetail | null> {
    const f = form.value
    if (!f || templateId.value === null) return null
    sendError.value = null
    for (const item of f.items) {
      const v = values.get(item.id)
      if (!v) continue
      const n = parseNumber(v.valueNumber)
      if (item.valueKind === CheckValueKind.NUMBER && n !== null && Number.isNaN(n)) {
        sendError.value = formError(t('Неверное число: {item}', { item: item.textRu }))
        return null
      }
      const empty =
        (item.valueKind === CheckValueKind.STRING && !v.valueString.trim()) ||
        (item.valueKind === CheckValueKind.NUMBER && n === null) ||
        (item.valueKind === CheckValueKind.DICT_VALUE && v.checkDictValue === null)
      if (item.required && empty) {
        sendError.value = formError(t('Заполните пункт: {item}', { item: item.textRu }))
        return null
      }
    }
    sending.value = true
    try {
      const photos = new Map<number, Blob[]>()
      values.forEach((v, id) => {
        if (v.photos.length > 0) photos.set(id, [...v.photos])
      })
      const kind = checkObject.value
      const draft = {
        checkListTemplate: templateId.value,
        wagon: kind === CheckObject.WAGON ? (wagon.value?.id ?? null) : null,
        trip: kind === CheckObject.WAGON ? null : trip.value,
        checkedUsr: kind === CheckObject.USR ? checkedUsr.value : null,
        createdDt: startedAt,
        values: f.items
          .filter((i) => i.valueKind !== null)
          .map((i) => {
            const v = values.get(i.id)!
            return {
              checkListTemplateItem: i.id,
              valueString: i.valueKind === CheckValueKind.STRING ? v.valueString.trim() || null : null,
              valueNumber: i.valueKind === CheckValueKind.NUMBER ? parseNumber(v.valueNumber) : null,
              checkDictValue: i.valueKind === CheckValueKind.DICT_VALUE ? v.checkDictValue : null,
              comment: v.comment.trim() || null,
              photos: v.stored,
            }
          }),
        photos,
      }
      return editing.value ? await updateCheckList(editing.value.id, draft) : await createCheckList(draft)
    } catch (e) {
      sendError.value = shownError(e)
      return null
    } finally {
      sending.value = false
    }
  }

  return {
    templates,
    templateId,
    template,
    checkObject,
    wagon,
    trip,
    checkedUsr,
    editing,
    startEdit,
    trips,
    tripLoad,
    crew,
    crewLoad,
    form,
    rows,
    values,
    loadError,
    sendError,
    sending,
    violationCount,
    loadTemplates,
    loadForm,
    dictValuesOf,
    violation,
    submit,
  }
}
