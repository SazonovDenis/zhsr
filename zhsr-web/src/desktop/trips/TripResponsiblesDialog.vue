<template>
  <FormDialog
    v-model:show="show"
    height="xl"
    title="Ответственные за вагоны"
    ok-text="Сохранить"
    width="80rem"
    :running="running"
    :error="error"
    @ok="save"
  >
    <!-- план или факт; план — с планового отправления или прибытия (выбор момента), факт — с введённого времени -->
    <div class="row items-center q-gutter-md">
      <CbSegment :model-value="kind" single required :values="kinds" @update:model-value="setKind($event as Kind)" />
      <CbSegment
        v-if="kind === 'plan'"
        :model-value="moment"
        single
        required
        :values="moments"
        @update:model-value="setMoment($event as Moment)"
      />
      <!-- у плана время подставлено и не правится -->
      <fieldset :disabled="kind === 'plan'" class="resp-dialog__time q-pa-none">
        <DatePicker
          :model-value="shownTime"
          with-time
          date-text="Время изменения"
          @update:model-value="factTime = $event"
        />
      </fieldset>
    </div>
    <div v-if="planText" class="zs-sub">{{ planText }}</div>
    <div v-if="data?.readonlyReason" class="zs-sub">{{ data.readonlyReason }}</div>
    <div class="resp-dialog__board column no-wrap">
      <TripResponsiblesBoard v-if="data" v-model="draft" :data="data" :readonly="!!data.readonlyReason" />
    </div>
    <div class="zs-sub">{{ t('Изменено вагонов: {n}', { n: changes.length }) }}</div>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, shallowRef } from 'vue'

import type { TripCrew, TripResponsibles } from '@/api/trips'
import { fetchTripCrew, fetchTripResponsibles, saveTripResponsibles } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { TripStatus } from '@/shared/constants/systemDicts'
import { TripResponsibleLayer } from '@/shared/constants/TripResponsibleLayer'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { formatDateTime, isoDateTime } from '@/shared/format'
import type { ResponsiblesDraft } from '@/shared/trips/tripResponsibles'
import { currentDraft, draftChanges, factTimeError } from '@/shared/trips/tripResponsibles'
import TripResponsiblesBoard from './TripResponsiblesBoard.vue'

/**
 * Диалог «Ответственные за вагоны» рейса (Ф-РЕЙ-08). Сверху — переключатель «План» / «Факт» и время изменения; у
 * плана между ними — переключатель «На рейс» / «После рейса» (как в панели рейса): план на рейс действует с планового
 * отправления, план после рейса — с планового прибытия, его время подставлено и не правится. Факт
 * записывается введённым временем (по умолчанию — сейчас): не в будущем и не раньше чем сутки назад — проверка при
 * сохранении. Доступность по статусу рейса: план на рейс — до отправления, план после рейса — до
 * прибытия, факт — в любом статусе. Ниже — две связанные таблицы
 * ответственных (TripResponsiblesBoard); «Сохранить» записывает изменённые вагоны. after — открыть план после рейса.
 * Нет права — таблицы только для просмотра, причина над ними. Открывается через openDialog, результат — true.
 */
const props = withDefaults(defineProps<{ tripId: number; after?: boolean }>(), { after: false })

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [saved: boolean] }>()
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок
const busy = provideBusy()

type Kind = 'plan' | 'fact'
type Moment = 'departure' | 'arrival' | 'date'

const crew = shallowRef<TripCrew | null>(null)
const data = shallowRef<TripResponsibles | null>(null)
const draft = ref<ResponsiblesDraft>({})
const kind = ref<Kind>('plan')
const moment = ref<Moment>('departure')
// значение DatePicker — время браузера без пояса, на сервер уходит с поясом
const factTime = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const running = ref(false)
const error = ref<ShownError | null>(null)

const status = computed(() => crew.value?.trip.tripStatusType)
// план на рейс правится до отправления, план после рейса — до прибытия, факт — в любом статусе, кроме «отменён»
const canDeparture = computed(() => status.value === TripStatus.PLANNED)
const canArrival = computed(() => status.value === TripStatus.PLANNED || status.value === TripStatus.IN_PROGRESS)
const canFact = computed(() => status.value !== undefined && status.value !== TripStatus.CANCELLED)

const kinds = computed<ChoiceItem[]>(() => [
  { id: 'plan', text: 'План', disabled: !canDeparture.value && !canArrival.value },
  { id: 'fact', text: 'Факт', disabled: !canFact.value },
])
const moments = computed<ChoiceItem[]>(() => [
  { id: 'departure', text: 'На рейс', disabled: !canDeparture.value },
  { id: 'arrival', text: 'После рейса', disabled: !canArrival.value },
])

const LAYERS: Record<Moment, number> = {
  departure: TripResponsibleLayer.TRIP,
  arrival: TripResponsibleLayer.AFTER,
  date: TripResponsibleLayer.FACT,
}
const layer = computed(() => LAYERS[moment.value])
const changes = computed(() => (data.value ? draftChanges(data.value, draft.value) : []))

/** Время в поле: у плана — плановое отправление или прибытие, у факта — введённое. */
const shownTime = computed(() => {
  const trip = crew.value?.trip
  if (kind.value === 'fact' || !trip) return factTime.value
  return date.formatDate(new Date(moment.value === 'arrival' ? trip.arrival : trip.plannedDt), 'YYYY-MM-DDTHH:mm')
})

/** Подпись плана: из кого выбирают ответственных; показан факт — пусто. */
const planText = computed(() => {
  if (kind.value !== 'plan' || !crew.value) return ''
  return moment.value === 'arrival'
    ? t('План после рейса — с планового прибытия {at}, из бригады и сотрудников станции прибытия', {
        at: formatDateTime(crew.value.trip.arrival),
      })
    : t('План на рейс — с планового отправления {at}, из бригады рейса', {
        at: formatDateTime(crew.value.trip.plannedDt),
      })
})

function setKind(k: Kind) {
  if (k === kind.value) return
  setMoment(k === 'fact' ? 'date' : canDeparture.value ? 'departure' : 'arrival')
}

/** Смена момента: план или факт — по моменту; другой слой — ответственные загружаются заново. */
function setMoment(m: Moment) {
  if (m === moment.value) return
  const before = layer.value
  moment.value = m
  kind.value = m === 'date' ? 'fact' : 'plan'
  if (layer.value !== before) void loadLayer()
}

async function loadLayer() {
  error.value = null
  data.value = null
  try {
    const d = await busy.run(fetchTripResponsibles(props.tripId, layer.value))
    data.value = d
    draft.value = currentDraft(d)
  } catch (e) {
    error.value = shownError(e)
  }
}

// рейс — до выбора момента: от статуса зависит, что доступно
void busy.run(async () => {
  try {
    const [c] = await Promise.all([fetchTripCrew(props.tripId), dictManager.prepareDict('WagonType')])
    crew.value = c
    if (props.after && canArrival.value) moment.value = 'arrival'
    else if (canDeparture.value) moment.value = 'departure'
    else moment.value = canFact.value ? 'date' : 'arrival'
    kind.value = moment.value === 'date' ? 'fact' : 'plan'
    await loadLayer()
  } catch (e) {
    error.value = shownError(e)
  }
})

async function save() {
  const d = data.value
  if (!d) return
  if (d.readonlyReason) {
    error.value = formError(d.readonlyReason)
    return
  }
  if (changes.value.length === 0) {
    error.value = formError(t('Ответственные не изменены'))
    return
  }
  const timeError = d.plan ? '' : factTimeError(factTime.value)
  if (timeError) {
    error.value = formError(timeError)
    return
  }
  running.value = true
  error.value = null
  try {
    const at = d.plan || !factTime.value ? null : isoDateTime(new Date(factTime.value))
    await saveTripResponsibles(props.tripId, at, changes.value, layer.value)
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
// отступы ряда (q-gutter-md) — как у соседних переключателей; свои поля fieldset убраны
.resp-dialog__time {
  min-width: 0;
  margin-right: 0;
  margin-bottom: 0;
  border: 0;

  &:disabled {
    opacity: 0.6;
  }
}

// таблицы прокручиваются внутри своей высоты
// таблицы занимают всё свободное место тела диалога: ошибка и подписи над ними сжимают таблицы, а не диалог
.resp-dialog__board {
  flex: 1 1 0;
  min-height: 0;
}
</style>
