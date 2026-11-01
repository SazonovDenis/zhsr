<template>
  <div class="cb-segment">
    <span v-if="label" class="cb-segment__label">{{ t(label) }}</span>

    <button
      v-for="(item, index) in visibleValues"
      :key="item.id"
      type="button"
      :data-index="index"
      :tabindex="item.id === tabStopId ? 0 : -1"
      class="cb-segment__item zs-focusable"
      :class="{ 'cb-segment__item--active bg-selected text-selected': isSelected(item) }"
      :disabled="isDisabled(item)"
      :title="displayOf(item)"
      @click="onButtonClick(item)"
      @focus="onItemFocus(item)"
      @keydown="onItemKeydown($event, item)"
      @keyup.space.prevent
    >
      {{ displayOf(item) }}
    </button>

    <CbMoreMenu
      v-if="hiddenValues.length > 0"
      class="cb-segment__more"
      tabindex="-1"
      :values="values"
      :single="single"
      :is-selected="isSelected"
      @keydown="onItemKeydown($event)"
      @pick="onMenuPick"
    />
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import type { ChoiceItem } from './choice'
import { displayOf, isDisabled } from './choice'
import CbMoreMenu from './CbMoreMenu.vue'
import type { ButtonChoiceEmit, ButtonChoiceProps } from './useButtonChoice'
import { useButtonChoice } from './useButtonChoice'

/**
 * CbSegment — сегментированный переключатель: ряд подписей в общей рамке-пилюле, выбранная подсвечена заливкой.
 * Подпись label стоит внутри рамки перед сегментами («Период:»). Значения, не влезшие в ряд, доступны через «...».
 *
 *   <CbSegment v-model="period" label="Период:" :values="items" single required />
 *
 * Правила values, v-model и change — см. choice.ts. Клавиатура: ряд — одна остановка Tab, по кнопкам ходят стрелками
 * (см. onItemKeydown в useButtonChoice).
 */
const props = withDefaults(defineProps<ButtonChoiceProps & { label?: string }>(), {
  single: false,
  required: false,
  values: () => [] as ChoiceItem[],
  hidden: () => [],
  maxButtonCount: 3,
  modelValue: null,
  label: '',
})

const emit = defineEmits<ButtonChoiceEmit>()

const { visibleValues, hiddenValues, isSelected, onButtonClick, onMenuPick, tabStopId, onItemFocus, onItemKeydown } =
  useButtonChoice(props, emit)
</script>

<style scoped lang="scss">
// Рамка-пилюля как у DatePicker и PeriodPicker: сегменты — во всю внутреннюю высоту рамки, со скруглением рамки,
// поэтому выбранный сегмент вписывается в рамку ровно, без зазоров. Внутренний радиус — радиус рамки минус её толщина
.cb-segment {
  --cb-segment-border: 0.0625rem;
  --cb-segment-radius: calc(var(--zs-control-height) / 2);

  display: inline-flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 0.125rem;
  height: var(--zs-control-height);
  border: var(--cb-segment-border) solid $zs-border-input;
  border-radius: var(--cb-segment-radius);
  background: #fff;
}

.cb-segment__label {
  display: flex;
  align-items: center;
  margin: 0 0.375rem 0 0.75rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
  white-space: nowrap;
}

.cb-segment__item {
  flex: none;
  padding: 0 0.75rem;
  border: 0;
  border-radius: calc(var(--cb-segment-radius) - var(--cb-segment-border));
  background: transparent;
  color: $zs-text;
  font: inherit;
  font-size: 0.8125rem;
  white-space: nowrap;
  cursor: pointer;

  &:hover:not(:disabled):not(.cb-segment__item--active) {
    background: $zs-bg-button-soft;
  }

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}

.cb-segment__more {
  padding: 0 0.5rem;
  border-radius: calc(var(--cb-segment-radius) - var(--cb-segment-border));
}
</style>
