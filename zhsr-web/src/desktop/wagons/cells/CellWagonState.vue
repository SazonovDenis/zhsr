<template>
  <DictBadge :id="row.state" dict="WagonState" class="cell-wagon-state" />
  <div class="zs-sub">{{ t('с {date}', { date: formatDateTime(row.stateSince) }) }}</div>
</template>

<script setup lang="ts">
import type { Wagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'

/** Состояние вагона плашкой и с какого времени. */
defineProps<CellProps<Wagon>>()
</script>

<style scoped lang="scss">
// по макету плашка выступает влево на свой отступ: текст плашки стоит ровно по краю колонки
.cell-wagon-state {
  margin: 0 0 0.1875rem -0.5rem;
}
</style>
