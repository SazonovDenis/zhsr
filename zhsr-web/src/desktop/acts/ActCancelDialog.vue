<template>
  <FormDialog
    v-model:show="show"
    height="sm"
    :title="t('Отмена акта № {n}', { n: number })"
    ok-text="Отменить акт"
    :running="running"
    :error="error"
    @ok="save"
  >
    <div class="q-mb-sm">{{ t(effect) }}</div>
    <FormField label="Причина отмены">
      <NoteInput v-model="reason" :rows="4" />
    </FormField>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { cancelTransfer } from '@/api/acts'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'

/**
 * Отмена акта приёма-передачи с причиной (Ф-АКТ-06): Система удаляет записи журналов вагонов, открытые актом.
 * Открывается через openDialog, результат — true.
 */
const props = defineProps<{ actId: number; number: string }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [cancelled: true] }>()

const effect = 'Принятые вагоны вернутся на прежнее расположение и к прежнему ответственному.'

const reason = ref('')
const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  if (!reason.value.trim()) {
    error.value = formError(t('Укажите причину отмены'))
    return
  }
  error.value = null
  running.value = true
  try {
    await cancelTransfer(props.actId, reason.value)
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
