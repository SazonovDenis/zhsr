<template>
  <!-- как LanguageSwitcher в kis-web: иконка языка с кратким кодом текущего языка, по клику — список языков -->
  <button type="button" class="language-switcher" :title="t('Язык')" @click.stop>
    <q-icon name="language" size="1.1rem" />
    <span v-if="!iconOnly" class="language-switcher__short">{{ current?.short }}</span>

    <q-menu :anchor="menuAnchor" :self="menuSelf" :offset="[0, 4]">
      <q-list class="q-my-sm">
        <q-item
          v-for="option in LOCALES"
          :key="option.value"
          v-close-popup
          clickable
          :active="option.value === locale"
          active-class="language-switcher__active"
          @click="setLocale(option.value)"
        >
          <q-item-section side class="language-switcher__code">{{ option.short }}</q-item-section>
          <q-item-section class="q-mr-lg">{{ option.label }}</q-item-section>
        </q-item>
      </q-list>
    </q-menu>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { i18n, t } from '@/boot/i18n'
import { LOCALES, setLocale } from '@/shared/locale'

/**
 * Переключатель языка интерфейса — как в kis-web: иконка языка и краткий код текущего языка (RU), по клику — список
 * языков. iconOnly — только иконка (свёрнутое левое меню). Цвет берётся от родителя.
 */
withDefaults(
  defineProps<{
    iconOnly?: boolean
    menuAnchor?: 'top left' | 'bottom left' | 'top right'
    menuSelf?: 'bottom left' | 'top left' | 'top right' | 'bottom right'
  }>(),
  { iconOnly: false, menuAnchor: 'top left', menuSelf: 'bottom left' },
)

const locale = computed(() => i18n.global.locale.value)
const current = computed(() => LOCALES.find((l) => l.value === locale.value))
</script>

<style scoped lang="scss">
.language-switcher {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.375rem;
  border: 0;
  border-radius: 0.25rem;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.08);
  }
}

.language-switcher__short {
  font-size: 0.8125rem;
  font-weight: 600;
}

.language-switcher__code {
  min-width: 2rem;
  color: $zs-text-muted;
  font-weight: 600;
}

.language-switcher__active {
  background: $zs-bg-hover;
  color: $primary;
}
</style>
