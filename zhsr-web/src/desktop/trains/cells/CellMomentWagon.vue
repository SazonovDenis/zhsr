<template>
  <div class="zs-mono text-weight-bold">{{ row.number }}</div>
  <div>{{ dictManager.getValue('WagonType', row.wagonType) }}</div>
  <div class="zs-sub">{{ dictManager.getValue('WagonModel', row.modelType) }}</div>
</template>

<script setup lang="ts">
import type { TrainWagon } from '@/api/trains'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Вагон в составе поезда: номер жирным, под ним тип и модель. */
defineProps<CellProps<TrainWagon>>()
</script>
