<template>
  <FormDialog
    v-model:show="show"
    :title="depart ? 'Отметка об отправлении' : 'Отметка о прибытии'"
    :ok-text="depart ? 'Отметить отправление' : 'Отметить прибытие'"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection columns="max-content">
      <FormField :label="depart ? 'Фактическое отправление' : 'Фактическое прибытие'">
        <DatePicker v-model="time" with-time />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { ref } from 'vue'

import type { TripCrew } from '@/api/trips'
import { markArrival, markDeparture } from '@/api/trips'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'

/**
 * Диалог отметки отправления (Ф-РЕЙ-03) или прибытия (Ф-РЕЙ-04): фактическое время, по умолчанию — сейчас. Открывается
 * через openDialog, результат — рейс после отметки.
 */
const props = defineProps<{ tripId: number; depart: boolean }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [crew: TripCrew] }>()

// значение DatePicker — время браузера без пояса, на сервер уходит с поясом
const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  if (!time.value) {
    error.value = formError(t('Укажите время'))
    return
  }
  error.value = null
  running.value = true
  try {
    const mark = props.depart ? markDeparture : markArrival
    emit('done', await mark(props.tripId, isoDateTime(new Date(time.value))))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
