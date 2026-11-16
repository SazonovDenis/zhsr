<template>
  <button
    type="button"
    class="cb-cycle zs-focusable q-py-none q-px-md"
    :class="current ? 'cb-cycle--selected bg-selected text-selected' : 'cb-cycle--empty'"
    :disabled="disabled"
    @click="next"
  >
    {{ current ? displayOf(current) : first ? displayOf(first) : '' }}
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ChoiceId, ChoiceItem } from './choice'
import { displayOf, isDisabled } from './choice'

/**
 * CbCycle — циклическая кнопка выбора одного значения из немногих (два-три варианта): нажатие выбирает следующий
 * вариант, после последнего — «не выбрано», затем снова первый. Пока ничего не выбрано, на кнопке серым — первый
 * вариант. Недоступные варианты пропускаются.
 *
 *   <CbCycle v-model="answer" :values="items" />
 *
 * Выбранное — классы bg-selected и text-selected (app.scss) и cb-cycle--selected: вызывающий может перекрасить выбранное
 * по смыслу значения.
 */
const props = withDefaults(defineProps<{ values?: ChoiceItem[]; disabled?: boolean }>(), {
  values: () => [],
  disabled: false,
})

const model = defineModel<ChoiceId | null>({ default: null })

const enabled = computed(() => props.values.filter((v) => !isDisabled(v)))
const first = computed(() => enabled.value[0] ?? null)
const current = computed(() => props.values.find((v) => v.id === model.value) ?? null)

function next() {
  const list = enabled.value
  const index = list.findIndex((v) => v.id === model.value)
  // не выбрано — первый; последний — «не выбрано»
  model.value = index + 1 < list.length ? list[index + 1]!.id : null
}
</script>

<style scoped lang="scss">
.cb-cycle {
  min-height: 2.8rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 1.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  white-space: nowrap;
  cursor: pointer;

  // цвета выбранного — общие классы bg-selected и text-selected (app.scss)
  &--selected {
    border-color: $zs-selected-border;
  }

  // ничего не выбрано: первый вариант серым
  &--empty {
    color: $zs-text-faint;
  }

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}
</style>
