<template>
  <PwaPage ref="page" :stale="stale">
    <div class="requests__pills">
      <button
        v-for="s in scopes"
        :key="s.id"
        type="button"
        class="requests__pill q-py-none q-px-md"
        :class="{ 'requests__pill--on': scope === s.id }"
        :disabled="mode === 'unsent'"
        @click="scope = s.id"
      >
        {{ t(s.text) }}
      </button>
    </div>
    <div class="requests__pills">
      <button
        v-for="m in modes"
        :key="m.id"
        type="button"
        class="requests__pill q-py-none q-px-md"
        :class="{ 'requests__pill--on': mode === m.id }"
        @click="mode = m.id"
      >
        {{ t(m.text) }}
      </button>
    </div>

    <ErrorBox v-if="error" :error="error" :retry="load" class="requests__error q-py-sm q-px-md" />
    <LoadSpinner v-if="busy.active.value" />

    <RequestSection :title="t(MODE_TITLE[mode])" :requests="shown" :queued="queued" :empty="t('Ничего не найдено')" />

    <template #fab>
      <router-link :to="{ name: 'pwa-request-new' }" class="requests__fab" :title="t('Новая заявка')">
        <q-icon name="add" size="1.8rem" />
      </router-link>
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, useTemplateRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'

import { isNetworkError } from '@/api/http'
import type { RepairRequest, RequestScope } from '@/api/requests'
import { fetchRequests, requestFilter } from '@/api/requests'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { dictManager } from '@/shared/dict/DictManager'
import { provideBusy } from '@/shared/busy'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import { cachedRows, loadCached } from '@/shared/offline/cache'
import { outbox, outboxAsRequest, queuedRequests, withQueuedEdit } from '@/shared/offline/outbox'
import { FINAL_STATUSES, seenByMe, waitsForMe } from '@/shared/requests/rights'
import { isRequestList, loadOffline } from '@/shared/requests/useRequestCard'
import { currentUser } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'
import type { RequestsMode } from './requestsListState'
import { listMode as mode, listScope as scope, listState } from './requestsListState'
import RequestSection from '../components/RequestSection.vue'

/**
 * Список заявок мобильной части (Ф-ЗАЯ-06): «Мои», «На исполнение», «Все» и под ними «Открытые» или закрытые заявки,
 * созданные за неделю.
 * Отбор и прокрутка запоминаются в памяти приложения (requestsListState). Заявки из очереди на телефоне показываются
 * первыми в открытых «Моих» и «Всех». Без связи — сохранённый список, а если этот список ещё не открывали — заявки из
 * других сохранённых списков.
 *
 * «Не отправлено» — заявки, у которых что-то ждёт отправки; отбор «Мои», «На исполнение», «Все» здесь не действует:
 * очередь своя, на телефоне. Открывается и со ссылки ?filter=unsent — из меню «Выход».
 */

const scopes: { id: RequestScope; text: string }[] = [
  { id: 'mine', text: 'Мои' },
  { id: 'todo', text: 'На исполнение' },
  { id: 'all', text: 'Все' },
]
// «Не отправлено» — только пока есть что отправлять
const modes = computed<{ id: RequestsMode; text: string }[]>(() => [
  { id: 'open', text: 'Открытые' },
  { id: 'closed', text: 'Закрытые' },
  ...(outbox.value.length > 0 ? [{ id: 'unsent' as const, text: 'Не отправлено' }] : []),
])

const MODE_TITLE: Record<RequestsMode, string> = {
  open: 'Открытые',
  closed: 'Созданные за неделю, закрытые',
  unsent: 'Не отправлено',
}

// закрытые — только созданные за последние 7 дней
function weekAgo(): string {
  const d = new Date()
  d.setDate(d.getDate() - 7)
  return isoDateTime(d).slice(0, 10)
}

const route = useRoute()
// со ссылки ?filter=unsent — сразу «Не отправлено»; запомненного «Не отправлено» больше нет, если всё отправилось
if (route.query.filter === 'unsent') mode.value = 'unsent'
else if (mode.value === 'unsent' && outbox.value.length === 0) mode.value = 'open'
watch(
  () => route.query.filter,
  (filter) => {
    if (filter === 'unsent') mode.value = 'unsent'
  },
)
// всё отправилось — список «Не отправлено» пуст, его кнопки больше нет
watch(
  () => outbox.value.length,
  (n) => {
    if (n === 0 && mode.value === 'unsent') mode.value = 'open'
  },
)
const closed = computed(() => mode.value === 'closed')

const requests = shallowRef<RepairRequest[]>([])
const stale = ref(false)
const error = ref<ShownError | null>(null)

const queued = computed(() => {
  const u = currentUser.value
  if (!u || closed.value || (mode.value === 'open' && scope.value === 'todo')) return []
  return queuedRequests.value.filter((x) => x.author === u.id).map((x) => outboxAsRequest(x, u.name))
})

// исправления, которые ждут отправки, видны сразу
const shown = computed(() => requests.value.map(withQueuedEdit))

// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

/** Заявки с сервера, к которым что-то ждёт отправки. */
const unsentIds = computed(() => [...new Set(outbox.value.flatMap((x) => (x.request === null ? [] : [x.request])))])

/** Подходит ли заявка под отбор страницы: как на сервере, но по данным на телефоне. */
function matches(r: RepairRequest): boolean {
  const u = currentUser.value
  if (FINAL_STATUSES.includes(r.requestStatusType) !== closed.value) return false
  if (closed.value && r.createdDt.slice(0, 10) < weekAgo()) return false
  if (scope.value === 'mine') return r.createdByUsr.id === u?.id
  if (scope.value === 'todo') return !!u && waitsForMe(u, r)
  return !!u && seenByMe(u, r)
}

/** Список по отбору: с сервера; нет связи — его копия, а нет копии — заявки из других сохранённых списков. */
async function loadList(): Promise<{ data: RepairRequest[]; stale: boolean }> {
  const key = `requests:${scope.value}:${closed.value}`
  const filter = requestFilter(scope.value, { closed: closed.value, from: closed.value ? weekAgo() : null })
  try {
    return await loadCached(key, () => fetchRequests(filter, null))
  } catch (e) {
    if (!isNetworkError(e)) throw e
    return { data: (await cachedRows<RepairRequest>(isRequestList)).filter(matches), stale: true }
  }
}

/** Заявки, к которым что-то ждёт отправки: заявка, которой нет ни на связи, ни на телефоне, не показывается. */
async function loadUnsent(): Promise<{ data: RepairRequest[]; stale: boolean }> {
  const results = await Promise.allSettled(unsentIds.value.map(loadOffline))
  const failed = results.find((x) => x.status === 'rejected' && !isNetworkError(x.reason))
  if (failed?.status === 'rejected') throw failed.reason
  const loaded = results.flatMap((x) => (x.status === 'fulfilled' ? [x.value] : []))
  return { data: loaded.map((x) => x.data), stale: loaded.some((x) => x.stale) }
}

let seq = 0
async function load() {
  const my = ++seq
  error.value = null
  try {
    // заявки и справочник неисправностей для строк — параллельно
    const [, result] = await busy.run(
      Promise.all([dictManager.prepareDict('FaultType'), mode.value === 'unsent' ? loadUnsent() : loadList()]),
    )
    if (my !== seq) return
    requests.value = result.data
    stale.value = result.stale
  } catch (e) {
    if (my !== seq) return
    requests.value = []
    error.value = shownError(e)
  }
}

// смена отбора — другой список, прокрутка с начала
watch([scope, mode], () => {
  listState.scrollTop = 0
  void load()
})

const page = useTemplateRef<InstanceType<typeof PwaPage>>('page')
onBeforeRouteLeave(() => {
  listState.scrollTop = page.value?.body?.scrollTop ?? 0
})
// отправилось или встало в очередь — список «Не отправлено» другой
watch(
  () => unsentIds.value.join(),
  () => {
    if (mode.value === 'unsent') void load()
  },
)

const onRefresh = () => void load()
bus.on('requests-refresh', onRefresh)
bus.on('connection-restored', onRefresh)
onBeforeUnmount(() => {
  bus.off('requests-refresh', onRefresh)
  bus.off('connection-restored', onRefresh)
})

// загрузка — сразу в setup: спиннер виден с первой отрисовки; прокрутка — когда список уже на экране
void load().then(async () => {
  await nextTick()
  if (page.value?.body) page.value.body.scrollTop = listState.scrollTop
})
</script>

<style scoped lang="scss">
.requests__pills {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
}

.requests__pill {
  flex: none;
  height: 2.5rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 62.5rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;

  &:disabled {
    opacity: 0.5;
  }

  &--on {
    border-color: $primary;
    background: $primary;
    color: #fff;
  }
}

.requests__fab {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  background: $primary;
  box-shadow: 0 0.3rem 0.8rem rgba(0, 0, 0, 0.3);
  color: #fff;
  text-decoration: none;
}

.requests__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
