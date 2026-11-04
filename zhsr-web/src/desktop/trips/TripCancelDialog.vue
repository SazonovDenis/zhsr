<template>
  <FormDialog
    v-model:show="show"
    title="Отмена рейса"
    ok-text="Отменить рейс"
    :running="running"
    :error="error"
    @ok="save"
  >
    <div>{{ t('Рейс {trip} перейдёт в статус «Отменён». Расположение вагонов не меняется.', { trip: tripName }) }}</div>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { cancelTrip } from '@/api/trips'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'

/** Подтверждение отмены рейса (Ф-РЕЙ-05). Открывается через openDialog, результат — true. */
const props = defineProps<{ tripId: number; tripName: string }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [cancelled: boolean] }>()

const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  error.value = null
  running.value = true
  try {
    await cancelTrip(props.tripId)
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
