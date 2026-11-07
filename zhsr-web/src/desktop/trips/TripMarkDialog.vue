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
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref } from 'vue'

import type { TripCrew } from '@/api/trips'
import { cancelArrival, cancelDeparture, markArrival, markDeparture, retimeArrival, retimeDeparture } from '@/api/trips'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'

/**
 * Диалог отметки отправления (Ф-РЕЙ-03) или прибытия (Ф-РЕЙ-04): фактическое время, по умолчанию — сейчас. fix —
 * время уже сделанной отметки: тогда диалог исправляет её время, а слева — отмена отметки (10 минут после отметки).
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

async function save() {
  if (!time.value) {
    error.value = formError(t('Укажите время'))
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
      const mark = props.depart ? markDeparture : markArrival
      const message = props.depart ? t('Отправление отмечено') : t('Прибытие отмечено')
      emit('done', { crew: await mark(props.tripId, value), message })
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
