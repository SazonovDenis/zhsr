<template>
  <div>{{ dictManager.getValue('WagonType', row.wagonType) }}</div>
  <div class="zs-sub">{{ dictManager.getValue('WagonModel', row.modelType) }}</div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Тип вагона и модель второй строкой. */
defineProps<CellProps<WagonRow>>()
</script>
