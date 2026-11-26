<template>
  <FormDialog
    v-model:show="show"
    min-height="sm"
    :title="t(title, { n: change.wagon?.number ?? '' })"
    :ok-text="okText"
    :running="running"
    :error="error"
    @ok="save"
  >
    <template v-if="problemRows.length > 0" #warnings>
      <WagonProblemList :rows="problemRows" title="После этого действия будут проблемы:" strip />
    </template>
    <div>{{ t(effect) }}</div>
    <!-- что будет мешать планам вагона после удаления факта -->
    <ErrorBox v-if="problemsError" :error="problemsError" />
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import type { TrainChange, TrainJournalId } from '@/api/trains'
import { undoChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { TrainJournalCode, TrainWagonChangeType } from '@/shared/constants/TrainWagonChange'
import { useJournalProblems } from '@/shared/trains/useWagonProblems'
import WagonProblemList from '@/shared/trains/WagonProblemList.vue'

/** Удаляемый факт: поля записи журнала, нужные окну. */
type UndoChange = TrainJournalId & Pick<TrainChange, 'changeType' | 'plan'> & { wagon: { number: string } | null }

/**
 * Удаление ошибочного факта без истории (Ф-АКТ-01, Ф-ПОЕ-02): прицепки, отцепки или смены порядка вагонов. Система
 * удаляет факт и записи журналов вагона, которые он открыл; состав поезда с этого времени строится заново, отработанный
 * фактом план снова ожидает. Факт, который отработал план (plan), — отметка выполнения: окно называет удаление
 * «Отменить отметку о выполнении». Открывается через openDialog, результат — true.
 */
const props = defineProps<{ change: UndoChange }>()

const seq = props.change.code === TrainJournalCode.TRAIN_WAGON_SEQ
const attach = props.change.changeType === TrainWagonChangeType.ATTACH
const marked = props.change.plan !== null

// факт, который отработал план (marked), — отметка выполнения: окно называет это отменой отметки
const title = computed(() => {
  if (seq) return marked ? 'Отменить отметку смены порядка вагонов' : 'Удалить смену порядка вагонов'
  if (marked) return attach ? 'Отменить отметку прицепки вагона {n}' : 'Отменить отметку отцепки вагона {n}'
  return attach ? 'Удалить прицепку вагона {n}' : 'Удалить отцепку вагона {n}'
})
const okText = computed(() => {
  if (marked) return 'Отменить отметку о выполнении'
  if (seq) return 'Удалить смену порядка'
  return attach ? 'Удалить прицепку' : 'Удалить отцепку'
})

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [undone: true] }>()

const effect = computed(() => {
  if (seq)
    return 'Вагоны встанут по порядку, как будто смены порядка не было. План смены порядка, если он был, снова ожидает.'
  return attach
    ? 'Вагон выйдет из поезда, как будто прицепки не было, вагоны за ним вернутся на прежние позиции; прицепка в рейсе вернёт вагону прежние расположение, состояние и ответственных. План прицепки, если он был, снова ожидает.'
    : 'Вагон вернётся в поезд на прежнюю позицию, как будто отцепки не было; отцепка в рейсе вернёт вагону прежние расположение, состояние и ответственных. План отцепки, если он был, снова ожидает. Проводники за вагоном заново не закрепляются.'
})

const { rows: problemRows, error: problemsError } = useJournalProblems(() => ({
  change: props.change,
  action: 'undo',
}))

const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  error.value = null
  running.value = true
  try {
    await undoChange(props.change)
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
