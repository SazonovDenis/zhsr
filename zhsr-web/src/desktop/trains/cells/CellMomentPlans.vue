<template>
  <div class="cell-plans">
    <div
      v-for="c in row.plans"
      :key="changeKey(c)"
      class="cell-plans__item"
      :class="{ 'cell-plans__item--selected': changeKey(c) === row.selectedPlan }"
      role="button"
      tabindex="0"
      @click.stop="plans.select(changeKey(c))"
      @keydown.enter.prevent="plans.select(changeKey(c))"
    >
      <span class="cell-plans__time">{{ formatDateTime(c.dt) }}</span>
      <span class="cell-plans__what"><ChangeTitle :change="c" /></span>
      <span>{{ t('Станция: {station}', { station: dictManager.getValue('Station', c.station) }) }}</span>
      <span v-if="c.trip">
        {{ t('Рейс:') }}
        <span @click.stop
          ><SoftLink text="{n}" :params="{ n: c.trip.trainNumber }" @click="plans.pickTrip(c.trip.id)"
        /></span>
        {{ t('от {date}', { date: formatDateTime(c.trip.plannedDt) }) }}
      </span>
      <span v-if="c.overdue" class="cell-plans__overdue">{{ t('план просрочен') }}</span>
      <span v-if="c.comment" class="cell-plans__muted">{{ c.comment }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue'

import type { TrainChange, TrainWagon } from '@/api/trains'
import { t } from '@/boot/i18n'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import ChangeTitle from '@/shared/trains/ChangeTitle.vue'
import { changeKey } from '@/shared/trains/trainJournal'
import { MOMENT_PLANS } from '../tripMoment'

/**
 * Ожидающие планы вагона после момента состава — каждый, как запись журнала: время, «Прицепка вагона 12345 на 3» или
 * «Отцепка вагона 12345», «Станция: …», «Рейс: 655/656 от 22.12 12:45» (номер — мягкой ссылкой, выбирает рейс),
 * просрочка, основания. Клик по плану выбирает его запись журнала. Справочник Station готовит страница.
 */
defineProps<CellProps<TrainWagon & { plans: TrainChange[]; selectedPlan: string | null }>>()

const plans = inject(MOMENT_PLANS)!
</script>

<style scoped lang="scss">
.cell-plans {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.cell-plans__item {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  cursor: pointer;

  &--selected {
    background: $zs-row-selected-bg;
  }
}

.cell-plans__time,
.cell-plans__muted {
  color: $zs-text-muted;
}

.cell-plans__what {
  font-weight: 600;
}

.cell-plans__overdue {
  color: $zs-error-fg;
  font-weight: 600;
}
</style>
