<template>
  <div v-if="pick.isPicked(row.id)">{{ t('Назначен в этот рейс') }}</div>
  <template v-else-if="busy?.trip && pick.isBusy(row.id)">
    <div class="cell-crew-plan__busy">{{ t('Занят до {until}', { until: formatDateTime(busy.until) }) }}</div>
    <div>{{ tripRefDatedText(busy.trip) }}</div>
  </template>
  <div v-else-if="busy?.trip">
    <b>{{ t('Свободен с {until}', { until: formatDateTime(busy.until) }) }}</b
    >, {{ t('после рейса: {trip}', { trip: tripRefDatedText(busy.trip) }) }}
  </div>
  <div v-else>—</div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

import { t } from '@/boot/i18n'
import type { StaffRow } from '@/api/staff'
import type { CellProps } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { CREW_PICK } from '../crewPick'

/**
 * «Назначен» в диалоге «Поездная бригада» пересчитывается по галочке: отмеченный — «Назначен в этот рейс»; иначе
 * другой рейс проводника: пересекающийся с этим — «Занят до …» и рейс, предыдущий — жирное «Свободен с …», затем «, после рейса: …»; рейсов нет — тире.
 */
const props = defineProps<CellProps<StaffRow>>()

const pick = inject(CREW_PICK)!
const busy = computed(() => pick.busy(props.row.id))
</script>

<style scoped lang="scss">
.cell-crew-plan__busy {
  color: #8b1e3f;
  font-weight: 700;
}
</style>
