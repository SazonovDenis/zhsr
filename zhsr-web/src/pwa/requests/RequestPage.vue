<template>
  <PwaPage
    white
    :back="goBack"
    :title="r ? faultName(r.faultType) : t('Заявка')"
    :subtitle="subtitle"
    :stale="card.stale.value"
  >
    <LoadSpinner v-if="busy.active.value && !r" />
    <ErrorBox
      v-if="card.error.value"
      :error="card.error.value"
      :retry="r ? null : card.load"
      class="card__error q-py-sm q-px-md"
    />
    <div v-if="!queuedItem && !r && !card.loading.value && !card.error.value" class="card__muted">
      {{ t('Заявка не найдена') }}
    </div>

    <template v-if="r">
      <div v-if="r.tor" class="card__tor q-py-sm q-px-md">{{ t('ТОР: вагон отцеплен, ремонт в депо') }}</div>

      <div class="card__badges">
        <QueuedRefusal v-if="queuedItem?.refused" :item="queuedItem" />
        <span v-else-if="queuedItem" class="zs-badge card__queued">{{ t('Ожидает отправки') }}</span>
        <DictBadge v-else :id="r.requestStatusType" dict="RequestStatus" />
        <span :style="severityStyle(r.severityType)">{{ dictManager.getValue('Severity', r.severityType) }}</span>
        <span v-if="r.forwarded" class="card__muted">{{ t('передана в депо') }}</span>
        <SoftLink
          v-if="queuedItem || card.editable.value"
          text="Редактировать заявку"
          class="q-ml-auto"
          :disabled="card.running.value"
          @click="edit"
        />
      </div>

      <!-- как запись истории: описание слева, фото заявки столбиком справа; ждущие отправки — там же -->
      <!-- без описания и фото блока нет: иначе пустое место над атрибутами -->
      <TextPhotos
        v-if="r.description || tree.photos.length > 0 || tree.own.length > 0"
        :photos="tree.photos"
        :pending="pendingOwn"
        :caption="{ meta: subtitle, text: r.description }"
      >
        <div class="card__top-main">
          <div v-if="r.description" class="card__desc">{{ r.description }}</div>
        </div>
        <template v-if="tree.own.length > 0" #photos="{ all, caption }">
          <QueuedPhoto v-for="item in tree.own" :key="item.id" :item="item" :all="all" :caption="caption" />
        </template>
      </TextPhotos>

      <section class="card__section q-pt-md q-px-sm q-pb-none">
        <div class="card__section-title">{{ t('Атрибуты заявки') }}</div>
        <div class="card__attrs">
          <span>{{ t('Вагон') }}</span>
          <span>
            <span class="zs-mono">{{ r.wagon.number }}</span>
            <template v-if="r.wagon.wagonType"> • {{ dictManager.getValue('WagonType', r.wagon.wagonType) }}</template>
          </span>
          <span>{{ t('Рейс') }}</span>
          <span>{{ r.trip ? tripRefDatedText(r.trip) : '—' }}</span>
          <span>{{ t('Служба') }}</span>
          <span>{{ dictManager.getValue('Service', r.service) }}</span>
          <span>{{ t('Автор') }}</span>
          <span>{{ r.createdByUsr.name }}</span>
          <template v-if="receivedLate(r.createdDt, r.receivedDt)">
            <span>{{ t('Поступила на сервер') }}</span>
            <span>{{ formatDateTime(r.receivedDt) }}</span>
          </template>
          <span>{{ t('Исполнитель') }}</span>
          <span>
            {{ r.executorUsr?.name ?? t('не назначен') }}
            <SoftLink v-if="card.assignable.value" text="Назначить" class="card__assign q-ml-sm" @click="openAssign" />
          </span>
        </div>
        <div v-if="card.stale.value" class="card__stale">{{ t('Фото недоступны без связи') }}</div>
      </section>

      <section class="card__section q-pt-md q-px-sm q-pb-none">
        <div class="card__section-head">
          <span class="card__section-title">{{ t('История') }}</span>
          <SoftLink text="Добавить примечание" :disabled="card.noteBlocked.value" @click="noteOpen = true" />
        </div>
        <div v-if="card.stale.value" class="card__stale">{{ t('История недоступна без связи') }}</div>
        <RequestHistory :nodes="tree.nodes" :status="historyStatus" :pending="pendingPhotos" large>
          <template #pending="{ node, all, caption }">
            <QueuedPhoto v-for="item in node.queued" :key="item.id" :item="item" :all="all" :caption="caption" />
          </template>
          <template #queued="{ node, line }">
            <QueuedItem :items="node.queued" :line="line" />
          </template>
        </RequestHistory>
      </section>
    </template>

    <template v-if="hasBar" #bar>
      <div v-if="!isOnline" class="card__need-net">
        {{ t('Для действия нужна связь') }}
      </div>
      <div class="card__buttons">
        <button
          v-for="a in actions"
          :key="a.action"
          type="button"
          class="card__btn q-py-none q-px-md"
          :class="[`card__btn--${a.kind}`, { 'card__btn--wide': a.kind !== 'secondary' }]"
          :disabled="card.blocked.value"
          @click="onAction(a.action)"
        >
          {{ a.title }}
        </button>
      </div>
    </template>
  </PwaPage>

  <!-- комментарий к действию -->
  <BottomSheet
    :show="!!card.pending.value"
    :title="card.pendingDef.value ? t(card.pendingDef.value.title) : ''"
    @update:show="(v) => !v && card.cancel()"
  >
    <textarea
      v-model="card.comment.value"
      class="card__textarea q-pa-md"
      rows="4"
      :placeholder="card.pendingDef.value?.comment ? t(card.pendingDef.value.comment) : ''"
    />
    <PhotoPicker v-if="card.pendingDef.value?.photos" v-model="card.actionPhotos.value" large compress />
    <template #buttons>
      <button type="button" class="card__btn q-py-none q-px-md" @click="card.cancel">{{ t('Отмена') }}</button>
      <button
        type="button"
        class="card__btn card__btn--grow q-py-none q-px-md"
        :class="card.pendingDef.value?.kind === 'danger' ? 'card__btn--danger' : 'card__btn--primary'"
        :disabled="!card.comment.value.trim() || card.blocked.value"
        @click="card.confirm"
      >
        {{ t('Подтвердить') }}
      </button>
    </template>
  </BottomSheet>

  <!-- взять в работу заявку, назначенную другому -->
  <BottomSheet v-model:show="takeOpen" :title="t('Заявка назначена другому')">
    <div>
      {{ t('Исполнитель заявки — {name}. Взять её в работу вместо него?', { name: card.foreignExecutor.value }) }}
    </div>
    <template #buttons>
      <button type="button" class="card__btn q-py-none q-px-md" @click="takeOpen = false">{{ t('Отмена') }}</button>
      <button type="button" class="card__btn card__btn--grow card__btn--primary q-py-none q-px-md" @click="confirmTake">
        {{ t('Взять в работу') }}
      </button>
    </template>
  </BottomSheet>

  <!-- назначение исполнителя -->
  <PickSheet
    v-model:show="assignOpen"
    :title="t('Назначить исполнителя')"
    :options="assignOptions"
    :selected="detail?.executorUsr?.id ?? null"
    :error="assignError"
    :retry="loadAssign"
    :disabled="card.blocked.value"
    @pick="card.assign($event)"
  />

  <!-- примечание к заявке: текст и фото -->
  <BottomSheet v-model:show="noteOpen" :title="t('Примечание к заявке')">
    <textarea v-model="noteText" class="card__textarea q-pa-md" rows="4" :placeholder="t('Текст примечания')" />
    <PhotoPicker v-model="notePhotos" large compress />
    <template #buttons>
      <button type="button" class="card__btn q-py-none q-px-md" @click="closeNote">{{ t('Отмена') }}</button>
      <button
        type="button"
        class="card__btn card__btn--grow card__btn--primary q-py-none q-px-md"
        :disabled="(!noteText.trim() && notePhotos.length === 0) || card.noteBlocked.value"
        @click="saveNote"
      >
        {{ t('Добавить') }}
      </button>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { RepairRequest, RequestAction } from '@/api/requests'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { bus } from '@/shared/bus'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import type { QueuedRequest } from '@/shared/offline/outbox'
import PickSheet from '../components/PickSheet.vue'
import QueuedItem from '../components/QueuedItem.vue'
import QueuedPhoto from '../components/QueuedPhoto.vue'
import QueuedRefusal from '../components/QueuedRefusal.vue'
import { useQueuedPhotos } from '../components/useQueuedPhotos'
import { outboxAsRequest, outboxItem, queuedFor, queueNote, withQueuedEdit } from '@/shared/offline/outbox'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import TextPhotos from '@/shared/requests/TextPhotos.vue'
import { historyTree } from '@/shared/requests/historyTree'
import RequestHistory from '@/shared/requests/RequestHistory.vue'
import { faultName, receivedLate, severityStyle } from '@/shared/requests/requestView'
import { useRequestCard } from '@/shared/requests/useRequestCard'
import { currentUser } from '@/shared/user'
import BottomSheet from '../components/BottomSheet.vue'
import PwaPage from '../components/PwaPage.vue'
import { tripRefDatedText } from '@/shared/trips/tripRefText'

/**
 * Карточка заявки на телефоне: неисправность, статус, атрибуты, фото и история; внизу —
 * кнопки действий по правам пользователя. Действие с комментарием открывает лист ввода комментария снизу. Мастер
 * депо выбирает исполнителя прямо в карточке. Без связи карточка показывается из копии на телефоне, а действия
 * недоступны; исправить заявку и добавить примечание можно: они ждут на телефоне и показаны в истории, а исправление
 * сразу видно в полях заявки.
 *
 * Заявка из очереди на телефоне (id «local-…») показывается по черновику: номера ещё нет, действий нет, исправить
 * можно.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()

const queuedItem = computed(() => (props.id.startsWith('local-') ? outboxItem(props.id) : null))
const serverId = computed(() => (props.id.startsWith('local-') ? null : Number(props.id)))

// общее ожидание страницы: спиннер на месте карточки — на всю цепочку загрузок
const busy = provideBusy()
const card = useRequestCard(serverId, true, busy)

// связь вернулась — вместо строки сохранённого списка показываем карточку с сервера
const onRestored = () => {
  if (card.stale.value) void card.load()
}
bus.on('connection-restored', onRestored)
onBeforeUnmount(() => bus.off('connection-restored', onRestored))

// заявка из очереди ушла на сервер, пока открыта её карточка, — номер присвоен, возвращаемся в список
watch(queuedItem, (now, before) => {
  if (before && !now) void router.replace({ name: 'pwa-requests' })
})

// исправление, примечания и фото этой заявки, которые ждут отправки
const waiting = computed(() => queuedFor(serverId.value ?? queuedItem.value?.guid ?? ''))
// дерево истории: записи сервера и очередь; что с чем показывать, решается здесь, а не в вёрстке
const tree = computed(() =>
  historyTree(card.stale.value ? null : detail.value, waiting.value, r.value?.createdDt ?? null),
)
const pendingPhotos = useQueuedPhotos(() => waiting.value)
const pendingOwn = computed(() => {
  const ids = tree.value.own.map((x) => x.id)
  return pendingPhotos.value.filter((p) => ids.includes(p.id))
})
// ушли на сервер или удалены — показываем карточку с сервера
watch(
  () => waiting.value.length,
  (now, before) => {
    if (now < before) void card.load()
  },
)

const detail = computed(() => card.request.value)

// история с сервера; без связи её нет, остаются только записи, ожидающие отправки
const historyStatus = computed(() => (detail.value && !card.stale.value ? detail.value.requestStatusType : null))
const r = computed<RepairRequest | QueuedRequest | null>(() => {
  const q = queuedItem.value
  if (q) return outboxAsRequest(q, currentUser.value?.name ?? '')
  return detail.value && withQueuedEdit(detail.value)
})

const subtitle = computed(() => {
  if (!r.value) return ''
  const number = queuedItem.value ? t('Новая') : r.value.number
  return `${number} • ${formatDateTime(r.value.createdDt)}`
})

// отцепка ТОР и передача в депо на телефоне не выполняются
const actions = computed(() => card.actions.value.filter((a) => a.action !== 'DETACH' && a.action !== 'FORWARD'))
const hasBar = computed(() => actions.value.length > 0)

const takeOpen = ref(false)

function onAction(action: RequestAction) {
  if (action === 'TAKE' && card.foreignExecutor.value) takeOpen.value = true
  else card.start(action)
}

function confirmTake() {
  takeOpen.value = false
  card.start('TAKE')
}

// исполнитель — из сотрудников службы заявки; текущий исполнитель есть в списке всегда
const staffOptions = computed(() => {
  const list = card.staff.value
  const current = detail.value?.executorUsr
  return current && !list.some((u) => u.id === current.id) ? [current, ...list] : list
})

// выбор исполнителя в листе снизу; null — не назначен
const assignOpen = ref(false)
const assignOptions = computed(() => [{ id: null, name: t('Не назначен') }, ...staffOptions.value])

const assignError = ref<ShownError | null>(null)

// список сотрудников — с сервера: ошибка показывается прямо в листе
async function loadAssign() {
  assignError.value = null
  try {
    await card.loadStaff()
  } catch (e) {
    assignError.value = shownError(e)
  }
}

function openAssign() {
  assignOpen.value = true
  void loadAssign()
}

// примечание
const noteOpen = ref(false)
const noteText = ref('')
const notePhotos = ref<Blob[]>([])

function closeNote() {
  noteOpen.value = false
  noteText.value = ''
  notePhotos.value = []
}

async function saveNote() {
  // заявка из очереди: примечание уйдёт следом за ней
  const q = queuedItem.value
  if (q) {
    await queueNote({ parent: q.guid }, noteText.value.trim(), notePhotos.value, q.author)
    closeNote()
    return
  }
  if (await card.addNote(noteText.value, notePhotos.value)) closeNote()
}

function edit() {
  void router.push({ name: 'pwa-request-edit', params: { id: props.id } })
}

// назад — туда, откуда пришли; открыли карточку напрямую — в список заявок
function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-requests' })
}
</script>

<style scoped lang="scss">
.card__muted {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.card__stale,
.card__need-net {
  color: #7a5200;
  font-size: 0.8rem;
  font-weight: 600;
}

.card__need-net {
  text-align: center;
}

.card__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.card__tor {
  border-radius: 0.3rem;
  background: #fbe1de;
  color: $zs-error-fg;
  font-size: 0.8rem;
  font-weight: 700;
}

.card__top-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  min-width: 0;
}

.card__desc {
  font-size: 0.9rem;
  line-height: 1.45;
  white-space: pre-line;
}

.card__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  font-size: 0.8rem;
}

.card__queued {
  background: #fff1cc;
  color: #6b4700;
}

.card__section {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  margin: 0 -0.5rem;
  border-top: 0.0625rem solid $zs-border;
}

.card__section > .card__section-title,
.card__section-head {
  // отступ под подписью группы — на треть меньше промежутка между блоками (0.8rem)
  margin-bottom: calc(-0.8rem / 3);
}

.card__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card__section-title {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.card__attrs {
  display: grid;
  grid-template-columns: 7.5rem minmax(0, 1fr);
  gap: 0.5rem 1rem;
  font-size: 0.8rem;

  > :nth-child(odd) {
    color: $zs-text-muted;
  }
}

.card__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.card__btn {
  flex: 1 1 40%;
  min-height: 3.3rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;

  &--wide {
    flex-basis: 100%;
  }

  &--grow {
    flex: 1;
  }

  &--primary {
    border: 0;
    background: $primary;
    color: #fff;
  }

  &--danger {
    border: 0;
    background: #b91c1c;
    color: #fff;
  }

  &:disabled {
    opacity: 0.45;
  }
}

.bottom-sheet__buttons .card__btn {
  flex: none;
}

.bottom-sheet__buttons .card__btn--grow {
  flex: 1;
}

.card__textarea {
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: $zs-bg-header;
  color: $zs-text;
  font: inherit;
  font-size: 1rem;
  resize: none;

  &:focus {
    border-color: $primary;
    outline: none;
  }
}
</style>
