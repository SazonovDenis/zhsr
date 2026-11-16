<template>
  <FormDialog
    v-model:show="show"
    :title="t(attach ? 'Отмена прицепки вагона {n}' : 'Отмена отцепки вагона {n}', { n: number })"
    :ok-text="attach ? 'Отменить прицепку' : 'Отменить отцепку'"
    :running="running"
    :error="error"
    @ok="save"
  >
    <div>{{ t(effect) }}</div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { undoChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'

/**
 * Отмена ошибочной прицепки или отцепки без истории (Ф-АКТ-01, Ф-ПОЕ-02): Система удаляет прицепку или отцепку и записи
 * журналов вагона, которые она открыла. Открывается через openDialog, результат — true.
 */
const props = defineProps<{ changeId: number; attach: boolean; number: string }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [undone: true] }>()

const effect = computed(() =>
  props.attach
    ? 'Вагон выйдет из поезда, как будто прицепки не было; прицепка в рейсе вернёт вагону прежние расположение, состояние и ответственного. Вагон снимается с проводников.'
    : 'Вагон вернётся в поезд, как будто отцепки не было; отцепка в рейсе вернёт вагону прежние расположение, состояние и ответственного. Проводники за вагоном заново не закрепляются.',
)

const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  error.value = null
  running.value = true
  try {
    await undoChange(props.changeId)
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
