<template>
  <SidePanel
    :empty="requestId ? '' : 'Выберите заявку в списке'"
    :title="r ? faultName(r.fault) : ''"
    :subtitle="r ? `${r.number} • ${formatDateTime(r.created)}` : ''"
    @close="emit('close')"
  >
    <div v-if="card.error.value && !r" class="request-panel__error">{{ card.error.value }}</div>

    <template v-if="r">
      <div v-if="r.tor" class="request-panel__tor">{{ t('ТОР: вагон отцеплен, ремонт в депо') }}</div>

      <div class="request-panel__badges">
        <DictBadge :id="r.status" dict="RequestStatus" />
        <span :style="severityStyle(r.severity)">{{ dictManager.getValue('Severity', r.severity) }}</span>
        <span v-if="r.forwarded" class="request-panel__mark">{{ t('передана в депо') }}</span>
      </div>

      <RequestSteps :status="r.status" />
      <div class="request-panel__next">
        <span class="request-panel__next-title">{{ t('Что дальше') }}</span>
        <span>{{ nextText(r) }}</span>
      </div>

      <!-- действия по заявке; с комментарием — сначала ввод комментария -->
      <div v-if="card.pending.value" class="request-panel__comment">
        <div class="request-panel__comment-title">{{ t(card.pendingDef.value!.title) }}</div>
        <textarea
          v-model="card.comment.value"
          class="request-panel__textarea"
          rows="3"
          :placeholder="t(card.pendingDef.value!.comment ?? '')"
        />
        <div class="request-panel__buttons">
          <button type="button" class="request-panel__btn" @click="card.cancel">{{ t('Отмена') }}</button>
          <button
            type="button"
            class="request-panel__btn"
            :class="
              card.pendingDef.value!.kind === 'danger' ? 'request-panel__btn--danger' : 'request-panel__btn--primary'
            "
            :disabled="!card.comment.value.trim() || card.running.value"
            @click="card.confirm"
          >
            {{ t('Подтвердить') }}
          </button>
        </div>
      </div>
      <div v-else-if="card.actions.value.length > 0 || card.editable.value" class="request-panel__buttons">
        <button
          v-for="a in card.actions.value"
          :key="a.action"
          type="button"
          class="request-panel__btn"
          :class="`request-panel__btn--${a.kind}`"
          :disabled="card.blocked.value"
          @click="card.start(a.action)"
        >
          {{ a.title }}
        </button>
        <button
          v-if="card.editable.value"
          type="button"
          class="request-panel__btn"
          :title="card.editHint.value"
          @click="edit"
        >
          {{ t('Исправить') }}
        </button>
      </div>
      <div v-if="card.error.value" class="request-panel__error">{{ card.error.value }}</div>

      <SidePanelFields>
        <span>{{ t('Вагон') }}</span>
        <span>
          <span class="zs-mono">{{ r.wagon }}</span> • {{ dictManager.getValue('WagonType', r.wagonType) }}
          <span v-if="r.wagonPlace" class="zs-sub request-panel__block">{{ r.wagonPlace }}</span>
        </span>
        <span>{{ t('Рейс') }}</span>
        <span>{{ r.trip?.name ?? '—' }}</span>
        <span>{{ t('Служба') }}</span>
        <span>{{ dictManager.getValue('Service', r.service) }}</span>
        <span>{{ t('Автор') }}</span>
        <span>{{ r.author.name }}</span>
        <span>{{ t('Исполнитель') }}</span>
        <span>
          <CbList
            v-if="card.assignable.value"
            :model-value="r.executor?.id ?? null"
            single
            placeholder="Не назначен"
            :values="staffChoices"
            class="request-panel__executor"
            @update:model-value="onAssign"
          />
          <template v-else>{{ r.executor?.name ?? t('не назначен') }}</template>
        </span>
      </SidePanelFields>

      <div v-if="r.description" class="request-panel__desc">{{ r.description }}</div>
      <PhotoStrip v-if="r.photos.length > 0" :photos="r.photos" />

      <div class="request-panel__section">
        <div class="request-panel__section-head">
          <span class="request-panel__section-title">{{ t('История') }}</span>
          <SoftLink v-if="!noteOpen" text="Добавить пояснение" @click="noteOpen = true" />
        </div>
        <div v-if="noteOpen" class="request-panel__comment">
          <textarea v-model="noteText" class="request-panel__textarea" rows="3" :placeholder="t('Текст пояснения')" />
          <PhotoPicker v-model="notePhotos" />
          <div class="request-panel__buttons">
            <button type="button" class="request-panel__btn" @click="closeNote">{{ t('Отмена') }}</button>
            <button
              type="button"
              class="request-panel__btn request-panel__btn--primary"
              :disabled="(!noteText.trim() && notePhotos.length === 0) || card.running.value"
              @click="saveNote"
            >
              {{ t('Добавить') }}
            </button>
          </div>
        </div>
        <RequestHistory :events="r.events" :status="r.status" />
      </div>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'

import type { RepairRequest } from '@/api/requests'
import { t } from '@/boot/i18n'
import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import { openDialog } from '@/shared/components/dialog/openDialog'
import DictBadge from '@/shared/components/DictBadge.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import PhotoStrip from '@/shared/requests/PhotoStrip.vue'
import RequestHistory from '@/shared/requests/RequestHistory.vue'
import RequestSteps from '@/shared/requests/RequestSteps.vue'
import { faultName, nextText, severityStyle } from '@/shared/requests/requestView'
import { useRequestCard } from '@/shared/requests/useRequestCard'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import RequestDialog from './RequestDialog.vue'

/**
 * Правая панель списка заявок: карточка выбранной заявки — статус, путь заявки, что дальше, кнопки действий по правам
 * пользователя, атрибуты, исполнитель (мастер депо выбирает его здесь), описание, фото, история и пояснения.
 * После изменения заявки в шину уходит requests-refresh — список за панелью загружается заново.
 */
const props = defineProps<{ requestId: string | null }>()

const emit = defineEmits<{ close: [] }>()

const card = useRequestCard(toRef(props, 'requestId'))
const r = computed(() => card.request.value)

// исполнитель — из сотрудников службы заявки; текущий исполнитель есть в списке, даже если он из другой службы
const staffChoices = computed(() => {
  const list = card.staff.value.map((u) => ({ id: u.id, text: u.name }))
  const current = r.value?.executor
  return current && !list.some((c) => c.id === current.id) ? [{ id: current.id, text: current.name }, ...list] : list
})

async function onAssign(value: ChoiceModel) {
  await card.assign(typeof value === 'string' ? value : null)
}

// пояснение к заявке: текст и фото
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

watch(
  () => props.requestId,
  () => closeNote(),
)

async function edit() {
  if (!r.value) return
  const updated = await openDialog<RepairRequest>(RequestDialog, { request: r.value })
  if (updated) await card.load()
}
</script>

<style scoped lang="scss">
.request-panel__tor {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe1de;
  color: #8f2416;
  font-size: 0.8125rem;
  font-weight: 700;
}

.request-panel__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.8125rem;
}

.request-panel__mark {
  color: $zs-text-muted;
}

.request-panel__next {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem 0.875rem;
  border-radius: 0.25rem;
  background: $zs-row-selected-bg;
  font-size: 0.875rem;
}

.request-panel__next-title,
.request-panel__section-title {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.request-panel__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.request-panel__btn {
  height: var(--zs-control-height);
  padding: 0 0.875rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8125rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: $zs-bg-hover;
  }

  &--primary {
    border-color: $primary;
    background: $primary;
    color: #fff;

    &:hover:not(:disabled) {
      background: $primary;
      opacity: 0.9;
    }
  }

  &--danger {
    border-color: #b91c1c;
    background: #b91c1c;
    color: #fff;

    &:hover:not(:disabled) {
      background: #b91c1c;
      opacity: 0.9;
    }
  }

  &:disabled {
    opacity: 0.45;
    cursor: default;
  }
}

.request-panel__comment {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  padding: 0.75rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
}

.request-panel__comment-title {
  font-weight: 700;
}

.request-panel__textarea {
  padding: 0.5rem 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  color: $zs-text;
  font: inherit;
  font-size: 0.875rem;
  resize: vertical;

  &:focus {
    border-color: $primary;
    outline: none;
    box-shadow: 0 0 0 0.0625rem $primary;
  }
}

.request-panel__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe5e1;
  color: #8f2416;
  font-size: 0.8125rem;
}

.request-panel__block {
  display: block;
}

.request-panel__executor {
  width: 100%;
}

.request-panel__desc {
  font-size: 0.875rem;
  line-height: 1.45;
  white-space: pre-line;
}

.request-panel__section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.request-panel__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
