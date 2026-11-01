<template>
  <button type="button" class="soft-link zs-focusable" @click="emit('click')">{{ t(text) }}</button>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * «Мягкая ссылка» — второстепенное действие рядом с полем: текст цветом ссылки, подчёркнутый пунктиром, без рамки
 * кнопки. Текст — на русском, переводится при показе.
 *
 *   <SoftLink text="Создать новый маршрут" @click="newRoute" />
 */
defineProps<{ text: string }>()

const emit = defineEmits<{ click: [] }>()
</script>

<style scoped lang="scss">
.soft-link {
  padding: 0;
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  font-size: 0.84375rem;
  font-weight: 500;
  text-decoration: underline dashed;
  text-decoration-thickness: 0.0625rem;
  text-underline-offset: 0.2em;
  white-space: nowrap;
  cursor: pointer;

  &:hover {
    text-decoration-style: solid;
  }

  &:focus-visible {
    border-radius: 0.125rem;
    outline: 0.125rem solid $primary;
    outline-offset: 0.125rem;
  }
}
</style>
