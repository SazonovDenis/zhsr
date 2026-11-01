<template>
  <div>{{ t('{n} часов', row.durationHours) }}</div>
  <div class="zs-sub">{{ t('{km} км', { km: row.points[row.points.length - 1]?.km ?? 0 }) }}</div>
</template>

<script setup lang="ts">
import type { Route } from '@/api/routes'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'

/** Время в пути и длина маршрута второй строкой. */
defineProps<CellProps<Route>>()
</script>
