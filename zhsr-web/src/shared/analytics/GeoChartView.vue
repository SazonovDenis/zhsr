<template>
  <ChartView :chart="view" class="geo" :class="{ 'geo--detailed': detailed }">
    <template v-if="detailed" #tools>
      <div class="geo__tools q-mb-sm">
        <PeriodPicker
          v-model:start-date="fromDay"
          v-model:end-date="toDay"
          :label="isMobile ? '' : 'Период:'"
          :presets="PRESETS"
          :periods="PERIODS"
          :nullable-start-date="false"
          :nullable-end-date="false"
        />
        <CbButton v-model="layers" :values="LAYERS" wrap />
      </div>
    </template>

    <template #default="{ data: loaded }">
      <div v-if="detailed" class="geo__legend q-mb-sm">
        <span>{{ t('отставание {n} мин и больше', { n: LATE_LIMIT }) }}</span>
        <span class="geo__ramp">
          <i v-for="step in LEGEND_STEPS" :key="step" :style="{ background: stepColor(step) }" />
        </span>
        <span>{{ t('опережение {n} мин и больше', { n: EARLY_LIMIT }) }}</span>
        <span class="geo__key"><TrainArrow :bearing="45" />{{ t('поезд') }}</span>
        <span class="geo__key"><i class="geo__dot geo__dot--wagon" />{{ t('вагон вне рейса') }}</span>
        <span class="geo__key"><i class="geo__dot geo__dot--usr" />{{ t('проводник вне рейса') }}</span>
      </div>

      <GeoChartMap class="geo__map" :chart="loaded" :minute="minute" :layers="shownLayers" :interactive="detailed" />

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
import { computed, ref, watch } from 'vue'

import type { GeoChart } from '@/api/analytics'
import { fetchGeoChart } from '@/api/analytics'
import { t } from '@/boot/i18n'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import { modelIds } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import { isMobile } from '@/shared/device'
import { dayStart, formatDateTime } from '@/shared/format'
import ChartView from './ChartView.vue'
import GeoChartMap from './GeoChartMap.vue'
import { EARLY_LIMIT, LATE_LIMIT, LEGEND_STEPS, stepColor } from './geoTrack'
import { LAYER_DONE, LAYER_OUTSIDE } from './geoLayers'
import TrainArrow from './TrainArrow.vue'
import { PERIODS, PRESETS, weekDates } from './periods'
import { useChart } from './useChart'

/**
 * Диаграмма «Текущие рейсы»: карта маршрутов, ход рейсов за период и положения поездов, вагонов и
 * проводников на момент. Момент внутри периода задаёт бегунок под картой; сначала он стоит на конце периода (не позже
 * текущего времени).
 *
 * detailed = false — ячейка дашборда: рейсы в пути сейчас. detailed = true — окно раскрытой
 * диаграммы: период, слои, легенда, бегунок, подсказки по точкам. Закономерность с цифрами уходит владельцу событием
 * find.
 */
const props = defineProps<{ detailed: boolean }>()
const emit = defineEmits<{ find: [text: string] }>()

/** Шаг бегунка, минут. */
const STEP = 10
const LAYERS = [
  { id: LAYER_OUTSIDE, text: 'Вагоны и проводники вне рейса' },
  { id: LAYER_DONE, text: 'Показать законченные рейсы' },
]
/** Рейс прибыл по графику, если отклонение не больше стольких минут. */
const ON_TIME_MINUTES = 10

const week = weekDates()
const fromDay = ref<string | null>(week.from)
const toDay = ref<string | null>(week.to)
const layers = ref<ChoiceModel>([LAYER_OUTSIDE])
const minute = ref(0)
const maxMinute = ref(0)

const shownLayers = computed(() => (props.detailed ? modelIds(layers.value).map(Number) : []))

const view = useChart(
  () => fetchGeoChart(dayStart(fromDay.value!), dayStart(toDay.value!, 1), props.detailed),
  (text) => emit('find', text),
  (chart) => {
    setRange(chart)
    return findText(chart)
  },
)
const { load } = view

/** Бегунок — на весь период, но не позже текущего времени; стоит в конце. */
function setRange(chart: GeoChart) {
  const start = new Date(chart.from).getTime()
  const end = Math.min(new Date(dayStart(toDay.value!, 1)).getTime(), Date.now())
  const minutes = Math.max(0, (end - start) / 60000)
  maxMinute.value = Math.floor(minutes / STEP) * STEP
  minute.value = maxMinute.value
}

const momentText = computed(() =>
  view.data.value
    ? formatDateTime(new Date(new Date(view.data.value.from).getTime() + minute.value * 60000).toISOString())
    : '',
)

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

.geo__dot {
  border-radius: 50%;

  &--wagon {
    width: 0.9rem;
    height: 0.9rem;
    background: #12967c;
  }

  &--usr {
    width: 0.5rem;
    height: 0.5rem;
    background: #ffc53d;
  }
}

.geo__map {
  flex: 1;
  min-height: 12rem;
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
