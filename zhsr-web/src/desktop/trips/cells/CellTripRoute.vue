<template>
  <div class="cell-trip-route__name">{{ row.train }} • {{ row.routeName }}</div>
  <div class="zs-sub">{{ t(row.routeKind) }}</div>
</template>

<script setup lang="ts">
import type { Trip } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'

/** Маршрут рейса: номер поезда, название и вид маршрута. */
defineProps<CellProps<Trip>>()
</script>

<style scoped lang="scss">
.cell-trip-route__name {
  font-weight: 600;
}
</style>
