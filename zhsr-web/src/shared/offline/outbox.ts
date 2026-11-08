import { computed, ref } from 'vue'

import { isLoginRequired, isPasswordChangeRequired, isRefusal } from '@/api/http'
import type { RepairRequest, RequestAction, RequestDraft } from '@/api/requests'
import {
  addRequestNote,
  addRequestPhoto,
  assignRequest,
  createRequest,
  runRequestAction,
  updateRequest,
} from '@/api/requests'
import type { TripRef } from '@/api/wagons'
import { bus } from '@/shared/bus'
import { RequestStatus } from '@/shared/constants/systemDicts'
import type { OutboxKind, OutboxRow } from '@/shared/db/db'
import { db, randomGuid } from '@/shared/db/db'
import { isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { faultDefaults } from '@/shared/requests/faults'
import { currentUser, isServerChecked } from '@/shared/user'

/**
 * Очередь отправки на сервер (IndexedDB, таблица outbox): новые заявки, исправления, примечания к заявкам, фото,
 * действия над заявками и назначения исполнителя. Действие и назначение — пункты «списка дел»: к моменту отправки они
 * могут оказаться несвоевременными, тогда сервер отказывает и пользователь видит причину.
 * Запись ждёт на телефоне и уходит на сервер сама, когда связь появится (событие браузера online, восстановление
 * соединения). Записи уходят в том порядке, в каком сделаны, и с временем, когда сделаны
 * (created). Номер заявке присваивает сервер при отправке. Очередь уходит только после сверки локальной БД с сервером
 * (isServerChecked): иначе данные попали бы в другую серверную БД. У каждой записи свой guid: сервер принимает запись
 * один раз, и повторная отправка после обрыва связи дубля не создаёт. Запись, которая не ушла с первой попытки, встаёт
 * в очередь с тем же guid: сервер мог её принять, а ответ — потеряться.
 *
 * Записи независимы: фото, примечание и исправление уходят сами по себе, отказ по одной не трогает другие. Каждое
 * фото — своя запись. Фото и примечание к заявке, которая ещё в очереди, ссылаются на её guid (parent) и ждут только её id:
 * сервер принял заявку и вернул id — они получают ссылку на заявку.
 *
 * Записи уходят строго по одной. Сбой (нет связи, 5xx, нет входа) останавливает очередь: та же запись повторяется при
 * следующей попытке, не реже раза в минуту; о сбое говорит общий статус (serverFailed), записи он не касается. Отказ
 * сервера по существу (4xx) помечает запись refused: она больше не уходит, очередь идёт дальше, а пользователь видит у
 * записи «Не принято» и отменяет её сам.
 *
 * Уходят только записи текущего пользователя. Вход другого пользователя удаляет очередь после его подтверждения
 * (страница входа): под чужим входом записи отправлять нельзя.
 *
 * После отправки в шину уходят requests-refresh и state-refresh: списки и счётчики загружаются заново.
 */

/** Запись очереди. */
export type OutboxItem = Omit<OutboxRow, 'draft'> & { draft: Omit<RequestDraft, 'photos'> }

/** Как показать черновик, пока он в очереди: в черновике вагон и рейс — только id. */
export interface DraftView {
  wagonNumber: string
  wagonType: number
  trip: TripRef | null
}

/** Записи очереди, от новых к старым; реактивно — для шапки мобильной части. */
export const outbox = ref<OutboxItem[]>([])

/** Новые заявки в очереди, от новых к старым; реактивно — для списков мобильной части. */
export const queuedRequests = computed(() => outbox.value.filter((x) => x.kind === 'request'))

/** Сервер не отвечает, хотя телефон на связи: очередь ждёт. Общий статус для шапки мобильной части. */
export const serverFailed = ref(false)

/** Как часто повторять отправку, пока в очереди что-то ждёт, мс. */
const RETRY_INTERVAL = 60_000

let started = false
let flushing: Promise<number> | null = null

/** Есть ли что отправить и можно ли это сделать: нет связи или входа — пытаться незачем. */
function canFlush(): boolean {
  const user = currentUser.value
  return isOnline.value && !!user && outbox.value.some((x) => !x.refused && x.author === user.id)
}

/**
 * Прочитать очередь, подписаться на появление связи и запустить повтор по таймеру. Вызывается мобильной частью при
 * старте и после каждого входа: при повторном вызове только отправляет.
 */
export async function startOutbox(): Promise<void> {
  if (!started) {
    started = true
    bus.on('connection-restored', () => void flushOutbox())
    setInterval(() => {
      if (canFlush()) void flushOutbox()
    }, RETRY_INTERVAL)
    await reload()
  }
  if (isOnline.value) void flushOutbox()
}

/** На что ссылается запись: заявка на сервере или заявка из очереди — её guid. */
export type QueueTarget = { request: number } | { parent: string }

/** Новая запись очереди: всё, что не задано, — пусто; время — сейчас. */
function newRow(kind: OutboxKind, author: number, fields: Partial<OutboxRow>): OutboxRow {
  return {
    id: `local-${randomGuid()}`,
    guid: randomGuid(),
    kind,
    request: null,
    parent: null,
    draft: {},
    wagonNumber: '',
    wagonType: 0,
    trip: null,
    text: '',
    photo: null,
    created: isoDateTime(new Date()),
    author,
    error: '',
    refused: false,
    ...fields,
  }
}

/** Записи фото: каждое фото — своя запись. */
function photoRows(target: QueueTarget, at: string, photos: Blob[], author: number): OutboxRow[] {
  return photos.map((photo) => newRow('photo', author, { ...target, photo, created: at }))
}

/** Поля записи из черновика, без фото: фото — свои записи. */
function draftFields(draft: RequestDraft, view: DraftView): Partial<OutboxRow> {
  const fields: Omit<RequestDraft, 'photos'> & { photos?: Blob[] } = { ...draft }
  delete fields.photos
  return { draft: { ...fields }, ...view }
}

/** Время черновика: с первой попытки отправки, иначе сейчас. */
const draftTime = (draft: RequestDraft) => draft.clientDt ?? isoDateTime(new Date())

/** Поставить новую заявку в очередь; её фото ждут её id. */
export async function queueRequest(draft: RequestDraft, author: number, view: DraftView): Promise<void> {
  const at = draftTime(draft)
  const guid = draft.clientGuid ?? randomGuid()
  const row = newRow('request', author, { ...draftFields(draft, view), guid, created: at })
  await db.outbox.bulkPut([row, ...photoRows({ parent: guid }, at, draft.photos, author)])
  await reload()
}

/**
 * Исправить заявку, которая ждёт в очереди; время создания и guid остаются прежними: это та же заявка. Новые фото —
 * свои записи. Возвращает false, если заявки в очереди уже нет: пока её исправляли, она ушла на сервер.
 */
export async function updateQueuedRequest(id: string, draft: RequestDraft, view: DraftView): Promise<boolean> {
  const row = await db.outbox.get(id)
  if (!row) return false
  await db.outbox.update(id, { ...draftFields(draft, view), error: '', refused: false })
  await db.outbox.bulkPut(photoRows({ parent: row.guid }, draftTime(draft), draft.photos, row.author))
  await reload()
  return true
}

/**
 * Поставить в очередь исправление заявки с сервера. Исправление этой заявки уже ждёт — новое его заменяет: оно
 * заполнено из ожидающего. Новые фото — свои записи.
 */
export async function queueEdit(request: number, draft: RequestDraft, author: number, view: DraftView): Promise<void> {
  const at = draftTime(draft)
  const before = queuedEdit(request)
  const fields = {
    request,
    ...draftFields(draft, view),
    guid: draft.clientGuid ?? randomGuid(),
    created: at,
    ...(before ? { id: before.id } : {}),
  }
  await db.outbox.bulkPut([newRow('edit', author, fields), ...photoRows({ request }, at, draft.photos, author)])
  await reload()
}

/**
 * Поставить в очередь примечание: текст и фото — свои записи с одним временем. guid — тот, с которым текст не ушёл с
 * первой попытки; at — время первой попытки.
 */
export async function queueNote(
  target: QueueTarget,
  text: string,
  photos: Blob[],
  author: number,
  guid = randomGuid(),
  at = isoDateTime(new Date()),
): Promise<void> {
  const note = text ? [newRow('note', author, { ...target, text, guid, created: at })] : []
  await db.outbox.bulkPut([...note, ...photoRows(target, at, photos, author)])
  await reload()
}

/**
 * Поставить в очередь действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07) с комментарием и фото. Это пункт «списка дел»: при
 * отправке сервер проверяет его заново и отказывает, если после него у заявки сменились статус или исполнитель, —
 * пользователь видит у записи «Не принято» с причиной.
 */
export async function queueAction(
  request: number,
  action: RequestAction,
  comment: string,
  photos: Blob[],
  author: number,
): Promise<void> {
  const at = isoDateTime(new Date())
  const row = newRow('action', author, { request, action, text: comment, created: at })
  await db.outbox.bulkPut([row, ...photoRows({ request }, at, photos, author)])
  await reload()
  void flushOutbox()
}

/**
 * Поставить в очередь назначение исполнителя (Ф-ЗАЯ-02); executor = null — снять назначение, name — имя для показа.
 * Проверяется при отправке так же, как действие.
 */
export async function queueAssign(
  request: number,
  executor: number | null,
  name: string,
  author: number,
): Promise<void> {
  await db.outbox.put(newRow('assign', author, { request, executor, text: name }))
  await reload()
  void flushOutbox()
}

/** Действие или назначение заявки, которое ждёт отправки; нет — null. Пока оно ждёт, новое не ставится. */
export function queuedDecision(request: number): OutboxItem | null {
  return (
    outbox.value.find((x) => (x.kind === 'action' || x.kind === 'assign') && x.request === request && !x.refused) ??
    null
  )
}

/** Поставить фото в очередь и отправить: при связи они идут вслед за заявкой или примечанием. */
export async function queuePhotos(target: QueueTarget, at: string, photos: Blob[], author: number): Promise<void> {
  await db.outbox.bulkPut(photoRows(target, at, photos, author))
  await reload()
  void flushOutbox()
}

/**
 * Убрать запись из очереди. Вместе с исправлением или примечанием уходят фото, приложенные с ними: то же время. Вместе
 * с заявкой — её фото и примечания: без заявки им негде быть.
 */
export async function removeQueued(item: OutboxItem): Promise<void> {
  await db.outbox
    .filter(
      (x) =>
        x.id === item.id ||
        (item.kind === 'request' && x.parent === item.guid) ||
        (item.kind !== 'photo' &&
          x.kind === 'photo' &&
          x.created === item.created &&
          x.request === item.request &&
          x.parent === item.parent),
    )
    .delete()
  await reload()
}

/** Удалить всю очередь: вошёл другой пользователь и подтвердил это. */
export async function clearOutbox(): Promise<void> {
  await db.outbox.clear()
  await reload()
}

/** Заявка из очереди по локальному id; нет — null (уже отправлена). */
export function outboxItem(id: string): OutboxItem | null {
  return queuedRequests.value.find((x) => x.id === id) ?? null
}

/** Что ждёт отправки к заявке — id на сервере или guid заявки из очереди: от старых к новым. */
export function queuedFor(request: number | string): OutboxItem[] {
  return outbox.value.filter((x) => x.request === request || x.parent === request).reverse()
}

/** Исправление заявки с сервера, которое ждёт отправки; нет — null. */
export function queuedEdit(request: number): OutboxItem | null {
  return outbox.value.find((x) => x.kind === 'edit' && x.request === request) ?? null
}

/** Заявка с сервера с учётом исправления, которое ждёт отправки. */
export function withQueuedEdit<T extends RepairRequest>(r: T): T {
  const edit = queuedEdit(r.id)
  if (!edit) return r
  return {
    ...r,
    wagon: { ...r.wagon, id: edit.draft.wagon, number: edit.wagonNumber, wagonType: edit.wagonType },
    trip: edit.trip,
    service: faultDefaults(edit.draft.faultType).service,
    faultType: edit.draft.faultType,
    severityType: edit.draft.severityType,
    description: edit.draft.description,
  }
}

/**
 * Отправить очередь по порядку. Возвращает, сколько записей ушло. Пропала связь — отправка останавливается до
 * следующей попытки; сервер не принял запись — она остаётся в очереди с текстом ошибки.
 */
export function flushOutbox(): Promise<number> {
  flushing ??= send().finally(() => (flushing = null))
  return flushing
}

/** Отправить запись на сервер с временем, когда она сделана, и убрать из очереди. */
async function deliver(row: OutboxRow): Promise<void> {
  const draft = {
    ...(row.draft as Omit<RequestDraft, 'photos'>),
    photos: [],
    clientDt: row.created,
    clientGuid: row.guid,
  }
  if (row.kind === 'request') {
    const created = await createRequest(draft)
    // сервер вернул id заявки: фото и примечания, которые её ждали, получают ссылку на неё
    await db.transaction('rw', db.outbox, async () => {
      await db.outbox.filter((x) => x.parent === row.guid).modify({ request: created.id, parent: null })
      await db.outbox.delete(row.id)
    })
    return
  }
  if (row.kind === 'edit') await updateRequest(row.request!, draft)
  else if (row.kind === 'note') await addRequestNote(row.request!, row.text, [], row.created, row.guid)
  else if (row.kind === 'action')
    await runRequestAction(row.request!, row.action as RequestAction, row.text, row.created, row.guid)
  else if (row.kind === 'assign') await assignRequest(row.request!, row.executor ?? null, row.created, row.guid)
  else await addRequestPhoto(row.request!, row.photo!, row.created, row.guid)
  await db.outbox.delete(row.id)
}

/** Следующая запись на отправку: своя, сервер ей не отказал, и id заявки уже есть. */
async function nextRow(): Promise<OutboxRow | undefined> {
  const author = currentUser.value?.id
  const rows = await db.outbox.orderBy('created').toArray()
  return rows.find((x) => x.author === author && !x.refused && (x.kind === 'request' || x.request !== null))
}

async function send(): Promise<number> {
  let sent = 0
  if (!(await isServerChecked())) return sent
  // очередь читается заново на каждом шаге: принятая заявка даёт своим фото и примечаниям id
  for (let row = await nextRow(); row; row = await nextRow()) {
    try {
      await deliver(row)
      serverFailed.value = false
      sent++
      // страницы видят ход отправки: счётчик, фото в карточке
      await reload()
    } catch (e) {
      if (isRefusal(e)) {
        await db.outbox.update(row.id, { error: e instanceof Error ? e.message : String(e), refused: true })
        await reload()
        continue
      }
      // сбой — запись ни при чём: она повторится при следующей попытке, вперёд неё ничего не уходит
      serverFailed.value = !isLoginRequired(e) && !isPasswordChangeRequired(e)
      break
    }
  }
  await reload()
  if (sent > 0) {
    bus.emit('requests-refresh')
    bus.emit('state-refresh')
  }
  return sent
}

async function reload() {
  const rows = await db.outbox.orderBy('created').reverse().toArray()
  outbox.value = rows.map(toItem)
}

function toItem(row: OutboxRow): OutboxItem {
  return { ...row, draft: row.draft as Omit<RequestDraft, 'photos'> }
}

/** Заявка из очереди в виде строки списка: id — локальный, «local-…». */
export type QueuedRequest = Omit<RepairRequest, 'id'> & { id: string; guid: string; refused: boolean }

/** Заявка из очереди в виде строки списка: номера ещё нет, вагон и рейс — из выбора при подаче. */
export function outboxAsRequest(item: OutboxItem, authorName: string): QueuedRequest {
  return {
    id: item.id,
    guid: item.guid,
    number: '',
    wagon: { id: item.draft.wagon, number: item.wagonNumber, wagonType: item.wagonType },
    locationType: null,
    place: null,
    wagonTrip: null,
    wagonStation: null,
    trip: item.trip,
    service: faultDefaults(item.draft.faultType).service,
    faultType: item.draft.faultType,
    severityType: item.draft.severityType,
    createdByUsr: { id: item.author, name: authorName },
    createdDt: item.created,
    receivedDt: item.created,
    description: item.draft.description,
    requestStatusType: RequestStatus.NEW,
    executorUsr: null,
    photoCount: queuedFor(item.guid).filter((x) => x.kind === 'photo').length,
    refused: item.refused,
  }
}
