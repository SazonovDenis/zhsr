<template>
  <span class="text-input" :style="{ width }">
    <input
      ref="field"
      v-model="model"
      class="text-input__field zs-focusable q-py-none q-pl-sm"
      :class="{
        'text-input__field--mono': mono,
        'text-input__field--password': password,
        'text-input__field--password-empty': password && !model,
      }"
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
    <!-- пароль: «глаз» показывает и снова скрывает введённые символы; стоит перед крестиком, а пока крестика нет — на
         его месте у правого края -->
    <button
      v-if="password"
      type="button"
      tabindex="-1"
      class="text-input__reveal q-pa-none"
      :class="{ 'text-input__reveal--alone': !model }"
      :title="t(revealed ? 'Скрыть пароль' : 'Показать пароль')"
      @mousedown.prevent
      @click="revealed = !revealed"
    >
      <q-icon :name="revealed ? 'visibility_off' : 'visibility'" class="text-input__reveal-icon" />
    </button>
  </span>
</template>

<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'

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
const field = useTemplateRef<HTMLInputElement>('field')

/** Поставить фокус в поле. */
function focus() {
  field.value?.focus()
}

defineExpose({ focus })
</script>

<style scoped lang="scss">
.text-input {
  position: relative;
  display: inline-flex;
  max-width: 100%;
}

.text-input__field {
  width: 100%;
  // справа — место под крестик
  padding-right: calc(var(--zs-clear-size) + 0.4rem);
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: var(--zs-control-font-size);

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

  // справа — и «глаз», и крестик
  &--password {
    padding-right: calc(var(--zs-clear-size) + var(--zs-reveal-size) + 1rem);
  }

  // пароль не введён: крестика нет — справа только «глаз»
  &--password-empty {
    padding-right: calc(var(--zs-reveal-size) + 0.8rem);
  }
}

// пароль: «глаз» — перед крестиком, крестик — у правого края, как у всех полей
.text-input__reveal {
  position: absolute;
  top: 50%;
  right: calc(var(--zs-clear-size) + 0.6rem);

  &--alone {
    right: 0.4rem;
  }
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

// «глаз» — размером с крестик: на телефоне крупнее
.text-input__reveal-icon {
  font-size: var(--zs-reveal-size);
}

.text-input__clear {
  position: absolute;
  top: 50%;
  right: 0.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--zs-clear-size);
  height: var(--zs-clear-size);
  border: 0;
  border-radius: 50%;
  background: $zs-bg-button-soft;
  color: $zs-text-faint;
  font-size: var(--zs-clear-font-size);
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
