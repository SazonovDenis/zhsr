<template>
  <FormDialog
    v-model:show="show"
    :title="t('Отцепка вагона {n}', { n: number })"
    ok-text="Отцепить"
    :error="error"
    @ok="save"
  >
    <FormSection columns="max-content max-content max-content">
      <!-- состав поезда: план вне рейса или на остановке рейса поезда — первым -->
      <FormField v-if="mode === 'plan' && stops.length === 0 && tripStops.length > 0" label="Где">
        <CbList v-model="where" single no-search required :values="whereChoices" style="width: 24rem" />
      </FormField>
      <FormField label="Отцепка">
        <CbSegment v-model="mode" single required :values="modes" />
      </FormField>
      <!-- вне рейса — где отцепка: станция, где сейчас вагон; только для чтения, первой после вида отцепки -->
      <FormField v-if="(mode === 'fact' || (stops.length === 0 && where === OUT)) && station !== null" label="Станция">
        <span class="detach__station">{{
          t('{station} (где сейчас вагон)', { station: dictManager.getValue('Station', station) })
        }}</span>
      </FormField>
      <FormField v-if="mode === 'plan' && stops.length > 0" label="Остановка">
        <CbList v-model="stopIndex" single no-search :values="stopChoices" style="width: 18rem" />
      </FormField>
      <FormField v-if="mode === 'plan' && stops.length === 0 && where === OUT" label="Плановое время">
        <DatePicker v-model="planTime" with-time date-text="Плановое время" />
      </FormField>
      <FormField v-else label="Время отцепки">
        <DatePicker v-model="factTime" with-time date-text="Время отцепки" />
      </FormField>
    </FormSection>
    <div class="q-mt-sm">
      {{ t('Отцепка снимает вагон с проводников в рейсах поезда в статусе «План».') }}
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref } from 'vue'

import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError } from '@/shared/errors'
import { formatDateTime, isoDateTime } from '@/shared/format'
import type { TripRef } from '@/api/wagons'
import { tripRefText } from '@/shared/trips/tripRefText'
import type { AttachStop, ChangeChoice } from './attach'

/**
 * Отцепка вагона от поезда фактом или планом (Ф-ПОЕ-02, Ф-РЕЙ-02): план в рейсе — на остановке маршрута, план вне
 * рейса — плановое время на станции вагона; факт — время отцепки на станции вагона. Открывается через openDialog,
 * результат — ChangeChoice.
 *
 * @prop number      номер вагона
 * @prop stops       остановки рейса для плана; пусто — отцепка вне рейса
 * @prop factAllowed отцепка фактом возможна
 * @prop station     вне рейса — станция, где сейчас вагон (id справочника Station): показывается, где будет отцепка;
 *                   null — не показывать
 * @prop tripStops   состав поезда: рейсы поезда в плане с их остановками — план можно поставить и вне рейса, и на
 *                   остановке рейса (выбор «Где»); пусто — только вне рейса
 */
const props = withDefaults(
  defineProps<{
    number: string
    stops?: AttachStop[]
    factAllowed?: boolean
    station?: number | null
    tripStops?: { trip: TripRef; stops: AttachStop[] }[]
  }>(),
  { stops: () => [], factAllowed: true, station: null, tripStops: () => [] },
)

// «Где» на составе поезда: OUT — вне рейса, иначе «рейс:номер остановки»
const OUT = 'out'
const where = ref<string>(OUT)
const whereChoices = computed<ChoiceItem[]>(() => [
  {
    id: OUT,
    text:
      props.station !== null
        ? t('Вне рейса — {station}', { station: dictManager.getValue('Station', props.station) })
        : t('Вне рейса'),
  },
  ...props.tripStops.flatMap((g) =>
    g.stops.map((s, i) => ({
      id: `${g.trip.id}:${i}`,
      text: `${tripRefText(g.trip)}: ${dictManager.getValue('Station', s.station)} • ${formatDateTime(s.time)}`,
    })),
  ),
])

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [choice: ChangeChoice] }>()

const modes = computed<ChoiceItem[]>(() => [
  { id: 'fact', text: 'Фактом', disabled: !props.factAllowed },
  { id: 'plan', text: 'Планом' },
])
const mode = ref<string>(props.factAllowed ? 'fact' : 'plan')
const factTime = ref(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const planTime = ref<string | null>(null)
const stopIndex = ref<number | null>(props.stops.length > 0 ? 0 : null)
const error = ref<ShownError | null>(null)

const stopChoices = computed<ChoiceItem[]>(() =>
  props.stops.map((s, i) => ({
    id: i,
    text: `${dictManager.getValue('Station', s.station)} • ${formatDateTime(s.time)}`,
  })),
)

function save() {
  if (mode.value === 'fact') {
    emit('done', { fact: true, time: isoDateTime(new Date(factTime.value)), station: null, routeStation: null })
    return
  }
  if (props.stops.length > 0) {
    const stop = props.stops[stopIndex.value ?? 0]!
    emit('done', { fact: false, time: null, station: null, routeStation: stop.routeStation })
    return
  }
  if (where.value !== OUT) {
    const [tripId, i] = where.value.split(':').map(Number)
    const group = props.tripStops.find((g) => g.trip.id === tripId)!
    const stop = group.stops[i!]!
    emit('done', { fact: false, time: null, station: null, routeStation: stop.routeStation, trip: group.trip.id })
    return
  }
  if (!planTime.value) {
    error.value = formError(t('Укажите плановое время'))
    return
  }
  emit('done', { fact: false, time: isoDateTime(new Date(planTime.value)), station: null, routeStation: null })
}
</script>

<style scoped lang="scss">
// станция — текстом на высоте полей рядом
.detach__station {
  display: flex;
  align-items: center;
  min-height: var(--zs-control-height);
}
</style>
