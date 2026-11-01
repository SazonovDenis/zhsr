<template>
  <div class="side-panel">
    <div v-if="empty" class="side-panel__empty">{{ t(empty) }}</div>

    <template v-else>
      <div class="side-panel__header">
        <div class="side-panel__titles">
          <div class="side-panel__title">{{ title }}</div>
          <div v-if="subtitle" class="side-panel__subtitle">{{ subtitle }}</div>
        </div>
        <button type="button" class="side-panel__close" :title="t('Закрыть')" @click="emit('close')">✕</button>
      </div>

      <div class="side-panel__body">
        <slot />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * Оформление правой панели фрейма по макету: заголовок, подзаголовок, крестик закрытия и тело (слот default).
 * Пока в списке ничего не выбрано, панель показывает подсказку empty («Выберите вагон в списке») — текст на русском,
 * переводится при показе.
 */
withDefaults(defineProps<{ title?: string; subtitle?: string; empty?: string }>(), {
  title: '',
  subtitle: '',
  empty: '',
})

const emit = defineEmits<{ close: [] }>()
</script>

<style scoped lang="scss">
.side-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.side-panel__empty {
  padding: 2rem 1.5rem;
  color: $zs-text-muted;
  font-size: 0.875rem;
}

.side-panel__header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1.25rem 1.5rem 0.25rem;
}

.side-panel__titles {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.375rem;
  min-width: 0;
}

.side-panel__title {
  font-size: 1.25rem;
  font-weight: 700;
}

.side-panel__subtitle {
  font-size: 0.9375rem;
  line-height: 1.35;
}

.side-panel__close {
  flex: none;
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: 0.25rem;
  background: $zs-bg-button-soft;
  font-size: 0.9375rem;
  cursor: pointer;
}

.side-panel__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1.375rem;
  padding: 1.125rem 1.5rem;
  overflow-y: auto;
}
</style>
