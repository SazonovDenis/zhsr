<template>
  <SidePanel
    :empty="requestId ? '' : 'Выберите заявку в списке'"
    :title="r ? faultName(r.faultType) : ''"
    :subtitle="r ? `${r.number} • ${formatDateTime(r.createdDt)}` : ''"
    @close="emit('close')"
  >
    <LoadSpinner v-if="busy.active.value && !r" />
    <ErrorBox
      v-if="card.error.value && !r"
      :error="card.error.value"
      :retry="card.load"
      class="request-panel__error q-py-sm q-px-md"
    />

    <template v-if="r">
      <div v-if="r.tor" class="request-panel__tor q-py-sm q-px-md">{{ t('ТОР: вагон отцеплен, ремонт в депо') }}</div>

      <div class="request-panel__badges">
        <DictBadge :id="r.requestStatusType" dict="RequestStatus" />
        <span :style="severityStyle(r.severityType)">{{ dictManager.getValue('Severity', r.severityType) }}</span>
        <span v-if="r.forwarded" class="request-panel__mark">{{ t('передана в депо') }}</span>
      </div>

      <!-- действия по заявке; причина — во всплывающем окне -->
      <div v-if="actions.length > 0 || card.editable.value" class="request-panel__buttons">
        <button
          v-for="a in actions"
          :key="a.action"
          type="button"
          class="request-panel__btn q-py-none q-px-md"
          :class="`request-panel__btn--${a.kind}`"
          :disabled="card.blocked.value"
          @click="onAction(a.action)"
        >
          {{ a.title }}
        </button>
        <button
          v-if="card.editable.value"
          type="button"
          class="request-panel__btn q-py-none q-px-md"
          :title="card.editHint.value"
          @click="edit"
        >
          {{ t('Исправить') }}
        </button>
      </div>
      <ErrorBox v-if="card.error.value" :error="card.error.value" class="request-panel__error q-py-sm q-px-md" />

      <SidePanelFields>
        <span>{{ t('Вагон') }}</span>
        <span>
          <span class="zs-mono">{{ r.wagon.number }}</span> • {{ dictManager.getValue('WagonType', r.wagon.wagonType) }}
          <span v-if="r.locationType" class="zs-sub request-panel__block">{{
            placeText(r.locationType, r.place, r.wagonTrip ? tripRefText(r.wagonTrip) : null)
          }}</span>
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
          <SoftLink v-if="card.assignable.value" text="Назначить" class="q-ml-sm" @click="assign" />
        </span>
      </SidePanelFields>

      <TextPhotos
        v-if="r.description || tree.photos.length > 0"
        :photos="tree.photos"
        :caption="{ meta: `${r.number} • ${formatDateTime(r.createdDt)}`, text: r.description }"
      >
        <div class="request-panel__desc">{{ r.description }}</div>
      </TextPhotos>

      <div class="request-panel__section">
        <div class="request-panel__section-head">
          <span class="request-panel__section-title">{{ t('История') }}</span>
          <SoftLink text="Добавить примечание" :disabled="card.blocked.value" @click="addNote" />
        </div>
        <RequestHistory :nodes="tree.nodes" :status="r.requestStatusType" />
      </div>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue'

import type { RepairRequest, RequestAction } from '@/api/requests'
import { t } from '@/boot/i18n'
import { openDialog } from '@/shared/components/dialog/openDialog'
import DictBadge from '@/shared/components/DictBadge.vue'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { placeText } from '@/shared/wagonPlace'
import { formatDateTime } from '@/shared/format'
import TextPhotos from '@/shared/requests/TextPhotos.vue'
import { historyTree } from '@/shared/requests/historyTree'
import RequestHistory from '@/shared/requests/RequestHistory.vue'
import { faultName, receivedLate, severityStyle } from '@/shared/requests/requestView'
import { ACTIONS } from '@/shared/requests/rights'
import { useRequestCard } from '@/shared/requests/useRequestCard'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import RequestDialog from './RequestDialog.vue'
import { tripRefDatedText, tripRefText } from '@/shared/trips/tripRefText'
import RequestAssignDialog from './RequestAssignDialog.vue'
import RequestTextDialog from './RequestTextDialog.vue'
import TakeForeignDialog from './TakeForeignDialog.vue'

/**
 * Правая панель списка заявок: карточка выбранной заявки — статус, кнопки действий по правам пользователя, атрибуты,
 * исполнитель, описание, фото, история и примечания. Всё, что вводится (причина действия, примечание, исполнитель), —
 * во всплывающих окнах, в самой панели форм нет.
 * После изменения заявки в шину уходит requests-refresh — список за панелью загружается заново.
 */
const props = defineProps<{ requestId: number | null }>()

const emit = defineEmits<{ close: [] }>()

// правая панель — свой фрейм: своё ожидание, спиннер на месте карточки
const busy = provideBusy()
const card = useRequestCard(toRef(props, 'requestId'), false, busy)

// история и фото — из плоских списков сервера
const tree = computed(() => historyTree(card.request.value, [], card.request.value?.createdDt ?? null))

// ТОР (отцепка вагона) на десктопе из карточки не выполняется
const actions = computed(() => card.actions.value.filter((a) => a.action !== 'DETACH'))

/**
 * Действие по кнопке: взять в работу чужую заявку — после подтверждения; действие с причиной — после ввода причины
 * (и фото, где можно) во всплывающем окне.
 */
async function onAction(action: RequestAction) {
  const name = card.foreignExecutor.value
  if (action === 'TAKE' && name && !(await openDialog(TakeForeignDialog, { name }))) return
  const def = ACTIONS[action]
  if (!def.comment) {
    card.start(action)
    return
  }
  const result = await openDialog<{ text: string; photos: Blob[] }>(RequestTextDialog, {
    title: def.title,
    placeholder: def.comment,
    okText: 'Подтвердить',
    photos: !!def.photos,
    required: true,
  })
  if (!result) return
  card.start(action)
  card.comment.value = result.text
  card.actionPhotos.value = result.photos
  await card.confirm()
}

const r = computed(() => card.request.value)

// исполнитель — во всплывающем окне, из сотрудников службы заявки
async function assign() {
  const result = await openDialog<{ executor: number | null }>(RequestAssignDialog, {
    current: r.value?.executorUsr ?? null,
    load: async () => {
      await card.loadStaff()
      return card.staff.value
    },
  })
  if (result) await card.assign(result.executor)
}

// примечание к заявке: текст и фото — во всплывающем окне
async function addNote() {
  const result = await openDialog<{ text: string; photos: Blob[] }>(RequestTextDialog, {
    title: 'Примечание к заявке',
    placeholder: 'Текст примечания',
    okText: 'Добавить',
    photos: true,
  })
  if (result) await card.addNote(result.text, result.photos)
}

async function edit() {
  if (!r.value) return
  const updated = await openDialog<RepairRequest>(RequestDialog, { request: r.value })
  if (updated) await card.load()
}
</script>

<style scoped lang="scss">
.request-panel__tor {
  border-radius: 0.3rem;
  background: #fbe1de;
  color: $zs-error-fg;
  font-size: 0.8rem;
  font-weight: 700;
}

.request-panel__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
  font-size: 0.8rem;
}

.request-panel__mark {
  color: $zs-text-muted;
}

.request-panel__section-title {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.request-panel__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.request-panel__btn {
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8rem;
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




.request-panel__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.request-panel__block {
  display: block;
}


.request-panel__desc {
  font-size: 0.9rem;
  line-height: 1.45;
  white-space: pre-line;
}

.request-panel__section {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.request-panel__section-head {
  // отступ под подписью группы — на треть меньше промежутка между блоками (0.8rem)
  margin-bottom: calc(-0.8rem / 3);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
