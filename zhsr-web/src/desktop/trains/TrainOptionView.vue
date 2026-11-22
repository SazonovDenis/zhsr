<template>
  <span class="train-option">
    <span class="train-option__main">
      <span class="train-option__name">{{ name }}</span>
      <span class="train-option__sub">{{ wagons }}</span>
    </span>
    <span v-if="place || busy" class="train-option__side">
      <span v-if="place" class="train-option__sub">{{ place }}</span>
      <StatusBadge v-if="busy" tone="wait">{{ busy }}</StatusBadge>
    </span>
  </span>
</template>

<script setup lang="ts">
import StatusBadge from '@/shared/components/StatusBadge.vue'

/**
 * Строка выбора поезда — как строка вагона (WagonOption): слева обозначение, под ним число вагонов; справа где вагоны
 * поезда, под ними плашка «Занят», если поезд занят в пересекающемся рейсе.
 *
 *   <TrainOptionView name="Тулпар-1" wagons="7 вагонов" place="Станция: Астана-1" busy="" />
 */
withDefaults(defineProps<{ name: string; wagons: string; place?: string; busy?: string }>(), {
  place: '',
  busy: '',
})
</script>

<style scoped lang="scss">
.train-option {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: 0.8rem;
  // во всю ширину строки и там, где родитель — не flex: расположение прижато к правому краю
  width: 100%;
  min-width: 0;
}

.train-option__main {
  display: flex;
  flex: none;
  flex-direction: column;
}

.train-option__name {
  font-size: 0.9rem;
  font-weight: 700;
}

.train-option__sub {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.train-option__side {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
  min-width: 0;
  text-align: right;
}
</style>
