<template>
  <div class="form-field">
    <span v-if="label">{{ t(label) }}</span>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * Поле формы: подпись над контролом. Подпись — на русском, переводится при показе.
 * Обычный блок, а не <label>: клик по подписи label браузер передаёт первой кнопке внутри, и у сегментов и выбора
 * даты он сам выбирал бы первый вариант.
 */
withDefaults(defineProps<{ label?: string }>(), { label: '' })
</script>

<style scoped lang="scss">
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 500;
}
</style>
