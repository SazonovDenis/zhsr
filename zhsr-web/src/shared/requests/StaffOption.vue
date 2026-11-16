<template>
  <span class="staff-option">
    <span class="staff-option__name">{{ name }}</span>
    <span v-if="candidate && candidate.crewDutyType !== null" class="staff-option__detail">
      <span :class="{ 'staff-option__match': candidate.inService }">{{
        dictManager.getValue('CrewDuty', candidate.crewDutyType)
      }}</span>
      <template v-for="w in wagons" :key="w">
        •
        <span :class="{ 'staff-option__match': w === wagonNumber }">{{ t('ваг. {n}', { n: w }) }}</span>
      </template>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { StaffCandidate } from '@/api/requests'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Строка выбора исполнителя заявки: ФИО слева, справа — должность в бригаде рейса и вагоны сотрудника (если есть).
 * Жирным — совпадения с заявкой: должность сотрудника службы заявки и вагон заявки. candidate = null — только ФИО.
 */
const props = defineProps<{ name: string; candidate: StaffCandidate | null; wagonNumber: string | null }>()

const wagons = computed(() =>
  [props.candidate?.wagon?.number, props.candidate?.wagon2?.number].filter((n): n is string => !!n),
)
</script>

<style scoped lang="scss">
.staff-option {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: 0.5rem;
  min-width: 0;
}

.staff-option__name {
  flex: 1;
  min-width: 0;
}

.staff-option__detail {
  flex: none;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.staff-option__match {
  color: $zs-text;
  font-weight: 700;
}
</style>
