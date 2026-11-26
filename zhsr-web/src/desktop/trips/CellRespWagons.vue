<template>
  <span>
    {{ row.wagons }}
    <span v-if="row.wagonsError" class="resp-wagons__bad">{{ row.wagonsError }}</span>
  </span>
</template>

<script setup lang="ts">
import type { CellProps } from '@/shared/components/table/types'
import type { PersonLine } from './TripResponsiblesBoard.vue'

/** Вагоны сотрудника в таблице «По сотрудникам» ответственных; у проводника с несоседними вагонами или больше чем с двумя —
 * предупреждение. Строка — любой таблицы с полями wagons и wagonsError. */
defineProps<CellProps<Pick<PersonLine, 'wagons' | 'wagonsError'>>>()
</script>

<style scoped lang="scss">
.resp-wagons__bad {
  display: block;
  color: $zs-error-fg;
}
</style>
