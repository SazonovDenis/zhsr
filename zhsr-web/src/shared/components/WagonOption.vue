<template>
  <span class="wagon-option">
    <span class="wagon-option__main">
      <span class="wagon-option__number zs-mono">{{ number }}</span>
      <span class="wagon-option__type">{{ type }}</span>
    </span>
    <span v-if="place || badge" class="wagon-option__side">
      <span v-if="place" class="wagon-option__place">{{ place }}</span>
      <StatusBadge v-if="badge" tone="selected">{{ badge }}</StatusBadge>
    </span>
  </span>
</template>

<script setup lang="ts">
import StatusBadge from './StatusBadge.vue'

/**
 * Строка выбора вагона — одна на всё приложение: в выпадающем списке вагона на десктопе и в списке вагонов на
 * телефоне. Слева номер, под ним тип; справа расположение, под ним плашка (например, «Ваш рейс»).
 *
 *   <WagonOption number="17431" type="Плацкартный" place="Рейс 2/1 • Алматы — Астана" badge="Ваш рейс" />
 */
withDefaults(defineProps<{ number: string; type: string; place?: string; badge?: string }>(), {
  place: '',
  badge: '',
})
</script>

<style scoped lang="scss">
.wagon-option {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: 0.8rem;
  // во всю ширину строки и там, где родитель — не flex: расположение прижато к правому краю
  width: 100%;
  min-width: 0;
}

.wagon-option__main {
  display: flex;
  flex: none;
  flex-direction: column;
}

.wagon-option__number {
  font-size: 1rem;
  font-weight: 700;
}

.wagon-option__type {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.wagon-option__side {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
  min-width: 0;
  text-align: right;
}

.wagon-option__place {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
