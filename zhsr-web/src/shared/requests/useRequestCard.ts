import { Notify } from 'quasar'
import type { Ref } from 'vue'
import { computed, ref, shallowRef, watch } from 'vue'

import { isNetworkError } from '@/api/http'
import type { RepairRequest, RequestAction, RequestDetail, RequestPhoto, StaffCandidate } from '@/api/requests'
import {
  addRequestNote,
  addRequestPhoto,
  assignRequest,
  deleteRequestPhoto,
  fetchRequest,
  fetchServiceStaff,
  runRequestAction,
  updateRequestNote,
} from '@/api/requests'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { randomGuid } from '@/shared/db/db'
import { dictManager } from '@/shared/dict/DictManager'
import type { Busy } from '@/shared/busy'
import { useBusy } from '@/shared/busy'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { findCachedRow, loadCached } from '@/shared/offline/cache'
import { queueAction, queueAssign, queuedDecision, queueNote, queuePhotos } from '@/shared/offline/outbox'
import { useNow } from '@/shared/trips/tripTimes'
import { currentUser } from '@/shared/user'
import { provideRequestChanges } from './requestChanges'
import { ACTIONS, assignBlockReason, canAssign, canChangeOwn, canEdit, requestActions, staffTrip } from './rights'

/**
 * Карточка заявки — общее для правой панели десктопа и экрана заявки на телефоне: загрузка заявки, кнопки действий
 * по правам пользователя, ввод комментария к действию, назначение исполнителя и примечание с фото.
 *
 * offline — мобильная часть: без связи заявка показывается из строки сохранённого списка заявок, без истории и фото
 * (stale). Сама карточка на телефоне не сохраняется. На телефоне без связи действие, назначение и примечание встают
 * в очередь и отправятся сами; действие и назначение при отправке проверяются заново (outbox.ts, queueAction).
 */
/** Сохранённые списки заявок мобильной части: страница «Заявки» и главная. */
export const isRequestList = (key: string) => key.startsWith('requests:') || key === 'today'

/** Карточка с сервера; нет связи — строка сохранённого списка без истории и фото. */
export async function loadOffline(id: number): Promise<{ data: RequestDetail; stale: boolean }> {
  try {
    return { data: await fetchRequest(id), stale: false }
  } catch (e) {
    if (!isNetworkError(e)) throw e
    const row = await findCachedRow<RepairRequest>(isRequestList, id)
    if (!row) throw e
    // без связи источник заявки (чек-лист, опись) неизвестен: в строке списка его нет
    const empty = { checkList: null, checkListTemplate: null, equipList: null, equipListTemplate: null }
    return { data: { ...row, statuses: [], executors: [], notes: [], photos: [], ...empty }, stale: true }
  }
}

export function useRequestCard(id: Ref<number | null>, offline = false, busy: Busy = useBusy()) {
  const request = shallowRef<RequestDetail | null>(null)
  const loading = ref(false)
  /** заявка показана из строки сохранённого списка: истории и фото нет */
  const stale = ref(false)
  /** ошибка загрузки или действия; null — ошибки нет */
  const error = ref<ShownError | null>(null)
  /** идёт действие: кнопки недоступны */
  const running = ref(false)

  /** действие, для которого вводится комментарий; null — ввод закрыт */
  const pending = ref<RequestAction | null>(null)
  const comment = ref('')
  /** фото к действию с комментарием */
  const actionPhotos = ref<Blob[]>([])

  const staff = shallowRef<StaffCandidate[]>([])

  async function load() {
    const current = id.value
    pending.value = null
    error.value = null
    if (!current) {
      request.value = null
      return
    }
    loading.value = true
    try {
      // в счёт ожидания фрейма карточки: спиннер фрейма — на всю цепочку
      // карточка и справочники для её показа (неисправность, тип вагона, место вагона) — параллельно
      const [, result] = await busy.run(
        Promise.all([
          dictManager.prepareDicts('FaultType', 'WagonType', 'Station', 'Depot', 'Yard', 'RequestStatus'),
          offline ? loadOffline(current) : fetchRequest(current).then((data) => ({ data, stale: false })),
        ]),
      )
      if (id.value !== current) return
      request.value = result.data
      stale.value = result.stale
    } catch (e) {
      error.value = shownError(e)
      request.value = null
    } finally {
      loading.value = false
    }
  }

  watch(id, load, { immediate: true })

  const user = computed(() => currentUser.value)
  // текущее время: кнопки исправления исчезают сами, когда срок вышел
  const now = useNow()

  /** исполнитель, если он назначен и это другой сотрудник: «Взять в работу» тогда не основная кнопка и спрашивает
   * подтверждение; иначе пусто */
  const foreignExecutor = computed(() => {
    const executor = request.value?.executorUsr
    return executor && executor.id !== user.value?.id ? executor.name : ''
  })

  /** кнопки действий: все, у недоступных — причина рядом (reason, переведена) */
  const actions = computed(() => {
    const r = request.value
    if (!r || !user.value) return []
    const status = dictManager.getValue('RequestStatus', r.requestStatusType)
    return requestActions(user.value, r).map(({ action, reason }) => ({
      action,
      ...ACTIONS[action],
      title: t(ACTIONS[action].title),
      kind: action === 'TAKE' && foreignExecutor.value ? ('secondary' as const) : ACTIONS[action].kind,
      reason: reason ? t(reason, { status }) : '',
    }))
  })
  const assignable = computed(() => !!request.value && !!user.value && canAssign(user.value, request.value))
  /** назначение или переназначение: у заявки уже есть исполнитель */
  const reassign = computed(() => !!request.value?.executorUsr)
  /** почему исполнителя не назначить — «Вы не можете назначить (переназначить): …» (переведено); пусто — можно */
  const assignHint = computed(() => {
    const r = request.value
    if (!r || !user.value) return ''
    const reason = assignBlockReason(user.value, r)
    if (!reason) return ''
    const status = dictManager.getValue('RequestStatus', r.requestStatusType)
    const text = reassign.value ? 'Вы не можете переназначить: {reason}' : 'Вы не можете назначить: {reason}'
    return t(text, { reason: t(reason, { status }) })
  })
  const editable = computed(() => !!request.value && !!user.value && canEdit(user.value, request.value, now.value))
  /** действие или назначение по заявке, которое ждёт отправки с телефона; null — нет */
  const waitingDecision = computed(() => (offline && request.value ? queuedDecision(request.value.id) : null))
  /**
   * действия недоступны: идёт действие; на телефоне — пока предыдущее действие ждёт отправки (без связи действие
   * встаёт в очередь); на десктопе — без связи
   */
  const blocked = computed(() =>
    offline ? running.value || !!waitingDecision.value : !isOnline.value || running.value,
  )
  /** на телефоне без связи действие и назначение встают в очередь */
  const queueing = () => offline && !isOnline.value
  /** примечание недоступно: на телефоне оно без связи встаёт в очередь */
  const noteBlocked = computed(() => (offline ? running.value : blocked.value))

  // кому можно назначить заявку — сотрудники её службы
  watch(
    () => (assignable.value ? request.value?.service : null),
    async (service) => {
      staff.value = service && (offline || isOnline.value) ? await serviceStaff(service) : []
    },
    { immediate: true },
  )

  /**
   * Кому назначить: сотрудники службы; ЛНП — вся бригада рейса вагона (staffTrip). На телефоне без связи — сохранённый список:
   * назначение без связи встаёт в очередь.
   */
  async function serviceStaff(service: number): Promise<StaffCandidate[]> {
    const u = user.value
    const trip = u ? staffTrip(u, request.value?.wagonTrip?.id ?? null) : null
    if (!offline) return fetchServiceStaff(service, trip)
    return (await loadCached(`staff-candidates:${service}:${trip ?? ''}`, () => fetchServiceStaff(service, trip))).data
  }

  /** Загрузить сотрудников службы заново: для выбора исполнителя; ошибку показывает вызывающий. */
  async function loadStaff() {
    const service = request.value?.service
    staff.value = service ? await serviceStaff(service) : []
  }

  /** Заявка изменилась на сервере: карточка — новая, списки и счётчики — загрузить заново. */
  function changed(detail: RequestDetail) {
    request.value = detail
    stale.value = false
    bus.emit('requests-refresh')
    bus.emit('state-refresh')
  }

  async function run(work: () => Promise<RequestDetail>): Promise<boolean> {
    if (blocked.value) return false
    running.value = true
    error.value = null
    try {
      changed(await work())
      return true
    } catch (e) {
      error.value = shownError(e)
      return false
    } finally {
      running.value = false
    }
  }

  /** Кнопка действия: без комментария — сразу, с комментарием — открывается ввод комментария. */
  function start(action: RequestAction) {
    if (blocked.value || !request.value) return
    if (ACTIONS[action].comment) {
      pending.value = action
      comment.value = ''
      actionPhotos.value = []
      return
    }
    if (queueing()) {
      void queueDecided(() => queueAction(request.value!.id, action, '', [], user.value?.id ?? 0))
      return
    }
    void run(() => runRequestAction(request.value!.id, action))
  }

  /** Поставить действие или назначение в очередь телефона и сказать об этом. */
  async function queueDecided(put: () => Promise<void>) {
    await put()
    Notify.create({ message: t('Связи нет. Действие сохранено на телефоне и отправится само'), position: 'bottom' })
  }

  /** Подтвердить действие с комментарием; без комментария не выполняется. */
  async function confirm() {
    if (!pending.value || !request.value || !comment.value.trim()) return
    const action = pending.value
    if (queueing()) {
      const id = request.value.id
      const photos = actionPhotos.value
      await queueDecided(() => queueAction(id, action, comment.value.trim(), photos, user.value?.id ?? 0))
      pending.value = null
      return
    }
    if (!(await run(() => runRequestAction(request.value!.id, action, comment.value.trim())))) return
    pending.value = null
    // фото — следом, по одному, со временем действия: в истории они показываются вместе с ним
    const at = request.value.statuses.map((x) => x.dbeg).sort((a, b) => Date.parse(a) - Date.parse(b))
    const done = at[at.length - 1]
    const photos = actionPhotos.value
    if (!done || photos.length === 0) return
    const id = request.value.id
    // телефон — через очередь: уйдут и после обрыва связи; десктоп — сразу, затем карточка заново
    if (offline) {
      await queuePhotos({ request: id }, done, photos, user.value?.id ?? 0)
      return
    }
    await run(async () => {
      for (const photo of photos) await addRequestPhoto(id, photo, done, randomGuid())
      return fetchRequest(id)
    })
  }

  function cancel() {
    pending.value = null
  }

  async function assign(executor: number | null) {
    if (!request.value) return
    if (queueing()) {
      const id = request.value.id
      const name = staff.value.find((c) => c.usr.id === executor)?.usr.name ?? ''
      await queueDecided(() => queueAssign(id, executor, name, user.value?.id ?? 0))
      return
    }
    await run(() => assignRequest(request.value!.id, executor))
  }

  async function addNote(text: string, photos: Blob[]): Promise<boolean> {
    if (!request.value || (!text.trim() && photos.length === 0)) return false
    if (!offline) return run(() => addRequestNote(request.value!.id, text.trim(), photos))
    if (running.value) return false
    running.value = true
    error.value = null
    // guid с первой попытки: сервер мог принять примечание, а ответ — потеряться
    const guid = randomGuid()
    const id = request.value.id
    const author = user.value?.id ?? 0
    // текст и фото — с одним временем: в истории они показываются вместе
    const at = isoDateTime(new Date())
    try {
      if (text.trim()) changed(await addRequestNote(id, text.trim(), [], at, guid))
      await queuePhotos({ request: id }, at, photos, author)
      return true
    } catch (e) {
      if (isNetworkError(e)) {
        await queueNote({ request: id }, text.trim(), photos, author, guid, at)
        Notify.create({
          message: t('Связи нет. Примечание сохранено на телефоне и отправится само'),
          position: 'bottom',
        })
        return true
      }
      error.value = shownError(e)
      return false
    } finally {
      running.value = false
    }
  }

  /** исправить примечание и удалить фото можно только при связи и по карточке с сервера: иначе кнопок нет */
  const canChange = () => !!request.value && !!user.value && !stale.value && isOnline.value

  function canDeletePhoto(photo: RequestPhoto): boolean {
    const p = canChange() ? request.value!.photos.find((x) => x.id === photo.id) : undefined
    return !!p && canChangeOwn(user.value!, p.createdByUsr, p.createdDt, now.value)
  }

  function canEditNote(noteId: number): boolean {
    const n = canChange() ? request.value!.notes.find((x) => x.id === noteId) : undefined
    return !!n && canChangeOwn(user.value!, n.createdByUsr, n.createdDt, now.value)
  }

  /** Исправление и удаление: только при связи, в очередь не встают; ошибка — в карточке. */
  async function runChange(work: () => Promise<RequestDetail>): Promise<boolean> {
    if (running.value) return false
    running.value = true
    error.value = null
    try {
      changed(await work())
      return true
    } catch (e) {
      error.value = shownError(e)
      return false
    } finally {
      running.value = false
    }
  }

  /** Исправить текст примечания; пустой текст не сохраняется. */
  function saveNote(noteId: number, text: string): Promise<boolean> {
    if (!text.trim()) return Promise.resolve(false)
    return runChange(() => updateRequestNote(noteId, text.trim()))
  }

  /**
   * Передать вниз по дереву исправление примечаний и удаление фото (requestChanges.ts). editNote — как страница
   * открывает ввод нового текста: на десктопе — окно, на телефоне — лист снизу.
   */
  function provideChanges(editNote: (noteId: number, text: string) => void) {
    provideRequestChanges({
      canDeletePhoto,
      deletePhoto: (photo) => void runChange(() => deleteRequestPhoto(photo.id)),
      canEditNote,
      editNote,
    })
  }

  return {
    request,
    loading,
    stale,
    error,
    running,
    blocked,
    waitingDecision,
    noteBlocked,
    actions,
    foreignExecutor,
    pending,
    pendingDef: computed(() => (pending.value ? ACTIONS[pending.value] : null)),
    comment,
    actionPhotos,
    assignable,
    assignHint,
    reassign,
    staff,
    loadStaff,
    editable,
    load,
    start,
    confirm,
    cancel,
    assign,
    addNote,
    saveNote,
    provideChanges,
  }
}
