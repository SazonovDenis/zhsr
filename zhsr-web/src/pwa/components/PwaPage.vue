<template>
  <div class="pwa-page">
    <div class="pwa-page__body">
      <div v-if="back || title" class="pwa-page__head">
        <button v-if="back" type="button" class="pwa-page__back" :title="t('Назад')" @click="back">
          <SvgIcon name="arrow-back" size="1.3rem" />
        </button>
        <div class="pwa-page__titles">
          <div class="pwa-page__title">{{ title }}</div>
          <div v-if="subtitle || $slots.subtitle" class="pwa-page__subtitle">
            <slot name="subtitle">{{ subtitle }}</slot>
          </div>
        </div>
        <slot name="head" />
      </div>
      <div class="pwa-page__content">
        <slot />
      </div>
    </div>
    <div v-if="$slots.bar" class="pwa-page__bar">
      <slot name="bar" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import SvgIcon from '@/shared/icons/SvgIcon.vue'

/**
 * Экран мобильной части: шапка с кнопкой «назад» (задан back), заголовком и подзаголовком (subtitle или слот subtitle),
 * прокручиваемое тело (слот default) и нижняя панель кнопок (слот bar) — она стоит внизу экрана и не прокручивается.
 * Шапка прилипает к верху при прокрутке.
 */
withDefaults(defineProps<{ title?: string; subtitle?: string; back?: (() => void) | null }>(), {
  title: '',
  subtitle: '',
  back: null,
})
</script>

<style scoped lang="scss">
.pwa-page {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.pwa-page__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.pwa-page__head {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 1rem;
  border-bottom: 0.0625rem solid $zs-border;
  background: $zs-bg-header;
}

.pwa-page__back {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 2.8rem;
  height: 2.8rem;
  padding: 0;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.5rem;
  background: #fff;
  color: $zs-text;
  cursor: pointer;
}

.pwa-page__titles {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
}

.pwa-page__title {
  font-size: 1.25rem;
  font-weight: 700;
  line-height: 1.25;
}

.pwa-page__subtitle {
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.pwa-page__content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem 1rem 1.5rem;
}

.pwa-page__bar {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem 1rem calc(1rem + env(safe-area-inset-bottom));
  border-top: 0.0625rem solid $zs-border;
  background: #fff;
}
</style>
