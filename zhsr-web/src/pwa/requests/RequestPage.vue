<template>
  <PwaPage :back="goBack" :title="r ? faultName(r.fault) : t('Заявка')" :subtitle="subtitle">
    <div v-if="card.loading.value && !r" class="card__muted">{{ t('Загрузка…') }}</div>
    <div v-if="card.error.value" class="card__error">{{ card.error.value }}</div>
    <div v-if="!queuedItem && !r && !card.loading.value && !card.error.value" class="card__muted">
      {{ t('Заявка не найдена') }}
    </div>

    <template v-if="r">
      <div v-if="card.stale.value" class="card__stale">{{ t('Нет связи — показаны сохранённые данные') }}</div>
      <div v-if="r.tor" class="card__tor">{{ t('ТОР: вагон отцеплен, ремонт в депо') }}</div>
      <div v-if="r.description" class="card__desc">{{ r.description }}</div>

      <div class="card__badges">
        <span v-if="queuedItem" class="zs-badge card__queued">{{ t('Ожидает отправки') }}</span>
        <DictBadge v-else :id="r.status" dict="RequestStatus" />
        <span :style="severityStyle(r.severity)">{{ dictManager.getValue('Severity', r.severity) }}</span>
        <span v-if="r.forwarded" class="card__muted">{{ t('передана в депо') }}</span>
      </div>

      <RequestSteps :status="r.status" :queued="!!queuedItem" large />
      <div class="card__next">
        <span class="card__section-title">{{ t('Что дальше') }}</span>
        <span class="card__next-text">{{ nextText(r, !!queuedItem) }}</span>
      </div>

      <section class="card__section">
        <div class="card__section-title">{{ t('Атрибуты заявки') }}</div>
        <div class="card__attrs">
          <span>{{ t('Вагон') }}</span>
          <span>
            <span class="zs-mono">{{ r.wagon }}</span>
            <template v-if="r.wagonType"> • {{ dictManager.getValue('WagonType', r.wagonType) }}</template>
          </span>
          <span>{{ t('Рейс') }}</span>
          <span>{{ r.trip?.name ?? '—' }}</span>
          <span>{{ t('Служба') }}</span>
          <span>{{ dictManager.getValue('Service', r.service) }}</span>
          <span>{{ t('Автор') }}</span>
          <span>{{ r.author.name }}</span>
          <span>{{ t('Исполнитель') }}</span>
          <span>
            <select
              v-if="card.assignable.value"
              class="card__select"
              :value="r.executor?.id ?? ''"
              :disabled="card.blocked.value"
              @change="onAssign"
            >
              <option value="">{{ t('Не назначен') }}</option>
              <option v-for="u in staffOptions" :key="u.id" :value="u.id">{{ u.name }}</option>
            </select>
            <template v-else>{{ r.executor?.name ?? t('не назначен') }}</template>
          </span>
        </div>
        <PhotoStrip v-if="photos.length > 0" :photos="photos" />
      </section>

      <section v-if="detail" class="card__section">
        <div class="card__section-head">
          <span class="card__section-title">{{ t('История') }}</span>
          <button type="button" class="card__link" :disabled="card.blocked.value" @click="noteOpen = true">
            {{ t('Добавить пояснение') }}
          </button>
        </div>
        <RequestHistory :events="detail.events" :status="detail.status" large />
      </section>
    </template>

    <template v-if="hasBar" #bar>
      <div v-if="!isOnline" class="card__need-net">{{ t('Для действия нужна связь') }}</div>
      <div class="card__buttons">
        <button
          v-if="card.editable.value"
          type="button"
          class="card__btn card__btn--wide"
          :disabled="card.blocked.value"
          @click="edit"
        >
          {{ t('Исправить') }}
        </button>
        <button
          v-for="a in card.actions.value"
          :key="a.action"
          type="button"
          class="card__btn"
          :class="[`card__btn--${a.kind}`, { 'card__btn--wide': a.kind !== 'secondary' }]"
          :disabled="card.blocked.value"
          @click="card.start(a.action)"
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
      class="card__textarea"
      rows="4"
      :placeholder="card.pendingDef.value?.comment ? t(card.pendingDef.value.comment) : ''"
    />
    <template #buttons>
      <button type="button" class="card__btn" @click="card.cancel">{{ t('Отмена') }}</button>
      <button
        type="button"
        class="card__btn card__btn--grow"
        :class="card.pendingDef.value?.kind === 'danger' ? 'card__btn--danger' : 'card__btn--primary'"
        :disabled="!card.comment.value.trim() || card.blocked.value"
        @click="card.confirm"
      >
        {{ t('Подтвердить') }}
      </button>
    </template>
  </BottomSheet>

  <!-- пояснение к заявке: текст и фото -->
  <BottomSheet v-model:show="noteOpen" :title="t('Пояснение к заявке')">
    <textarea v-model="noteText" class="card__textarea" rows="4" :placeholder="t('Текст пояснения')" />
    <PhotoPicker v-model="notePhotos" large />
    <template #buttons>
      <button type="button" class="card__btn" @click="closeNote">{{ t('Отмена') }}</button>
      <button
        type="button"
        class="card__btn card__btn--grow card__btn--primary"
        :disabled="(!noteText.trim() && notePhotos.length === 0) || card.blocked.value"
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

import type { RepairRequest, RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { outboxAsRequest, outboxItem } from '@/shared/offline/outbox'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import PhotoStrip from '@/shared/requests/PhotoStrip.vue'
import RequestHistory from '@/shared/requests/RequestHistory.vue'
import RequestSteps from '@/shared/requests/RequestSteps.vue'
import { faultName, nextText, severityStyle } from '@/shared/requests/requestView'
import { useRequestCard } from '@/shared/requests/useRequestCard'
import { currentUser } from '@/shared/user'
import BottomSheet from '../components/BottomSheet.vue'
import PwaPage from '../components/PwaPage.vue'

/**
 * Карточка заявки на телефоне: неисправность, статус, путь заявки, что дальше, атрибуты, фото и история; внизу —
 * кнопки действий по правам пользователя. Действие с комментарием открывает лист ввода комментария снизу. Мастер
 * депо выбирает исполнителя прямо в карточке. Без связи карточка показывается из копии на телефоне, а действия
 * недоступны.
 *
 * Заявка из очереди на телефоне (id «local-…») показывается по черновику: номера ещё нет, действий нет.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()

const queuedItem = computed(() => (props.id.startsWith('local-') ? outboxItem(props.id) : null))
const serverId = computed(() => (props.id.startsWith('local-') ? null : props.id))

const card = useRequestCard(serverId, true)

// заявка из очереди ушла на сервер, пока открыта её карточка, — номер присвоен, возвращаемся в список
watch(queuedItem, (now, before) => {
  if (before && !now) void router.replace({ name: 'pwa-requests' })
})

const detail = computed(() => card.request.value)
const r = computed<RepairRequest | null>(() => {
  const q = queuedItem.value
  if (q) return outboxAsRequest(q, currentUser.value?.name ?? '')
  return detail.value
})

// фото черновика из очереди — адреса файлов в памяти
const queuedUrls = ref<string[]>([])
watch(
  queuedItem,
  (q) => {
    queuedUrls.value.forEach((url) => URL.revokeObjectURL(url))
    queuedUrls.value = q ? q.photos.map((p) => URL.createObjectURL(p)) : []
  },
  { immediate: true },
)
onBeforeUnmount(() => queuedUrls.value.forEach((url) => URL.revokeObjectURL(url)))

const photos = computed<RequestPhoto[]>(() =>
  queuedItem.value ? queuedUrls.value.map((url, i) => ({ id: String(i), url })) : (detail.value?.photos ?? []),
)

const subtitle = computed(() => {
  if (!r.value) return ''
  const number = queuedItem.value ? t('номер присвоят при отправке') : r.value.number
  return `${number} • ${formatDateTime(r.value.created)}`
})

const hasBar = computed(() => !queuedItem.value && (card.actions.value.length > 0 || card.editable.value))

// исполнитель — из сотрудников службы заявки; текущий исполнитель есть в списке всегда
const staffOptions = computed(() => {
  const list = card.staff.value
  const current = detail.value?.executor
  return current && !list.some((u) => u.id === current.id) ? [current, ...list] : list
})

function onAssign(e: Event) {
  const value = (e.target as HTMLSelectElement).value
  void card.assign(value || null)
}

// пояснение
const noteOpen = ref(false)
const noteText = ref('')
const notePhotos = ref<Blob[]>([])

function closeNote() {
  noteOpen.value = false
  noteText.value = ''
  notePhotos.value = []
}

async function saveNote() {
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
  font-size: 0.875rem;
}

.card__stale,
.card__need-net {
  color: #7a5200;
  font-size: 0.8125rem;
  font-weight: 600;
}

.card__need-net {
  text-align: center;
}

.card__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe5e1;
  color: #8f2416;
  font-size: 0.8125rem;
}

.card__tor {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe1de;
  color: #8f2416;
  font-size: 0.8125rem;
  font-weight: 700;
}

.card__desc {
  font-size: 0.875rem;
  line-height: 1.45;
  white-space: pre-line;
}

.card__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.625rem;
  font-size: 0.8125rem;
}

.card__queued {
  background: #fff1cc;
  color: #6b4700;
}

.card__next {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.875rem 1rem;
  border-radius: 0.375rem;
  background: $zs-row-selected-bg;
}

.card__next-text {
  font-size: 1rem;
  font-weight: 600;
}

.card__section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0 -1rem;
  padding: 1rem 1rem 0;
  border-top: 0.0625rem solid $zs-border;
}

.card__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card__section-title {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.card__attrs {
  display: grid;
  grid-template-columns: 7.5rem minmax(0, 1fr);
  gap: 0.5rem 1rem;
  font-size: 0.84375rem;

  > :nth-child(odd) {
    color: $zs-text-muted;
  }
}

.card__select {
  width: 100%;
  height: 2.8rem;
  padding: 0 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9375rem;
}

.card__link {
  padding: 0;
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    opacity: 0.45;
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
  padding: 0 0.875rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9375rem;
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
  padding: 0.75rem 0.875rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
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
