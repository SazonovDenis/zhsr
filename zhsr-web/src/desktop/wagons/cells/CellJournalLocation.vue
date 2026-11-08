<template>
  <span v-if="!row.locationChanged" class="zs-sub">—</span>
  <span v-else>
    <template v-if="row.prevLocation">
      <span class="zs-sub">{{ journalPlace(row.prevLocation) }}</span>
      <span class="zs-sub"> → </span>
    </template>
    <span class="cell-journal__to">{{ row.location ? journalPlace(row.location) : '' }}</span>
  </span>
</template>

<script setup lang="ts">
import type { CellProps } from '@/shared/components/table/types'
import type { JournalRow } from '../journalColumns'
import { journalPlace } from '../journalText'

/** Смена расположения: откуда → куда; первая запись — только куда; событие расположение не меняло — прочерк. */
defineProps<CellProps<JournalRow>>()
</script>

<style scoped lang="scss">
.cell-journal__to {
  font-weight: 500;
}
</style>
