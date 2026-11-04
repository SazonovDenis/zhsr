<template>
  <div class="side-panel">
    <div v-if="empty" class="side-panel__empty">{{ t(empty) }}</div>

    <template v-else>
      <div class="side-panel__header">
        <div class="side-panel__titles">
          <div class="side-panel__title">{{ title }}</div>
          <div v-if="subtitle" class="side-panel__subtitle">{{ subtitle }}</div>
        </div>
        <button v-if="closable" type="button" class="side-panel__close" :title="t('Закрыть')" @click="emit('close')">
          ✕
        </button>
      </div>

      <div class="side-panel__body" :class="{ 'side-panel__body--fixed': !scroll }">
        <slot />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * Оформление правой панели фрейма по макету: заголовок, подзаголовок, крестик закрытия (closable) и тело (слот
 * default).
 * Пока в списке ничего не выбрано, панель показывает подсказку empty («Выберите вагон в списке») — текст на русском,
 * переводится при показе.
 *
 * Тело прокручивается целиком (scroll); scroll = false — тело не прокручивается, прокрутку внутри себя делает
 * содержимое: например, только список остановок, а атрибуты над ним стоят на месте.
 */
withDefaults(
  defineProps<{ title?: string; subtitle?: string; empty?: string; scroll?: boolean; closable?: boolean }>(),
  {
    title: '',
    subtitle: '',
    empty: '',
    scroll: true,
    closable: true,
  },
)

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

// шапка — своим цветом из палитры таблицы (quasar.variables.scss): в варианте А — цветом выбранной строки
.side-panel__header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1.25rem 1.5rem 1rem;
  background: $zs-panel-head-bg;
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
  background: $zs-panel-close-bg;
  font-size: 0.9375rem;
  cursor: pointer;
}

.side-panel__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1.375rem;
  min-height: 0;
  padding: 1.125rem 1.5rem;
  overflow-y: auto;

  &--fixed {
    overflow-y: hidden;
  }
}
</style>
