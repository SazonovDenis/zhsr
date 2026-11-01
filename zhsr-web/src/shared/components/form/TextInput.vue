<template>
  <span class="text-input" :style="{ width }">
    <input
      v-model="model"
      class="text-input__field zs-focusable"
      :class="{ 'text-input__field--mono': mono }"
      :type="password ? 'password' : 'text'"
      :inputmode="inputmode"
      :placeholder="placeholder && t(placeholder)"
    />
    <button
      v-if="model"
      type="button"
      tabindex="-1"
      class="text-input__clear"
      :title="t('Очистить')"
      @click="model = ''"
    >
      ✕
    </button>
  </span>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * Текстовое поле по макету с крестиком сброса. placeholder — на русском, переводится при показе. Даты вводятся не
 * здесь, а в DatePicker.
 *
 * Используется и как текстовый фильтр над таблицей, и как поле формы (см. FormField).
 */
withDefaults(
  defineProps<{
    placeholder?: string
    width?: string
    /** моноширинный шрифт: номера вагонов и поездов */
    mono?: boolean
    inputmode?: 'text' | 'numeric' | 'decimal'
    /** ввод пароля: символы скрыты */
    password?: boolean
  }>(),
  { placeholder: '', width: '100%', mono: false, inputmode: 'text', password: false },
)

const model = defineModel<string>({ default: '' })
</script>

<style scoped lang="scss">
.text-input {
  position: relative;
  display: inline-flex;
  max-width: 100%;
}

.text-input__field {
  width: 100%;
  height: var(--zs-control-height);
  padding: 0 1.75rem 0 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.875rem;

  &::placeholder {
    color: $zs-text-faint;
  }

  &:focus {
    border-color: $primary;
    outline: none;
    box-shadow: 0 0 0 0.0625rem $primary;
  }

  &--mono {
    font-family: $zs-font-mono;
  }
}

.text-input__clear {
  position: absolute;
  top: 50%;
  right: 0.375rem;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.1rem;
  height: 1.1rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #eef1f4;
  color: $zs-text-faint;
  font-size: 0.5625rem;
  font-weight: 700;
  line-height: 1;
  transform: translateY(-50%);
  cursor: pointer;

  &:hover {
    background: #e1e6eb;
    color: $zs-text-muted;
  }
}
</style>
