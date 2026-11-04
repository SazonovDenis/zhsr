<template>
  <div>{{ dictManager.getValue('RouteKind', row.routeKind) }}</div>
  <div class="zs-sub">{{ dictManager.getValue('TransportType', row.transportType) }}</div>
</template>

<script setup lang="ts">
import type { RouteRow } from '@/api/routes'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Вид маршрута и вид перевозки второй строкой. */
defineProps<CellProps<RouteRow>>()
</script>
