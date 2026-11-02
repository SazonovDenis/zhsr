import type { Ref } from 'vue'
import { computed, ref, shallowRef, watch } from 'vue'

import type { RequestAction, RequestDetail, UserRef } from '@/api/requests'
import { addRequestNote, assignRequest, fetchRequest, fetchServiceStaff, runRequestAction } from '@/api/requests'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { isOnline } from '@/shared/network'
import { loadCached } from '@/shared/offline/cache'
import { currentUser } from '@/shared/user'
import { ACTIONS, canAssign, canEdit, editBlockReason, requestActions } from './rights'

/**
 * Карточка заявки — общее для правой панели десктопа и экрана заявки на телефоне: загрузка заявки, кнопки действий
 * по правам пользователя, ввод комментария к действию, назначение исполнителя и пояснение с фото.
 *
 * offline — мобильная часть: заявка сохраняется на телефоне и без связи показывается из копии; действия без связи
 * недоступны.
 */
export function useRequestCard(id: Ref<string | null>, offline = false) {
  const request = shallowRef<RequestDetail | null>(null)
  const loading = ref(false)
  /** заявка показана из копии на телефоне */
  const stale = ref(false)
  const error = ref('')
  /** идёт действие: кнопки недоступны */
  const running = ref(false)

  /** действие, для которого вводится комментарий; null — ввод закрыт */
  const pending = ref<RequestAction | null>(null)
  const comment = ref('')

  const staff = shallowRef<UserRef[]>([])

  async function load() {
    const current = id.value
    pending.value = null
    error.value = ''
    if (!current) {
      request.value = null
      return
    }
    loading.value = true
    try {
      const result = offline
        ? await loadCached(`request:${current}`, () => fetchRequest(current))
        : { data: await fetchRequest(current), stale: false }
      if (id.value !== current) return
      request.value = result.data
      stale.value = result.stale
    } catch (e) {
      error.value = e instanceof Error ? t(e.message) : String(e)
      request.value = null
    } finally {
      loading.value = false
    }
  }

  watch(id, load, { immediate: true })

  const user = computed(() => currentUser.value)

  const actions = computed(() =>
    request.value && user.value
      ? requestActions(user.value, request.value).map((a) => ({ action: a, ...ACTIONS[a], title: t(ACTIONS[a].title) }))
      : [],
  )
  const assignable = computed(() => !!request.value && !!user.value && canAssign(user.value, request.value))
  const editable = computed(() => !!request.value && !!user.value && canEdit(user.value, request.value))
  const editHint = computed(() =>
    request.value && user.value ? t(editBlockReason(user.value, request.value) || 'Исправить заявку') : '',
  )
  /** действия без связи недоступны */
  const blocked = computed(() => !isOnline.value || running.value)

  // кому можно назначить заявку — сотрудники её службы
  watch(
    () => (assignable.value ? request.value?.service : null),
    async (service) => {
      staff.value = service ? await fetchServiceStaff(service) : []
    },
    { immediate: true },
  )

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
    error.value = ''
    try {
      changed(await work())
      return true
    } catch (e) {
      error.value = e instanceof Error ? t(e.message) : String(e)
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
      return
    }
    void run(() => runRequestAction(request.value!.id, action))
  }

  /** Подтвердить действие с комментарием; без комментария не выполняется. */
  async function confirm() {
    if (!pending.value || !request.value || !comment.value.trim()) return
    const action = pending.value
    if (await run(() => runRequestAction(request.value!.id, action, comment.value.trim()))) pending.value = null
  }

  function cancel() {
    pending.value = null
  }

  async function assign(executor: string | null) {
    if (!request.value) return
    await run(() => assignRequest(request.value!.id, executor))
  }

  async function addNote(text: string, photos: Blob[]): Promise<boolean> {
    if (!request.value || (!text.trim() && photos.length === 0)) return false
    return run(() => addRequestNote(request.value!.id, text.trim(), photos))
  }

  return {
    request,
    loading,
    stale,
    error,
    running,
    blocked,
    actions,
    pending,
    pendingDef: computed(() => (pending.value ? ACTIONS[pending.value] : null)),
    comment,
    assignable,
    staff,
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
