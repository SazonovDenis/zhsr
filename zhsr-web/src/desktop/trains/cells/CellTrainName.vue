<template>
  <div class="cell-train-name">{{ row.name }}</div>
  <div class="zs-sub">{{ row.wagons.length ? t('{n} вагонов', row.wagons.length) : t('нет вагонов') }}</div>
</template>

<script setup lang="ts">
import type { TrainRow } from '@/api/trains'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'

/** Обозначение поезда жирным, под ним серым — количество вагонов («23 вагона»); сами вагоны — в панели. */
defineProps<CellProps<TrainRow>>()
</script>

<style scoped lang="scss">
.cell-train-name {
  font-weight: 700;
}
</style>
