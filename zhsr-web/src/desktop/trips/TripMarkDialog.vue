<template>
  <FormDialog
    v-model:show="show"
    :title="title"
    :ok-text="fix ? 'Сохранить время' : depart ? 'Отметить отправление' : 'Отметить прибытие'"
    :width="fix ? '35rem' : '50rem'"
    :min-height="fix ? 'sm' : 'xl'"
    :running="running"
    :error="error"
    @ok="save"
  >
    <template v-if="fix" #actions-left>
      <SoftLink :text="depart ? 'Отменить отправление' : 'Отменить прибытие'" :disabled="running" @click="cancel" />
    </template>
    <FormSection columns="max-content">
      <FormField :label="depart ? 'Фактическое отправление' : 'Фактическое прибытие'">
        <DatePicker v-model="time" with-time :date-text="depart ? 'Фактическое отправление' : 'Фактическое прибытие'" />
      </FormField>
    </FormSection>
    <!-- ответственные за вагоны (Ф-РЕЙ-08): отправление — план на рейс, прибытие — план после рейса (у вагона без плана —
         текущие); правки записываются вместо плана временем отметки. Во всю ширину окна -->
    <div v-if="!fix && responsibles" class="trip-mark__resp q-mt-md">
      <div class="trip-mark__title q-mb-xs">{{ t('Ответственные за вагоны') }}</div>
      <TripResponsiblesTable v-model="draft" :data="responsibles" />
    </div>
    <!-- прибытие: плановые прицепки и отцепки рейса отмечаются или отменяются до завершения рейса -->
    <div v-if="!depart && !fix && planned.length > 0" class="trip-mark__plans q-mt-md">
      <div class="trip-mark__title">{{ t('Не отмечены прицепки и отцепки рейса') }}</div>
      <div v-for="c in planned" :key="c.id" class="trip-mark__plan">
        <span class="zs-mono">{{ c.wagon.number }}</span>
        <ChangeLine :change="c" @mark="markPlan" @cancel="cancelPlan" />
      </div>
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, shallowRef } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { cancelChangePlan } from '@/api/trains'
import type { TripCrew, TripResponsibles } from '@/api/trips'
import {
  cancelArrival,
  cancelDeparture,
  fetchTripCrew,
  fetchTripResponsibles,
  markArrival,
  markDeparture,
  retimeArrival,
  retimeDeparture,
} from '@/api/trips'
import { t } from '@/boot/i18n'
import { TripResponsibleLayer } from '@/shared/constants/TripResponsibleLayer'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FormField from '@/shared/components/form/FormField.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { isWaiting } from '@/shared/trains/trainJournal'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import type { ResponsiblesDraft } from '@/shared/trips/tripResponsibles'
import { currentDraft, draftChanges } from '@/shared/trips/tripResponsibles'
import ChangeLine from '../trains/ChangeLine.vue'
import MarkChangeDialog from '../trains/MarkChangeDialog.vue'
import TripResponsiblesTable from './TripResponsiblesTable.vue'

/**
 * Диалог отметки отправления (Ф-РЕЙ-03) или прибытия (Ф-РЕЙ-04): фактическое время, по умолчанию — сейчас. fix —
 * время уже сделанной отметки: тогда диалог исправляет её время, а слева — отмена отметки (10 минут после отметки).
 * Отметка показывает ответственных за вагоны (Ф-РЕЙ-08): отправление — план ответственных на рейс, прибытие — план
 * после рейса, а у вагона без плана — текущих. Их можно поправить; отметка отрабатывает план, правки — вместо плана у
 * своих вагонов, временем отметки.
 * Прибытие: плановые прицепки и отцепки рейса показаны списком — рейс не завершается, пока каждую не отметят
 * выполненной или не отменят.
 * Открывается через openDialog, результат — рейс после действия и сообщение о нём.
 */
const props = withDefaults(defineProps<{ tripId: number; depart: boolean; fix?: string | null }>(), { fix: null })

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [result: { crew: TripCrew; message: string }] }>()

const title = computed(() => {
  if (props.fix) return props.depart ? 'Исправление отправления' : 'Исправление прибытия'
  return props.depart ? 'Отметка об отправлении' : 'Отметка о прибытии'
})

// значение DatePicker — время браузера без пояса, на сервер уходит с поясом
const time = ref<string | null>(date.formatDate(props.fix ? new Date(props.fix) : new Date(), 'YYYY-MM-DDTHH:mm'))
const running = ref(false)
const error = ref<ShownError | null>(null)

// ответственные за вагоны; не загружены — отметка их не меняет; ошибка — в поле ошибки диалога
const responsibles = shallowRef<TripResponsibles | null>(null)
const draft = ref<ResponsiblesDraft>({})
if (!props.fix) {
  fetchTripResponsibles(props.tripId, props.depart ? TripResponsibleLayer.TRIP : TripResponsibleLayer.ARRIVAL)
    .then((d) => {
      responsibles.value = d
      draft.value = currentDraft(d)
    })
    .catch((e: unknown) => (error.value = shownError(e)))
}

// прибытие: плановые прицепки и отцепки рейса; список свой, ошибка — в поле ошибки диалога
const changes = shallowRef<TrainChange[]>([])
const planned = computed(() => changes.value.filter(isWaiting))

async function loadChanges() {
  try {
    changes.value = (await fetchTripCrew(props.tripId)).changes
  } catch (e) {
    error.value = shownError(e)
  }
}
if (!props.depart && !props.fix) void loadChanges()

async function markPlan(change: TrainChange) {
  const done = await openDialog<TrainCard>(MarkChangeDialog, { change, inProgress: true })
  if (done) await loadChanges()
}

async function cancelPlan(change: TrainChange) {
  error.value = null
  try {
    await cancelChangePlan(change.id)
    await loadChanges()
  } catch (e) {
    error.value = shownError(e)
  }
}

async function save() {
  if (!time.value) {
    error.value = formError(t('Укажите время'))
    return
  }
  if (!props.depart && !props.fix && planned.value.length > 0) {
    error.value = formError(t('Отметьте или отмените плановые прицепки и отцепки рейса'))
    return
  }
  error.value = null
  running.value = true
  try {
    const value = isoDateTime(new Date(time.value))
    if (props.fix) {
      const retime = props.depart ? retimeDeparture : retimeArrival
      emit('done', { crew: await retime(props.tripId, value), message: t('Время исправлено') })
    } else {
      const changes = responsibles.value ? draftChanges(responsibles.value, draft.value) : null
      if (props.depart) {
        emit('done', { crew: await markDeparture(props.tripId, value, changes), message: t('Отправление отмечено') })
      } else {
        emit('done', { crew: await markArrival(props.tripId, value, changes), message: t('Прибытие отмечено') })
      }
    }
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}

async function cancel() {
  error.value = null
  running.value = true
  try {
    const undo = props.depart ? cancelDeparture : cancelArrival
    const message = props.depart ? t('Отправление отменено') : t('Прибытие отменено')
    emit('done', { crew: await undo(props.tripId), message })
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.trip-mark__title {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
}

// таблица ответственных занимает свободную высоту окна
.trip-mark__resp {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-height: 10rem;
}

.trip-mark__plan {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  font-size: 0.8rem;
}
</style>
