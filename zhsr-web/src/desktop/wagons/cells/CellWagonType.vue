<template>
  <div>{{ dictManager.getValue('WagonType', row.type) }}</div>
  <div class="zs-sub">{{ dictManager.getValue('WagonModel', row.model) }}</div>
</template>

<script setup lang="ts">
import type { Wagon } from '@/api/wagons'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Тип вагона и модель второй строкой. */
defineProps<CellProps<Wagon>>()
</script>
