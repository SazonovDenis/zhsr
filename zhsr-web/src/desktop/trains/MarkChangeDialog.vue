<template>
  <FormDialog
    v-model:show="show"
    :title="t(attach ? 'Прицепка вагона {n}' : 'Отцепка вагона {n}', { n: change.number })"
    ok-text="Отметить выполненной"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormField label="Фактическое время">
      <DatePicker v-model="time" with-time date-text="Фактическое время" />
    </FormField>
    <!-- в рейсе прицепка и отцепка меняют журналы вагона: ответственный выбирается явно -->
    <FormField v-if="inProgress" label="Ответственный за вагон">
      <ResponsiblePicker
        v-model="responsible"
        :station="change.station"
        :trip="change.trip?.id ?? null"
        :wagons="[change.number]"
      />
    </FormField>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { ref } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { markChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { TrainWagonChangeType } from '@/shared/constants/TrainWagonChange'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import type { ResponsibleChoice } from '@/shared/wagons/responsible'
import { responsibleUsr } from '@/shared/wagons/responsible'
import ResponsiblePicker from '@/shared/wagons/ResponsiblePicker.vue'

/**
 * Отметка плановой прицепки или отцепки выполненной (Ф-ПОЕ-02, Ф-АКТ-01): фактическое время, а в рейсе в статусе
 * «выполняется» — ответственный за вагон. Открывается через openDialog, результат — поезд после отметки.
 */
const props = defineProps<{ change: TrainChange; inProgress: boolean }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [train: TrainCard] }>()

const attach = props.change.changeType === TrainWagonChangeType.ATTACH
const time = ref(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const responsible = ref<ResponsibleChoice>(null)
const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  if (props.inProgress && responsible.value === null) {
    error.value = formError(t('Выберите ответственного за вагон'))
    return
  }
  error.value = null
  running.value = true
  try {
    emit(
      'done',
      await markChange(props.change.id, isoDateTime(new Date(time.value)), responsibleUsr(responsible.value)),
    )
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
