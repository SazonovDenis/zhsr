import { computed, reactive, ref, shallowRef, watch } from 'vue'

import type { EquipForm, EquipFormItem, EquipListDetail, EquipTemplate, EquipValueDraft } from '@/api/equip'
import { createEquipList, EquipQuantityKind, fetchEquipForm, fetchEquipTemplates, updateEquipList } from '@/api/equip'
import { t } from '@/boot/i18n'
import type { Busy } from '@/shared/busy'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import type { CheckFillWagon } from './useCheckFill'
import { numText } from './texts'
import { treeRows } from './tree'

/** Примечание и фото позиции в памяти страницы. stored — сохранённые фото (исправление описи), photos — новые. */
export interface EquipFillExtra {
  comment: string
  photos: Blob[]
  stored: number[]
}

/**
 * Составление описи экипировки (Ф-ЭКП-02) — общее для десктопа и мобильной части. Выбраны шаблон и вагон —
 * загружается опись: требуемое количество для вагона и количество из прошлой описи; фактическое количество по
 * умолчанию — из прошлой описи. Значения живут в памяти страницы и уходят на сервер по «Опись окончена».
 * Исправление описи (startEdit): шаблон и вагон — из описи, значения, примечания и фото — сохранённые; «было» — из
 * описи до исправляемой.
 *
 * @param busy ожидание фрейма: в него входит загрузка шаблонов и описи
 */
export function useEquipFill(busy: Busy) {
  const templates = shallowRef<EquipTemplate[]>([])
  const templateId = ref<number | null>(null)
  const wagon = shallowRef<CheckFillWagon | null>(null)
  const form = shallowRef<EquipForm | null>(null)
  /** фактическое количество по пункту — строкой, как введено */
  const quantities = reactive(new Map<number, string>())
  /** примечание и фото по пункту */
  const extras = reactive(new Map<number, EquipFillExtra>())
  /** исправляемая опись; null — новая опись */
  const editing = shallowRef<EquipListDetail | null>(null)
  const loadError = ref<ShownError | null>(null)
  const sendError = ref<ShownError | null>(null)
  const sending = ref(false)
  let startedAt = ''

  const template = computed(() => templates.value.find((x) => x.id === templateId.value) ?? null)

  async function loadTemplates() {
    loadError.value = null
    try {
      templates.value = await busy.run(fetchEquipTemplates())
    } catch (e) {
      loadError.value = shownError(e)
    }
  }

  async function loadForm() {
    form.value = null
    quantities.clear()
    extras.clear()
    sendError.value = null
    if (templateId.value === null || wagon.value === null) return
    loadError.value = null
    const edit = editing.value
    try {
      const f = await busy.run(fetchEquipForm(templateId.value, wagon.value.id, edit?.id ?? null))
      for (const item of f.items) {
        if (item.unit === null) continue
        const saved = edit?.values.find((v) => v.equipListTemplateItem === item.id)
        quantities.set(item.id, numText(saved ? saved.quantityActual : item.quantityPrevious))
        extras.set(item.id, { comment: saved?.comment ?? '', photos: [], stored: [...(saved?.photos ?? [])] })
      }
      startedAt = isoDateTime(new Date())
      form.value = f
    } catch (e) {
      loadError.value = shownError(e)
    }
  }
  watch([templateId, () => wagon.value?.id], loadForm)

  /** Исправить сохранённую опись: шаблон, вагон и значения — из описи. */
  function startEdit(detail: EquipListDetail) {
    editing.value = detail
    templateId.value = detail.equipListTemplate.id
    wagon.value = { id: detail.wagon.id, number: detail.wagon.number, wagonType: detail.wagon.wagonType }
  }

  const rows = computed(() => (form.value ? treeRows(form.value.items) : []))

  function parse(s: string | undefined): number | null {
    const v = (s ?? '').trim().replace(',', '.')
    return v === '' ? null : Number(v)
  }

  /** Фактическое количество позиции; null — не введено или не число. */
  function actual(item: EquipFormItem): number | null {
    const n = parse(quantities.get(item.id))
    return n === null || Number.isNaN(n) ? null : n
  }

  /** Нарушение — то же правило, что на сервере: «ровно» — не равно требуемому, «не менее» — меньше требуемого. */
  function violation(item: EquipFormItem): boolean {
    const n = actual(item)
    if (n === null || item.quantityRequired === null) return false
    const required = Number(item.quantityRequired)
    return item.quantityKind === EquipQuantityKind.EXACT ? n !== required : n < required
  }

  /** Количество меньше прошлой описи: подсвечивается. */
  function decreased(item: EquipFormItem): boolean {
    const n = actual(item)
    return n !== null && item.quantityPrevious !== null && n < Number(item.quantityPrevious)
  }

  const violationCount = computed(() => (form.value?.items ?? []).filter((i) => violation(i)).length)

  /**
   * «Опись окончена»: проверить, что по каждой позиции введено количество, отправить опись.
   *
   * @return сохранённая опись; null — не отправлено (ошибка в sendError)
   */
  async function submit(): Promise<EquipListDetail | null> {
    const f = form.value
    if (!f || templateId.value === null || !wagon.value) return null
    sendError.value = null
    const values: EquipValueDraft[] = []
    const photos = new Map<number, Blob[]>()
    for (const item of f.items) {
      if (item.unit === null) continue
      const n = parse(quantities.get(item.id))
      if (n === null || Number.isNaN(n) || n < 0) {
        sendError.value = formError(t('Укажите количество, не меньше нуля: {item}', { item: item.textRu }))
        return null
      }
      const extra = extras.get(item.id)
      values.push({
        equipListTemplateItem: item.id,
        quantityActual: n,
        comment: extra?.comment.trim() || null,
        photos: extra?.stored ?? [],
      })
      if (extra && extra.photos.length > 0) photos.set(item.id, [...extra.photos])
    }
    sending.value = true
    try {
      const draft = { equipListTemplate: templateId.value, wagon: wagon.value.id, createdDt: startedAt, values, photos }
      return editing.value ? await updateEquipList(editing.value.id, draft) : await createEquipList(draft)
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
    wagon,
    form,
    rows,
    quantities,
    extras,
    editing,
    startEdit,
    loadError,
    sendError,
    sending,
    violationCount,
    loadTemplates,
    loadForm,
    violation,
    decreased,
    submit,
  }
}
