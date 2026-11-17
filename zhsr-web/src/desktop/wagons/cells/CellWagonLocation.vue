<template>
  <!-- в рейсе — сам рейс; иначе вид и место: «Депо: Вагонное депо Астана» -->
  <div v-if="row.locationType === LocationType.TRIP">{{ wagonPlace(row) }}</div>
  <div v-else>{{ dictManager.getValue('LocationKind', row.locationType) }}: {{ wagonPlace(row) }}</div>
  <div class="zs-sub">
    {{ t('ответственный:') }}
    <UserName v-if="row.responsibleUsr" :user="row.responsibleUsr" /><template v-else>{{ t('не назначен') }}</template>
  </div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { LocationType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonPlace } from '@/shared/wagonPlace'

import UserName from '@/shared/components/UserName.vue'
/**
 * Расположение вагона: в рейсе — рейс («Рейс 2/1 • Алматы — Астана»), иначе вид и место («Депо: Вагонное депо
 * Астана»); второй строкой — текущий ответственный.
 */
defineProps<CellProps<WagonRow>>()
</script>
