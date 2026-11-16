<template>
  <div class="note-input" :class="{ 'note-input--touch': isMobile }">
    <textarea
      ref="field"
      v-model="model"
      class="note-input__field zs-focusable"
      :rows="rows"
      :placeholder="placeholder && t(placeholder)"
      @blur="emit('blur')"
    />
    <!-- mousedown.prevent: нажатие на крестик не уводит фокус из поля -->
    <button
      v-if="model || clearEmpty"
      type="button"
      tabindex="-1"
      class="note-input__clear q-pa-none"
      :title="t('Очистить')"
      @mousedown.prevent
      @click="clear"
    >
      ✕
    </button>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, useTemplateRef } from 'vue'

import { t } from '@/boot/i18n'
import { isMobile } from '@/shared/device'

/**
 * Многострочный ввод текста (примечание, описание, причина) с крестиком очистки в правом верхнем углу. placeholder — на
 * русском, переводится при показе. autofocus — фокус в поле сразу после появления.
 *
 * Крестик очищает текст, оставляет фокус в поле и посылает событие clear: форма может по нему и выйти из
 * редактирования (например, на телефоне — закрыть поле). blur — фокус ушёл из поля. Крестик виден только при тексте;
 * clearEmpty — и при пустом поле: форма закрывает по нему поле.
 */
const props = withDefaults(
  defineProps<{ placeholder?: string; rows?: number; autofocus?: boolean; clearEmpty?: boolean }>(),
  { placeholder: '', rows: 3, autofocus: false, clearEmpty: false },
)

const model = defineModel<string>({ default: '' })
const emit = defineEmits<{ clear: []; blur: [] }>()
const field = useTemplateRef<HTMLTextAreaElement>('field')

function clear() {
  model.value = ''
  emit('clear')
  // форма могла убрать поле по clear — тогда фокус ставить некуда
  void nextTick(() => field.value?.focus())
}

/** Поставить фокус в поле. */
function focus() {
  field.value?.focus()
}

onMounted(() => {
  if (props.autofocus) focus()
})

defineExpose({ focus })
</script>

<style scoped lang="scss">
.note-input {
  position: relative;
  width: 100%;
}

.note-input__field {
  display: block;
  width: 100%;
  // справа — место под крестик
  padding: 0.5rem calc(var(--zs-clear-size) + 0.8rem) 0.5rem 0.5rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  resize: vertical;

  &::placeholder {
    color: $zs-text-faint;
  }

  &:focus {
    border-color: $primary;
    outline: none;
    box-shadow: 0 0 0 0.0625rem $primary;
  }
}

.note-input__clear {
  position: absolute;
  top: 0.4rem;
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
  cursor: pointer;

  &:hover {
    background: $zs-border-row;
    color: $zs-text-muted;
  }
}

// телефон: стандартный шрифт, поле не растягивается рукой; размер крестика — --zs-clear-size телефона
.note-input--touch .note-input__field {
  padding: 0.8rem calc(var(--zs-clear-size) + 0.8rem) 0.8rem 0.8rem;
  font-size: 1rem;
  resize: none;
}
</style>
