<template>
  <div class="stop-option">
    <span class="stop-option__station">{{ data.station }}</span>
    <span class="stop-option__time">{{ data.time }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ChoiceItem } from '@/shared/components/choice/choice'

/**
 * Строка выпадающего списка станций рейса: станция слева, время стоянки по расписанию прижато вправо. Значение — из
 * useChangeWhere: data — { station, time }, уже текстом.
 */
const props = defineProps<{ item: ChoiceItem }>()

const data = computed(() => props.item.data as { station: string; time: string })
</script>

<style scoped lang="scss">
.stop-option {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.stop-option__time {
  flex: none;
  color: $zs-text-muted;
  font-variant-numeric: tabular-nums;
}
</style>
