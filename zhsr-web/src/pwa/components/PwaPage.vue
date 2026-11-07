<template>
  <div class="pwa-page">
    <div ref="body" class="pwa-page__body" :class="{ 'pwa-page__body--white': white }">
      <div v-if="back || title || stale" class="pwa-page__head">
        <div v-if="back || title" class="pwa-page__head-row q-pa-sm">
          <button v-if="back" type="button" class="pwa-page__back q-pa-none" :title="t('Назад')" @click="back">
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
        <div v-if="stale" class="pwa-page__stale q-py-xs q-px-sm">
          {{ t('Нет связи — показаны сохранённые данные') }}
        </div>
      </div>
      <div class="pwa-page__content q-py-md q-px-sm">
        <slot />
      </div>
    </div>
    <div v-if="$slots.fab" class="pwa-page__fab">
      <slot name="fab" />
    </div>
    <div v-if="$slots.bar" class="pwa-page__bar">
      <slot name="bar" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useTemplateRef } from 'vue'

import { t } from '@/boot/i18n'
import SvgIcon from '@/shared/icons/SvgIcon.vue'

/**
 * Экран мобильной части: шапка с кнопкой «назад» (задан back), заголовком и подзаголовком (subtitle или слот subtitle),
 * прокручиваемое тело (слот default) и нижняя панель кнопок (слот bar) — она стоит внизу экрана и не прокручивается.
 * Слот fab — плавающая кнопка в правом нижнем углу тела.
 * Шапка прилипает к верху при прокрутке. Показаны сохранённые данные (stale) — внизу шапки во всю ширину полоса об этом;
 * у страницы без заголовка шапка состоит из одной этой полосы.
 */
withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    back?: (() => void) | null
    stale?: boolean
    /** тело белое, серый фон — только в шапке */
    white?: boolean
  }>(),
  { title: '', subtitle: '', back: null, stale: false, white: false },
)

// прокручиваемое тело — странице, которая запоминает прокрутку
const body = useTemplateRef<HTMLElement>('body')
defineExpose({ body })
</script>

<style scoped lang="scss">
.pwa-page {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.pwa-page__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;

  &--white {
    background: #fff;
  }
}

.pwa-page__head {
  position: sticky;
  top: 0;
  z-index: 3;
  border-bottom: 0.0625rem solid $zs-border;
  background: $zs-bg-header;
}

.pwa-page__head-row {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.pwa-page__stale {
  text-align: center;
  background: #fff1cc;
  color: #7a5200;
  font-size: 0.8rem;
  font-weight: 600;
}

.pwa-page__back {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 2.8rem;
  height: 2.8rem;
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
  gap: 0.1rem;
  min-width: 0;
}

.pwa-page__title {
  font-size: 1.3rem;
  font-weight: 700;
  line-height: 1.25;
}

.pwa-page__subtitle {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.pwa-page__content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.pwa-page__fab {
  position: absolute;
  right: 0.8rem;
  bottom: 0.8rem;
  z-index: 2;
}

.pwa-page__bar {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.8rem 0.5rem calc(1rem + env(safe-area-inset-bottom));
  border-top: 0.0625rem solid $zs-border;
  background: #fff;
}
</style>
