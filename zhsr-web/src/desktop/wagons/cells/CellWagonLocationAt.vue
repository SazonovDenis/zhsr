<template>
  <div v-if="row.locationAt">
    {{ journalPlace(row.locationAt) }}
  </div>
  <div v-else class="zs-sub">—</div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import type { CellProps } from '@/shared/components/table/types'
import { journalPlace } from '../journalText'

/** Расположение вагона на момент фильтра at (по журналу): место и вид расположения; вагона тогда не было — прочерк. */
defineProps<CellProps<WagonRow>>()
</script>
