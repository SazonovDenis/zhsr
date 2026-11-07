<template>
  <span class="text-input" :style="{ width }">
    <input
      v-model="model"
      class="text-input__field zs-focusable q-py-none q-pr-lg q-pl-sm"
      :class="{ 'text-input__field--mono': mono, 'text-input__field--password': password }"
      :type="password && !revealed ? 'password' : 'text'"
      :inputmode="inputmode"
      :placeholder="placeholder && t(placeholder)"
    />
    <button
      v-if="model"
      type="button"
      tabindex="-1"
      class="text-input__clear q-pa-none"
      :title="t('Очистить')"
      @click="model = ''"
    >
      ✕
    </button>
    <!-- пароль: «глаз» показывает и снова скрывает введённые символы -->
    <button
      v-if="password"
      type="button"
      tabindex="-1"
      class="text-input__reveal q-pa-none"
      :title="t(revealed ? 'Скрыть пароль' : 'Показать пароль')"
      @mousedown.prevent
      @click="revealed = !revealed"
    >
      <q-icon :name="revealed ? 'visibility_off' : 'visibility'" size="1.1rem" />
    </button>
  </span>
</template>

<script setup lang="ts">
import { ref } from 'vue'

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
    /** ввод пароля: символы скрыты, «глаз» справа показывает их */
    password?: boolean
  }>(),
  { placeholder: '', width: '100%', mono: false, inputmode: 'text', password: false },
)

const model = defineModel<string>({ default: '' })
// пароль показан открытым текстом
const revealed = ref(false)
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
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;

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

  // справа — и крестик, и «глаз»
  &--password {
    padding-right: 3.1rem;
  }
}

.text-input__field--password ~ .text-input__clear {
  right: 1.9rem;
}

.text-input__reveal {
  position: absolute;
  top: 50%;
  right: 0.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: none;
  color: $zs-text-faint;
  transform: translateY(-50%);
  cursor: pointer;

  &:hover {
    color: $zs-text-muted;
  }
}

.text-input__clear {
  position: absolute;
  top: 50%;
  right: 0.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.1rem;
  height: 1.1rem;
  border: 0;
  border-radius: 50%;
  background: $zs-bg-button-soft;
  color: $zs-text-faint;
  font-size: 0.6rem;
  font-weight: 700;
  line-height: 1;
  transform: translateY(-50%);
  cursor: pointer;

  &:hover {
    background: $zs-border-row;
    color: $zs-text-muted;
  }
}
</style>
