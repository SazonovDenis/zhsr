<template>
  <FormDialog
    v-model:show="show"
    min-height="sm"
    :title="title"
    :ok-text="okText"
    width="40rem"
    @ok="emit('done', true)"
  >
    <template v-if="rows.length > 0" #warnings>
      <WagonProblemList :rows="rows" title="После этого действия будут проблемы:" strip />
    </template>
    <ErrorBox v-if="error" :error="error" />
  </FormDialog>
</template>

<script setup lang="ts">
import type { PlanProblem } from '@/api/trains'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import WagonProblemList from '@/shared/trains/WagonProblemList.vue'

/**
 * Подтверждение действия над записью журнала, у которого нет своего окна, — с предупреждениями: что будет мешать планам
 * вагона после действия. Открывается через confirmByWarnings, результат — true.
 *
 * @prop title  заголовок (на русском, переводит FormDialog)
 * @prop okText кнопка действия
 * @prop rows   предупреждения
 * @prop error  ошибка загрузки предупреждений; действие она не запрещает
 */
defineProps<{ title: string; okText: string; rows: PlanProblem[]; error: ShownError | null }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [ok: true] }>()
</script>
