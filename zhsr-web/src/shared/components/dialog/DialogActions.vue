<template>
  <div class="dialog-actions q-py-md q-px-lg">
    <slot name="left" />
    <q-space />
    <template v-for="(action, index) in visible" :key="index">
      <q-btn
        no-caps
        unelevated
        :outline="!action.default"
        :color="action.default ? 'primary' : 'grey-8'"
        :autofocus="action.default"
        :icon="action.icon"
        :label="action.title && t(action.title)"
        :disable="action.disabled || !!actionRunning"
        :loading="!!actionRunning && action.name === actionRunning"
        class="dialog-actions__btn zs-focusable"
        @click="emit('action', action)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'
import type { DialogAction } from './types'
import { isActionVisible } from './types'

/**
 * Кнопки подвала диалога. Пока действие выполняется (actionRunning), его кнопка крутит спиннер, а заблокированы все
 * кнопки: и та, что его запустила, и соседние.
 */
const props = withDefaults(defineProps<{ actions?: DialogAction[]; actionRunning?: string | null }>(), {
  actions: () => [],
  actionRunning: null,
})

const emit = defineEmits<{ action: [action: DialogAction] }>()

const visible = computed(() => props.actions.filter(isActionVisible))
</script>

<style scoped lang="scss">
.dialog-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-top: 0.0625rem solid $zs-border;
}

.dialog-actions__btn {
  min-height: var(--zs-control-height);
  font-weight: 600;
}
</style>
