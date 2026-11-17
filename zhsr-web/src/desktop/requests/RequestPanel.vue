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
      <div class="request-panel__badges">
        <DictBadge :id="r.requestStatusType" dict="RequestStatus" />
        <span :style="severityStyle(r.severityType)">{{ dictManager.getValue('Severity', r.severityType) }}</span>
      </div>

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
        <span><UserName :user="r.createdByUsr" /></span>
        <!-- заявку создала Система по нарушению в чек-листе или по недостаче в описи — переход к ним -->
        <template v-if="source">
          <span>{{ t('Источник') }}</span>
          <span><SoftLink :text="source.text" :params="{ name: source.name }" @click="openSource" /></span>
        </template>
        <template v-if="receivedLate(r.createdDt, r.receivedDt)">
          <span>{{ t('Поступила на сервер') }}</span>
          <span>{{ formatDateTime(r.receivedDt) }}</span>
        </template>
        <span>{{ t('Исполнитель') }}</span>
        <span>
          <UserName v-if="r.executorUsr" :user="r.executorUsr" /><template v-else>{{ t('не назначен') }}</template>
          <!-- статус не подходит (выполнена, закрыта…) — ссылки нет; нет прав — неактивна, причина во всплывающей
               подсказке -->
          <SoftLink
            v-if="card.assignStatusOk.value"
            :text="card.reassign.value ? 'Переназначить' : 'Назначить'"
            :disabled="!card.assignable.value"
            :title="card.assignable.value ? undefined : card.assignHint.value || undefined"
            class="q-ml-sm"
            @click="assign"
          />
        </span>
      </SidePanelFields>

      <!-- действия по заявке: видны все; нет прав или не тот статус — кнопка неактивна, причина во всплывающей подсказке;
           исключение — «Исправить» -->
      <div class="request-panel__buttons">
        <template v-for="a in actions" :key="a.action">
          <!-- основное действие — синяя, опасное — красная, остальные — белые с рамкой -->
          <q-btn
            unelevated
            no-caps
            :outline="a.kind === 'secondary'"
            :color="a.kind === 'primary' ? 'primary' : a.kind === 'danger' ? 'danger' : 'ink'"
            :class="{ 'zs-soft-border': a.kind === 'secondary' }"
            :disable="card.blocked.value || !!a.reason"
            :title="a.reason || undefined"
            :label="a.title"
            @click="onAction(a.action)"
          />
        </template>
        <!-- исправить: кнопка есть, пока автор может исправить заявку; срок вышел — кнопка исчезает -->
        <q-btn
          v-if="card.editable.value"
          unelevated
          outline
          no-caps
          color="ink"
          class="zs-soft-border"
          :disable="card.blocked.value"
          :label="t('Исправить')"
          @click="edit"
        />
      </div>
      <ErrorBox v-if="card.error.value" :error="card.error.value" class="request-panel__error q-py-sm q-px-md" />

      <TextPhotos
        v-if="r.description || tree.photos.length > 0"
        :photos="tree.photos"
        :caption="{ meta: `${r.number} • ${formatDateTime(r.createdDt)}`, text: r.description }"
        :has-text="!!r.description?.trim()"
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
import { computed, toRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import UserName from '@/shared/components/UserName.vue'
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
import { requestSource } from '@/shared/requests/requestSource'
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
import UserPickDialog from '@/shared/components/dialog/UserPickDialog.vue'
import { executorRows } from '@/shared/requests/staffRow'
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

const actions = computed(() => card.actions.value)

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
    title: def.sheet?.title ?? def.title,
    placeholder: def.comment,
    okText: def.sheet?.okText ?? 'Подтвердить',
    cancelText: def.sheet?.cancelText ?? 'Отмена',
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
const source = computed(() => requestSource(r.value))
const router = useRouter()

/** Перейти к чек-листу или описи, по которым создана заявка: список с выбранной записью. */
function openSource() {
  const s = source.value
  if (s) void router.push({ name: s.kind === 'check' ? 'checks' : 'equip', query: { id: String(s.id) } })
}

// исполнитель — во всплывающем окне, из сотрудников службы заявки
async function assign() {
  const reassign = !!r.value?.executorUsr
  await openDialog<{ usr: number | null }>(UserPickDialog, {
    title: reassign ? 'Переназначить исполнителя' : 'Назначить исполнителя',
    okText: reassign ? 'Переназначить' : 'Назначить',
    current: r.value?.executorUsr ?? null,
    load: async () => {
      await card.loadStaff()
      return executorRows(card.staff.value, r.value?.wagon.number ?? null)
    },
    // сохраняет окно: ошибка — в окне, а не в панели
    save: (usr: number | null) => card.assign(usr),
  })
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

// своё примечание автор исправляет во всплывающем окне; своё фото удаляет крестиком на миниатюре
card.provideChanges((noteId, text) => void editNote(noteId, text))

async function editNote(noteId: number, text: string) {
  const result = await openDialog<{ text: string; photos: Blob[] }>(RequestTextDialog, {
    title: 'Исправить примечание',
    placeholder: 'Текст примечания',
    okText: 'Сохранить',
    required: true,
    requiredText: 'Напишите примечание',
    initial: text,
  })
  if (result) await card.saveNote(noteId, result.text)
}

async function edit() {
  if (!r.value) return
  const updated = await openDialog<RepairRequest>(RequestDialog, { request: r.value })
  if (updated) await card.load()
}

/**
 * Дождаться карточки заявки id: меню строки списка выбирает заявку и сразу запускает действие панели. false —
 * карточка не загрузилась (ошибка показана в панели).
 */
function ready(id: number): Promise<boolean> {
  if (card.request.value?.id === id) return Promise.resolve(true)
  return new Promise((resolve) => {
    const stop = watch([() => card.request.value?.id, () => card.error.value], ([loaded, error]) => {
      if (loaded !== id && !error) return
      stop()
      resolve(loaded === id)
    })
  })
}

// действия панели для меню строки списка заявок: те же окна и проверки, что у кнопок
defineExpose({ ready, onAction, edit, assign, addNote })
</script>

<style scoped lang="scss">
.request-panel__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
  font-size: 0.8rem;
}

.request-panel__section-title {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.request-panel__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
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
