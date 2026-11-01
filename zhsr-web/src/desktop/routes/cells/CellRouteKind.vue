<template>
  <div>{{ dictManager.getValue('RouteKind', row.kind) }}</div>
  <div class="zs-sub">{{ t(row.transport) }}</div>
</template>

<script setup lang="ts">
import type { Route } from '@/api/routes'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Вид маршрута и вид перевозки второй строкой. */
defineProps<CellProps<Route>>()
</script>
