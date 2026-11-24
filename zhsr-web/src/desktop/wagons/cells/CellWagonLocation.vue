<template>
  <!-- в рейсе — сам рейс; иначе вид и место: «Депо: Вагонное депо Астана» -->
  <div v-if="row.locationType === LocationType.TRIP">{{ wagonPlace(row) }}</div>
  <div v-else-if="row.locationType === null">—</div>
  <div v-else>{{ dictManager.getValue('LocationKind', row.locationType) }}: {{ wagonPlace(row) }}</div>
  <div class="zs-sub">
    {{ row.responsibleUsrs.length > 1 ? t('ответственные:') : t('ответственный:') }}
    <UserNames :users="row.responsibleUsrs" />
  </div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { LocationType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonPlace } from '@/shared/wagonPlace'

import UserNames from '@/shared/components/UserNames.vue'
/**
 * Расположение вагона: в рейсе — рейс («Рейс: 2/1 • Алматы — Астана»), иначе вид и место («Депо: Вагонное депо
 * Астана»); второй строкой — текущие ответственные.
 */
defineProps<CellProps<WagonRow>>()
</script>
