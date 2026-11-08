<template>
  <BottomSheet v-model:show="show" :title="title" :subtitle="subtitle">
    <ErrorBox v-if="error" :error="error" :retry="retry" class="pick-sheet__error q-py-sm q-px-md" />
    <div v-else class="pick-sheet">
      <button
        v-for="o in options"
        :key="String(o.id)"
        type="button"
        class="pick-sheet__item q-py-xs q-px-md"
        :class="{ 'pick-sheet__item--on': picked === o.id }"
        :disabled="o.disabled"
        @click="picked = o.id"
      >
        <span>{{ o.name }}</span>
        <span v-if="o.hint" class="pick-sheet__hint" :class="{ 'pick-sheet__hint--warn': o.warn }">{{ o.hint }}</span>
      </button>
    </div>
    <template #buttons>
      <button type="button" class="pick-sheet__btn q-py-none q-px-md" @click="show = false">{{ t('Отмена') }}</button>
      <button
        type="button"
        class="pick-sheet__btn pick-sheet__btn--primary q-py-none q-px-md"
        :disabled="!!error || disabled"
        @click="confirm"
      >
        {{ t(okText) }}
      </button>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts" generic="T extends string | number | null">
import { shallowRef, watch } from 'vue'

import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import BottomSheet from './BottomSheet.vue'

/** Вариант выбора. */
export interface PickOption<T> {
  id: T
  name: string
  /** вторая строка мелким */
  hint?: string
  /** hint — предупреждением */
  warn?: boolean
  disabled?: boolean
}

/**
 * Назначение: лист снизу со списком вариантов и кнопками «Отмена» и «Назначить». Выбор — нажатием на строку, применяется
 * кнопкой: событие pick(id). selected — что выбрано при открытии. error — вместо списка, например нет связи; retry —
 * повторить загрузку.
 */
const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    options: PickOption<T>[]
    selected: T
    error?: ShownError | null
    retry?: (() => unknown) | null
    disabled?: boolean
    /** текст кнопки применения на русском */
    okText?: string
  }>(),
  { subtitle: '', error: null, retry: null, disabled: false, okText: 'Назначить' },
)

const emit = defineEmits<{ pick: [id: T] }>()

const show = defineModel<boolean>('show', { default: false })

const picked = shallowRef<T>(props.selected)

watch(show, (open) => {
  if (open) picked.value = props.selected
})

function confirm() {
  show.value = false
  emit('pick', picked.value)
}
</script>

<style scoped lang="scss">
.pick-sheet {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  overflow-y: auto;
}

.pick-sheet__item {
  display: flex;
  flex-direction: column;
  min-height: 2.8rem;
  justify-content: center;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }

  &--on {
    border-color: $primary;
    background: $zs-selected-bg;
    color: $zs-selected-fg;
    font-weight: 600;
  }
}

.pick-sheet__hint {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;

  &--warn {
    color: $zs-error-fg;
  }
}

.pick-sheet__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.pick-sheet__btn {
  flex: 1;
  min-height: 2.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  cursor: pointer;

  &--primary {
    flex: 2;
    border-color: $primary;
    background: $primary;
    color: #fff;
  }

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}
</style>
