<template>
  <FormDialog
    v-model:show="show"
    height="sm"
    :title="t(attach ? 'Удалить прицепку вагона {n}' : 'Удалить отцепку вагона {n}', { n: number })"
    :ok-text="attach ? 'Удалить прицепку' : 'Удалить отцепку'"
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
 * Удаление ошибочного факта прицепки или отцепки без истории (Ф-АКТ-01, Ф-ПОЕ-02): только последний факт поезда.
 * Система удаляет факт и записи журналов вагона, которые он открыл; состав поезда с этого времени строится заново,
 * отработанный фактом план снова ожидает. Открывается через openDialog, результат — true.
 */
const props = defineProps<{ changeId: number; attach: boolean; number: string }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [undone: true] }>()

const effect = computed(() =>
  props.attach
    ? 'Вагон выйдет из поезда, как будто прицепки не было, вагоны за ним вернутся на прежние позиции; прицепка в рейсе вернёт вагону прежние расположение, состояние и ответственных. План прицепки, если он был, снова ожидает.'
    : 'Вагон вернётся в поезд на прежнюю позицию, как будто отцепки не было; отцепка в рейсе вернёт вагону прежние расположение, состояние и ответственных. План отцепки, если он был, снова ожидает. Проводники за вагоном заново не закрепляются.',
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
