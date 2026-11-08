<template>
  <div class="dialog-actions q-py-md q-px-lg">
    <q-btn
      v-for="(action, index) in leftActions"
      :key="`left${index}`"
      no-caps
      unelevated
      outline
      color="grey-8"
      :icon="action.icon"
      :label="action.title && t(action.title)"
      :disable="action.disabled || !!actionRunning"
      class="dialog-actions__btn zs-focusable"
      @click="emit('action', action)"
    />
    <slot name="left" />
    <q-space />
    <template v-for="(action, index) in rightActions" :key="index">
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
 * Кнопки подвала диалога: кнопки с left («Отмена») — у левого края, остальные — справа. Пока действие выполняется (actionRunning), его кнопка крутит спиннер, а заблокированы все
 * кнопки: и та, что его запустила, и соседние.
 */
const props = withDefaults(defineProps<{ actions?: DialogAction[]; actionRunning?: string | null }>(), {
  actions: () => [],
  actionRunning: null,
})

const emit = defineEmits<{ action: [action: DialogAction] }>()

const visible = computed(() => props.actions.filter(isActionVisible))
// у левого края — «Отмена» и другие кнопки с left, перед слотом left; остальные — справа
const leftActions = computed(() => visible.value.filter((a) => a.left))
const rightActions = computed(() => visible.value.filter((a) => !a.left))
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
