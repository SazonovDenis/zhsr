import { Notify } from 'quasar'
import type { Ref } from 'vue'
import { computed, ref, shallowRef, watch } from 'vue'

import { isNetworkError } from '@/api/http'
import type { RepairRequest, RequestAction, RequestDetail, UserRef } from '@/api/requests'
import {
  addRequestNote,
  addRequestPhoto,
  assignRequest,
  fetchRequest,
  fetchServiceStaff,
  runRequestAction,
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
import { findCachedRow } from '@/shared/offline/cache'
import { queueNote, queuePhotos } from '@/shared/offline/outbox'
import { currentUser } from '@/shared/user'
import { ACTIONS, canAssign, canEdit, editBlockReason, requestActions } from './rights'

/**
 * Карточка заявки — общее для правой панели десктопа и экрана заявки на телефоне: загрузка заявки, кнопки действий
 * по правам пользователя, ввод комментария к действию, назначение исполнителя и примечание с фото.
 *
 * offline — мобильная часть: без связи заявка показывается из строки сохранённого списка заявок, без истории и фото
 * (stale). Сама карточка на телефоне не сохраняется. Действия без связи недоступны; примечание без связи встаёт в
 * очередь на телефоне и отправится само.
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
    return { data: { ...row, statuses: [], executors: [], notes: [], photos: [] }, stale: true }
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

  const staff = shallowRef<UserRef[]>([])

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
          dictManager.prepareDicts('FaultType', 'WagonType', 'Station', 'Depot', 'Yard'),
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

  /** исполнитель, если он назначен и это другой сотрудник: «Взять в работу» тогда не основная кнопка и спрашивает
   * подтверждение; иначе пусто */
  const foreignExecutor = computed(() => {
    const executor = request.value?.executorUsr
    return executor && executor.id !== user.value?.id ? executor.name : ''
  })

  const actions = computed(() =>
    request.value && user.value
      ? requestActions(user.value, request.value).map((a) => ({
          action: a,
          ...ACTIONS[a],
          title: t(ACTIONS[a].title),
          kind: a === 'TAKE' && foreignExecutor.value ? ('secondary' as const) : ACTIONS[a].kind,
        }))
      : [],
  )
  const assignable = computed(() => !!request.value && !!user.value && canAssign(user.value, request.value))
  const editable = computed(() => !!request.value && !!user.value && canEdit(user.value, request.value))
  const editHint = computed(() =>
    request.value && user.value ? t(editBlockReason(user.value, request.value) || 'Исправить заявку') : '',
  )
  /** действия без связи недоступны */
  const blocked = computed(() => !isOnline.value || running.value)
  /** примечание недоступно: на телефоне оно без связи встаёт в очередь */
  const noteBlocked = computed(() => (offline ? running.value : blocked.value))

  // кому можно назначить заявку — сотрудники её службы
  watch(
    () => (assignable.value ? request.value?.service : null),
    async (service) => {
      staff.value = service && isOnline.value ? await fetchServiceStaff(service) : []
    },
    { immediate: true },
  )

  /** Загрузить сотрудников службы заново: для выбора исполнителя; ошибку показывает вызывающий. */
  async function loadStaff() {
    const service = request.value?.service
    staff.value = service ? await fetchServiceStaff(service) : []
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
    void run(() => runRequestAction(request.value!.id, action))
  }

  /** Подтвердить действие с комментарием; без комментария не выполняется. */
  async function confirm() {
    if (!pending.value || !request.value || !comment.value.trim()) return
    const action = pending.value
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

  return {
    request,
    loading,
    stale,
    error,
    running,
    blocked,
    noteBlocked,
    actions,
    foreignExecutor,
    pending,
    pendingDef: computed(() => (pending.value ? ACTIONS[pending.value] : null)),
    comment,
    actionPhotos,
    assignable,
    staff,
    loadStaff,
    editable,
    editHint,
    load,
    start,
    confirm,
    cancel,
    assign,
    addNote,
  }
}
