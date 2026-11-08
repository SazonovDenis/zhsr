<template>
  <button type="button" class="soft-link zs-focusable q-pa-none" @click="emit('click')">
    {{ t(text, params ?? {}) }}
  </button>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * «Мягкая ссылка» — второстепенное действие рядом с полем: текст цветом ссылки, подчёркнутый пунктиром, без рамки
 * кнопки. Текст — на русском, переводится при показе.
 *
 *   <SoftLink text="Создать новый маршрут" @click="newRoute" />
 */
defineProps<{
  text: string
  /** значения для подстановки в текст: «Рейсы вагона ({n})» */
  params?: Record<string, unknown> | undefined
}>()

const emit = defineEmits<{ click: [] }>()
</script>

<style scoped lang="scss">
.soft-link {
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 500;
  text-decoration: underline dashed;
  text-decoration-thickness: 0.1rem;
  text-underline-offset: 0.2em;
  white-space: nowrap;
  cursor: pointer;

  // под мышью — сплошная. Только там, где есть наведение: на телефоне после нажатия :hover «залипает», и ссылка
  // оставалась бы сплошной
  @media (hover: hover) {
    &:hover {
      text-decoration-style: solid;
    }
  }

  &:disabled {
    opacity: 0.45;
    cursor: default;
  }

  &:focus-visible {
    border-radius: 0.1rem;
    outline: 0.1rem solid $primary;
    outline-offset: 0.1rem;
  }
}
</style>
