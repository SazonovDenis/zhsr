<template>
  <div>{{ t('{km} км', { km: row.km }) }}</div>
  <div>{{ formatHoursMin(row.duration) }}</div>
</template>

<script setup lang="ts">
import type { RouteRow } from '@/api/routes'
import { t } from '@/boot/i18n'
import { formatHoursMin } from '@/desktop/routes/duration'
import type { CellProps } from '@/shared/components/table/types'

/** Путь маршрута в две строки, как в списке рейсов: длина («3921 км») и время в пути («23 ч 30 мин»). */
defineProps<CellProps<RouteRow>>()
</script>
