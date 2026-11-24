<template>
  <div
    class="journal-mark q-py-xs q-pl-md q-pr-xs"
    :class="{ 'journal-mark--selected': selected }"
    role="button"
    tabindex="0"
    @click="emit('select')"
    @keydown.enter.prevent="emit('select')"
  >
    <span class="journal-mark__time">{{ formatDateTime(mark.dt).replace('\u00a0', ' ') }}</span>
    <span class="journal-mark__text">
      {{
        t(mark.kind === 'departure' ? 'Отправление рейса {trip}' : 'Прибытие рейса {trip}', {
          trip: mark.trip.trainNumber,
        })
      }}<template v-if="!mark.fact"> • {{ t('по расписанию') }}</template>
    </span>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import { formatDateTime } from '@/shared/format'
import type { JournalMark } from '@/shared/trains/trainJournal'

/**
 * Отметка рейса в журнале поезда: «Отправление рейса 655/656» или «Прибытие рейса …», у планового — «• по расписанию».
 * Разделитель на шкале времени, а не запись журнала: без «+» и «−» и без меню. Время показывается всегда, даже если
 * оно то же, что у строки выше. Клик выбирает отметку — справа состав на этот момент.
 */
defineProps<{ mark: JournalMark; selected: boolean }>()
const emit = defineEmits<{ select: [] }>()
</script>

<style scoped lang="scss">
// колонки — как у записи журнала (TrainJournalItem)
.journal-mark {
  display: grid;
  grid-template-columns: 2.8rem minmax(0, 1fr) auto;
  gap: 0.6rem;
  align-items: start;
  border-top: 0.0625rem dashed $zs-border-row;
  border-left: 0.2rem solid transparent;
  font-size: 0.8rem;
  cursor: pointer;

  &:hover {
    background: $zs-row-hover-bg;
  }

  &--selected {
    border-left-color: $zs-row-selected-bar;
    background: $zs-row-selected-bg;
  }
}

.journal-mark__text {
  font-weight: 600;
}
</style>
