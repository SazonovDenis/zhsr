<template>
  <div>{{ formatHm(row.duration) }}</div>
  <div class="zs-sub">{{ t('{km} км', { km: row.km }) }}</div>
</template>

<script setup lang="ts">
import type { RouteRow } from '@/api/routes'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import type { CellProps } from '@/shared/components/table/types'

/** Время в пути и длина маршрута второй строкой. */
defineProps<CellProps<RouteRow>>()
</script>
