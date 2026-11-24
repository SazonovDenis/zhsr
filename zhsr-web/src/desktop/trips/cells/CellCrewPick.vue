<template>
  <div class="cell-crew-pick">
    <q-icon v-if="pick.isBusy(row.id)" name="lock" size="1.4rem" color="grey-6" :title="t('Занят в другом рейсе')" />
    <q-checkbox
      v-else
      :model-value="pick.isPicked(row.id)"
      :disable="!!pick.reason()"
      :title="pick.reason() || undefined"
      dense
      @click.stop
      @update:model-value="pick.toggle(row.id)"
    />
    <div>
      <div>{{ row.fullName }}</div>
      <div v-if="row.login" class="zs-sub zs-mono">{{ row.login }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue'

import { t } from '@/boot/i18n'
import type { StaffRow } from '@/api/staff'
import type { CellProps } from '@/shared/components/table/types'
import { CREW_PICK } from '../crewPick'

/** ФИО проводника с отметкой выбора в диалоге «Поездная бригада»; под ФИО серым — логин (администратору). */
defineProps<CellProps<StaffRow>>()

const pick = inject(CREW_PICK)!
</script>

<style scoped lang="scss">
.cell-crew-pick {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
