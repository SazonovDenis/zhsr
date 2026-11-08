<template>
  <DictBadge v-if="row.wagonStatusTypeAt" :id="row.wagonStatusTypeAt" dict="WagonState" class="cell-wagon-state-at" />
  <div v-else class="zs-sub">—</div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'

/** Состояние вагона на момент фильтра at (по журналу) плашкой; вагона тогда не было — прочерк. */
defineProps<CellProps<WagonRow>>()
</script>

<style scoped lang="scss">
// как в колонке текущего состояния: плашка выступает влево на свой отступ
.cell-wagon-state-at {
  margin-left: -0.5rem;
}
</style>
