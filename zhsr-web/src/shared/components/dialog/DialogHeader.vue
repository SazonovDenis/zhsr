<template>
  <div class="dialog-header q-py-md q-px-lg">
    <template v-for="(action, index) in leftActions" :key="index">
      <q-btn flat dense no-caps :icon="action.icon" :label="action.title && t(action.title)" @click="run(action)" />
    </template>

    <div class="dialog-header__title">{{ title }}</div>

    <template v-for="(action, index) in rightActions" :key="index">
      <q-btn
        flat
        dense
        no-caps
        tabindex="-1"
        :icon="action.icon"
        :label="action.title && t(action.title)"
        @click="run(action)"
      />
    </template>

    <button type="button" class="dialog-header__close" :title="t('Закрыть')" @click.stop="emit('close')">✕</button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'
import type { DialogAction } from './types'
import { isActionVisible } from './types'

/** Заголовок диалога: текст, кнопки действий слева и справа от него и крестик закрытия. */
const props = withDefaults(defineProps<{ title?: string; actions?: DialogAction[] }>(), {
  title: '',
  actions: () => [],
})

const emit = defineEmits<{ close: []; action: [name: string] }>()

const visible = computed(() => props.actions.filter(isActionVisible))
const leftActions = computed(() => visible.value.filter((a) => !a.right))
const rightActions = computed(() => visible.value.filter((a) => a.right))

function run(action: DialogAction) {
  if (action.exec) action.exec(action)
  else if (action.name) emit('action', action.name)
}
</script>

<style scoped lang="scss">
.dialog-header {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  border-bottom: 0.0625rem solid $zs-border;
}

.dialog-header__title {
  flex: 1;
  font-size: 1.1rem;
  font-weight: 700;
}

.dialog-header__close {
  width: 1.9rem;
  height: 1.9rem;
  border: 0;
  border-radius: 0.3rem;
  background: $zs-bg-button-soft;
  cursor: pointer;
}
</style>
