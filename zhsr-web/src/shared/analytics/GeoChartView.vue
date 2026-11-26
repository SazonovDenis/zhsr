<template>
  <ChartView :chart="view" class="geo" :class="{ 'geo--detailed': detailed }">
    <template v-if="detailed" #tools>
      <div class="geo__tools q-mb-sm">
        <PeriodPicker
          v-model:start-date="fromDay"
          v-model:end-date="toDay"
          :label="isMobile ? '' : 'Период:'"
          :presets="GEO_PRESETS"
          :periods="GEO_PERIODS"
          :nullable-start-date="false"
          :nullable-end-date="false"
        />
        <CbButton v-model="layers" :values="LAYERS" wrap />
      </div>
    </template>

    <template #default="{ data: loaded }">
      <div v-if="detailed" class="geo__legend q-mb-sm">
        <span>{{ t('отставание больше {n} мин', { n: LATE_LIMIT }) }}</span>
        <span class="geo__ramp">
          <i v-for="step in LEGEND_STEPS" :key="step" :style="{ background: stepColor(step) }" />
        </span>
        <span>{{ t('опережение больше {n} мин', { n: EARLY_LIMIT }) }}</span>
        <span class="geo__key"><TrainArrow :bearing="45" />{{ t('поезд') }}</span>
        <span class="geo__key"
          ><i class="geo__box geo__box--wait"><SvgIcon name="wagons" size="0.9rem" /></i
          >{{ t('вагон ждёт прицепки') }}</span
        >
        <span class="geo__key"
          ><i class="geo__box geo__box--left"><SvgIcon name="wagons" size="0.9rem" /></i>{{ t('вагон отцеплен') }}</span
        >
        <span class="geo__key"
          ><i class="geo__box geo__box--overdue"><SvgIcon name="wagons" size="0.9rem" /></i
          >{{ t('план просрочен') }}</span
        >
        <span class="geo__key">
          <i class="geo__box geo__box--usr"><q-icon name="man" size="0.9rem" /></i>{{ t('проводник') }}
        </span>
      </div>

      <div class="geo__map">
        <GeoChartMap
          class="geo__canvas"
          :chart="loaded"
          :minute="minute"
          :now="nowMinute"
          :layers="shownLayers"
          :interactive="detailed"
          @trip="openTrip"
        />
        <StatusBadge v-if="detailed" :tone="minute > nowMinute ? 'wait' : 'ok'" class="geo__badge">
          {{ momentBadgeText(minute > nowMinute, momentIso) }}
        </StatusBadge>
      </div>

      <div v-if="detailed" class="geo__time q-mt-sm">
        <span class="geo__moment">{{ momentText }}</span>
        <q-slider
          v-model="minute"
          class="geo__slider q-mx-sm"
          :min="0"
          :max="maxMinute"
          :step="STEP"
          :aria-label="t('Дата и время')"
        />
      </div>
    </template>
  </ChartView>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { GeoChart, GeoTrip } from '@/api/analytics'
import { fetchGeoChart } from '@/api/analytics'
import { t } from '@/boot/i18n'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import { modelIds } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { TrainWagonKind } from '@/shared/constants/TrainWagonChange'
import { dictManager } from '@/shared/dict/DictManager'
import { isMobile } from '@/shared/device'
import { dayStart, formatDateTime } from '@/shared/format'
import { momentBadgeText } from '@/shared/trains/trainJournal'
import ChartView from './ChartView.vue'
import GeoChartMap from './GeoChartMap.vue'
import { tripTimes } from './geoCycle'
import { EARLY_LIMIT, LATE_LIMIT, LEGEND_STEPS, stepColor } from './geoTrack'
import { LAYER_DONE } from './geoLayers'
import TrainArrow from './TrainArrow.vue'
import { GEO_PERIODS, GEO_PRESETS, weekDates } from './periods'
import { useChart } from './useChart'

/**
 * Диаграмма «Текущие рейсы»: карта маршрутов, ход рейсов за период и положения поездов, вагонов и
 * проводников на момент. Момент задаёт бегунок под картой: от начала периода до его конца, а если у рейсов периода есть
 * планы позже, то до последнего из них (ожидающие записи циклов и ожидаемые прибытия); сначала он стоит на конце периода
 * (не позже текущего времени). После текущего времени на карте ожидаемое по плану.
 * На десктопе в обоих видах клик по значку поезда на карте открывает список рейсов
 * маршрута с этим рейсом.
 *
 * detailed = false — ячейка дашборда: рейсы в пути сейчас. detailed = true — окно раскрытой
 * диаграммы: период, слои, легенда, бегунок, подсказки по точкам. Закономерность с цифрами уходит владельцу событием
 * find.
 */
const props = defineProps<{ detailed: boolean }>()
const emit = defineEmits<{ find: [text: string] }>()

/** Шаг бегунка, минут. */
const STEP = 10
const LAYERS = [{ id: LAYER_DONE, text: 'Показать законченные рейсы' }]
/** Рейс прибыл по графику, если отклонение не больше стольких минут. */
const ON_TIME_MINUTES = 10

const week = weekDates()
const fromDay = ref<string | null>(week.from)
const toDay = ref<string | null>(week.to)
const layers = ref<ChoiceModel>([])
const minute = ref(0)
const maxMinute = ref(0)
/** «Сейчас» — время загрузки данных, минуты от начала периода. */
const nowMinute = ref(0)

const shownLayers = computed(() => (props.detailed ? modelIds(layers.value).map(Number) : []))

const view = useChart(
  async () => {
    const [chart] = await Promise.all([
      fetchGeoChart(dayStart(fromDay.value!), dayStart(toDay.value!, 1), props.detailed),
      // названия мест в подсказках значков
      dictManager.prepareDicts('Station', 'Depot', 'Yard'),
    ])
    return chart
  },
  (text) => emit('find', text),
  (chart) => {
    setRange(chart)
    return findText(chart)
  },
)
const { load } = view

/**
 * Бегунок — на весь период, но не позже текущего времени, и дальше до последнего плана рейсов периода; стоит на конце
 * периода не позже текущего времени.
 */
function setRange(chart: GeoChart) {
  const now = Date.now()
  const start = new Date(chart.from).getTime()
  const minutes = (time: number) => Math.max(0, (time - start) / 60000)
  nowMinute.value = (now - start) / 60000
  const current = Math.floor(minutes(Math.min(new Date(dayStart(toDay.value!, 1)).getTime(), now)) / STEP) * STEP
  const plans = chart.trips.flatMap((trip) => [
    ...(trip.arrivedDt ? [] : [tripTimes(trip, now).arrival]),
    ...trip.changes.filter((c) => c.kind === TrainWagonKind.PLAN).map((c) => new Date(c.moment).getTime()),
  ])
  maxMinute.value = Math.max(current, ...plans.map((x) => Math.ceil(minutes(x) / STEP) * STEP))
  minute.value = current
}

const momentIso = computed(() =>
  view.data.value
    ? new Date(new Date(view.data.value.from).getTime() + minute.value * 60000).toISOString()
    : new Date().toISOString(),
)
const momentText = computed(() => formatDateTime(momentIso.value))

/** Закономерность: доли прибывших рейсов с отставанием и с опережением. */
function findText(chart: GeoChart): string {
  const delays = chart.trips.filter((x) => x.arrivedDt && x.track.length).map((x) => x.track[x.track.length - 1]![2])
  if (delays.length === 0) return ''
  const pct = (n: number) => Math.round((n / delays.length) * 100) + '%'
  return t(
    'За период прибыло рейсов: {count}. С отставанием больше {n} мин - {late}, с опережением больше {n} мин - {early}.',
    {
      count: delays.length,
      n: ON_TIME_MINUTES,
      late: pct(delays.filter((d) => d > ON_TIME_MINUTES).length),
      early: pct(delays.filter((d) => d < -ON_TIME_MINUTES).length),
    },
  )
}

const router = useRouter()

/**
 * Список рейсов с отбором по маршруту, состоянию и периоду рейса, рейс выбран (TripsPage). Период — с отправления по
 * прибытие, у рейса в пути — по сегодня, у неотправленного — день планового отправления.
 */
function openTrip(trip: GeoTrip) {
  const day = (d: Date) => date.formatDate(d, 'YYYY-MM-DD')
  void router.push({
    name: 'trips',
    query: {
      route: String(trip.route),
      // состояние рейса сейчас, а не на момент бегунка: список отбирает по нему
      state: String(trip.tripStatusType),
      from: day(new Date(trip.departedDt ?? trip.plannedDt)),
      to: day(new Date(trip.arrivedDt ?? (trip.departedDt ? Date.now() : trip.plannedDt))),
      trip: String(trip.id),
    },
  })
}

watch([fromDay, toDay], () => void load())

void load()
</script>

<style scoped lang="scss">
.geo__tools,
.geo__legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.geo__legend {
  color: $zs-text;
  font-size: 0.8rem;
}

.geo__ramp {
  display: inline-flex;

  i {
    width: 1.6rem;
    height: 0.8rem;
  }
}

.geo__key {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.geo__box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.6rem;
  height: 1.3rem;
  border-radius: 0.3rem;

  &--wait {
    background: #ffc53d;
  }

  &--left {
    background: #12967c;
    color: #fff;
  }

  &--overdue {
    background: $zs-error-fg;
    color: #fff;
  }

  &--usr {
    border: 0.1rem solid #22303f;
    background: #fff;
  }
}

.geo__map {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 12rem;
}

.geo__canvas {
  flex: 1;
}

// поверх слоёв и плашки Leaflet
.geo__badge {
  position: absolute;
  z-index: 1;
  top: 0.5rem;
  right: 0.5rem;
}

.geo--detailed .geo__map {
  min-height: 26rem;
}

.geo__time {
  display: flex;
  align-items: center;
}

.geo__moment {
  flex: none;
  min-width: 6rem;
  font-variant-numeric: tabular-nums;
}

.geo__slider {
  flex: 1;
}
</style>
