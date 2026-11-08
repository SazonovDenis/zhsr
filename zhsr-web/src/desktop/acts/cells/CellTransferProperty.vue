<template>
  <div v-if="row.pendingAct" class="cell-transfer-property">
    <div>{{ t(row.pendingWithProperty ? 'с имуществом' : 'без имущества') }}</div>
    <div class="cell-transfer-property__note">{{ t('ожидает приёма, акт № {n}', { n: row.pendingAct }) }}</div>
  </div>
  <CbSegment v-else single :model-value="value" :values="choices" @update:model-value="pick" />
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

import type { TransferWagon } from '@/api/transfers'
import { t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import type { CellProps } from '@/shared/components/table/types'
import { TRANSFER_CHOICE } from '../transferChoice'

/**
 * Колонка «Передача» / «Приём» таблицы вагонов акта: переключатель «с имуществом / без имущества». Выбор значения
 * отмечает вагон, повторный клик по выбранному — снимает отметку. Вагон, который ждёт приёма по другому акту, не
 * выбирается: вместо переключателя — как он передаётся в том акте и номер акта.
 */
const props = defineProps<CellProps<TransferWagon>>()

const choice = inject(TRANSFER_CHOICE)!

const choices = [
  { id: 'with', text: 'с имуществом' },
  { id: 'without', text: 'без имущества' },
]

const value = computed(() => {
  const v = choice.chosen.value[props.row.number]
  return v === undefined ? null : v ? 'with' : 'without'
})

function pick(v: ChoiceModel) {
  choice.set(props.row.number, v === 'with' ? true : v === 'without' ? false : null)
}
</script>

<style scoped lang="scss">
.cell-transfer-property {
  white-space: nowrap;
}

.cell-transfer-property__note {
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
