<template>
  <div class="fault-option" :class="`fault-option--${kind}`">
    <span
      v-if="kind === 'service'"
      class="fault-option__mark"
      :style="{ background: String(item.data?.color ?? '') }"
    />
    <span class="fault-option__text">{{ item.text }}</span>
    <span v-if="kind !== 'fault'" class="fault-option__count">{{ item.data?.count }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ChoiceItem } from '@/shared/components/choice/choice'

/** Строка дерева неисправностей в CbList: служба (заголовок с меткой цвета), группа (заголовок), неисправность. */
const props = defineProps<{ item: ChoiceItem }>()
const kind = computed(() => (typeof props.item.data?.kind === 'string' ? props.item.data.kind : 'fault'))
</script>

<style scoped lang="scss">
.fault-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: $zs-text;

  &--service {
    font-weight: 700;
  }

  &--group {
    padding-left: 1rem;
    font-size: 0.8rem;
    font-weight: 600;
  }

  &--fault {
    padding-left: 2rem;
  }
}

.fault-option__mark {
  flex: none;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 0.1rem;
}

.fault-option__text {
  flex: 1;
  min-width: 0;
}

.fault-option__count {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;
}
</style>
