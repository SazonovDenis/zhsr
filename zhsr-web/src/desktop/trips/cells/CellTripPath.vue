<template>
  <div>{{ t('{km} км', { km: row.km }) }}</div>
  <div>{{ formatHoursMin(row.duration) }}</div>
</template>

<script setup lang="ts">
import type { TripRow } from '@/api/trips'
import { t } from '@/boot/i18n'
import { formatHoursMin } from '@/desktop/routes/duration'
import type { CellProps } from '@/shared/components/table/types'

/** «В пути» рейса в две строки: длина маршрута («3921 км») и время в пути («23 ч 30 мин»). */
defineProps<CellProps<TripRow>>()
</script>
