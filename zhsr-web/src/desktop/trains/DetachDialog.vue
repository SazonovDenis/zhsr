<template>
  <FormDialog
    v-model:show="show"
    :title="t('Отцепка вагона {n}', { n: number })"
    ok-text="Отцепить"
    :error="error"
    @ok="save"
  >
    <FormSection columns="max-content max-content">
      <FormField label="Отцепка">
        <CbSegment v-model="mode" single required :values="modes" />
      </FormField>
      <FormField v-if="mode === 'plan' && stops.length > 0" label="Остановка">
        <CbList v-model="stopIndex" single no-search :values="stopChoices" style="width: 18rem" />
      </FormField>
      <FormField v-else-if="mode === 'plan'" label="Плановое время">
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
import type { AttachStop, ChangeChoice } from './attach'

/**
 * Отцепка вагона от поезда фактом или планом (Ф-ПОЕ-02, Ф-РЕЙ-02): план в рейсе — на остановке маршрута, план вне
 * рейса — плановое время на станции вагона; факт — время отцепки на станции вагона. Открывается через openDialog,
 * результат — ChangeChoice.
 *
 * @prop number      номер вагона
 * @prop stops       остановки рейса для плана; пусто — отцепка вне рейса
 * @prop factAllowed отцепка фактом возможна
 */
const props = withDefaults(defineProps<{ number: string; stops?: AttachStop[]; factAllowed?: boolean }>(), {
  stops: () => [],
  factAllowed: true,
})

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
  if (!planTime.value) {
    error.value = formError(t('Укажите плановое время'))
    return
  }
  emit('done', { fact: false, time: isoDateTime(new Date(planTime.value)), station: null, routeStation: null })
}
</script>
