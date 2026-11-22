<template>
  <span>
    {{ row.wagons }}
    <span v-if="row.notAdjacent" class="resp-wagons__bad">{{ t('назначен на несоседние вагоны') }}</span>
  </span>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import type { PersonLine } from './TripResponsiblesBoard.vue'

/** Вагоны сотрудника в таблице «По сотрудникам» ответственных; у проводника с несоседними вагонами — предупреждение. */
defineProps<CellProps<PersonLine>>()
</script>

<style scoped lang="scss">
.resp-wagons__bad {
  display: block;
  color: $zs-error-fg;
}
</style>
