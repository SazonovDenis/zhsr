<template>
  <FormDialog
    v-model:show="show"
    :title="title"
    :ok-text="fix ? 'Сохранить время' : depart ? 'Отметить отправление' : 'Отметить прибытие'"
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
    <!-- прибытие: ответственный за все вагоны поезда — по желанию, из бригады рейса или сотрудников депо станции
         прибытия; не выбран — до назначения в форме рейса отвечают проводники. Во всю ширину окна -->
    <FormSection v-if="!depart && !fix" class="q-mt-md">
      <FormField label="Ответственный за вагоны">
        <ResponsiblePicker v-model="responsible" :station="null" :trip="tripId" />
      </FormField>
    </FormSection>
    <!-- прибытие: плановые прицепки и отцепки рейса отмечаются или отменяются до завершения рейса -->
    <div v-if="!depart && !fix && planned.length > 0" class="trip-mark__plans q-mt-md">
      <div class="trip-mark__title">{{ t('Не отмечены прицепки и отцепки рейса') }}</div>
      <div v-for="c in planned" :key="c.id" class="trip-mark__plan">
        <span class="zs-mono">{{ c.number }}</span>
        <ChangeNote :change="c" @mark="markPlan" @cancel="cancelPlan" />
      </div>
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, shallowRef } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { cancelChangePlan } from '@/api/trains'
import type { TripCrew } from '@/api/trips'
import {
  cancelArrival,
  cancelDeparture,
  fetchTripCrew,
  markArrival,
  markDeparture,
  retimeArrival,
  retimeDeparture,
} from '@/api/trips'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import type { ResponsibleChoice } from '@/shared/wagons/responsible'
import ResponsiblePicker from '@/shared/wagons/ResponsiblePicker.vue'
import FormField from '@/shared/components/form/FormField.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import ChangeNote from '../trains/ChangeNote.vue'
import MarkChangeDialog from '../trains/MarkChangeDialog.vue'

/**
 * Диалог отметки отправления (Ф-РЕЙ-03) или прибытия (Ф-РЕЙ-04): фактическое время, по умолчанию — сейчас. fix —
 * время уже сделанной отметки: тогда диалог исправляет её время, а слева — отмена отметки (10 минут после отметки).
 * Прибытие: ответственного за вагоны можно выбрать сразу — не выбран, его назначают потом в форме рейса; плановые прицепки и отцепки рейса показаны списком — рейс не завершается, пока каждую не отметят выполненной или не отменят.
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
// прибытие: ответственный за вагоны; null — не выбран, ответственный не меняется
const responsible = ref<ResponsibleChoice>(null)
const error = ref<ShownError | null>(null)

// прибытие: плановые прицепки и отцепки рейса; список свой, ошибка — в поле ошибки диалога
const changes = shallowRef<TrainChange[]>([])
const planned = computed(() => changes.value.filter((c) => c.factDt === null))

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
    } else if (props.depart) {
      emit('done', { crew: await markDeparture(props.tripId, value), message: t('Отправление отмечено') })
    } else {
      const crew = await markArrival(props.tripId, value, responsible.value)
      emit('done', { crew, message: t('Прибытие отмечено') })
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

.trip-mark__plan {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  font-size: 0.8rem;
}
</style>
