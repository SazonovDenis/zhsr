<template>
  <div class="cb-button">
    <span v-if="label" class="cb-button__label q-mr-xs">{{ t(label) }}</span>

    <!-- варианты грузятся или не загрузились — на месте кнопок -->
    <ChoiceSourceState v-if="source && (source.loading || source.error)" :source="source" />

    <template v-else>
      <button
        v-for="(item, index) in visibleValues"
        :key="item.id"
        type="button"
        :data-index="index"
        :tabindex="item.id === tabStopId ? 0 : -1"
        class="cb-button__btn zs-focusable q-py-none q-px-sm"
        :class="{ 'cb-button__btn--selected bg-selected text-selected': isSelected(item) }"
        :disabled="isDisabled(item)"
        :title="displayOf(item)"
        @click="onButtonClick(item)"
        @focus="onItemFocus(item)"
        @keydown="onItemKeydown($event, item)"
        @keyup.space.prevent
      >
        <span class="cb-button__text">{{ displayOf(item) }}</span>
        <span v-if="item.count !== undefined" class="cb-button__count">{{ item.count }}</span>
      </button>

      <CbMoreMenu
        v-if="hiddenValues.length > 0"
        class="cb-button__more"
        tabindex="-1"
        :values="values"
        :single="single"
        :is-selected="isSelected"
        @keydown="onItemKeydown($event)"
        @pick="onMenuPick"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import type { ChoiceItem } from './choice'
import { displayOf, isDisabled } from './choice'
import CbMoreMenu from './CbMoreMenu.vue'
import ChoiceSourceState from './ChoiceSourceState.vue'
import type { ButtonChoiceEmit, ButtonChoiceProps } from './useButtonChoice'
import { useButtonChoice } from './useButtonChoice'

/**
 * CbButton — ряд кнопок-фильтров («чипов») с поиском по остальным значениям через «...».
 *
 *   <CbButton v-model="states" label="Состояние" :values="items" />
 *   <CbButton v-model="state" :values="items" single :max-button-count="5" />
 *
 * Правила values, v-model и change — см. choice.ts. У значения можно задать count: число показывается рядом с
 * подписью, например сколько вагонов в этом состоянии. Клавиатура: ряд — одна остановка Tab, по кнопкам ходят
 * стрелками (см. onItemKeydown в useButtonChoice).
 */
const props = withDefaults(defineProps<ButtonChoiceProps & { label?: string }>(), {
  single: false,
  required: false,
  values: () => [] as ChoiceItem[],
  hidden: () => [],
  maxButtonCount: 3,
  modelValue: null,
  source: null,
  label: '',
})

const emit = defineEmits<ButtonChoiceEmit>()

const { visibleValues, hiddenValues, isSelected, onButtonClick, onMenuPick, tabStopId, onItemFocus, onItemKeydown } =
  useButtonChoice(props, emit)
</script>

<style scoped lang="scss">
.cb-button {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0.3rem;
}

.cb-button__label {
  color: $zs-text-muted;
  font-size: 0.8rem;
  white-space: nowrap;
}

.cb-button__btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  height: 1.9rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.9rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8rem;
  white-space: nowrap;
  cursor: pointer;

  &:hover:not(:disabled):not(.cb-button__btn--selected) {
    background: $zs-bg-hover;
  }

  // цвета выбранного — общие классы bg-selected и text-selected (app.scss)
  &--selected {
    border-color: $zs-selected-border;
  }

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}

.cb-button__text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.cb-button__count {
  opacity: 0.6;
  font-variant-numeric: tabular-nums;
}

.cb-button__more {
  width: 1.9rem;
  height: 1.9rem;
  border-radius: 0.9rem;
}
</style>
