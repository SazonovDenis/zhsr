<template>
  <!-- основным — вид расположения и сколько вагон в нём, серым — само место -->
  <div>{{ dictManager.getValue('LocationKind', row.locationType) }}, {{ sinceText(row.dbegLocation, nowMinute) }}</div>
  <div class="zs-sub">{{ wagonPlace(row) }}</div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import type { CellProps } from '@/shared/components/table/types'
import { nowMinute, sinceText } from '@/shared/since'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonPlace } from '@/shared/wagonPlace'

/** Расположение вагона: вид расположения, с какого времени и сколько уже длится; второй строкой — место. */
defineProps<CellProps<WagonRow>>()
</script>
