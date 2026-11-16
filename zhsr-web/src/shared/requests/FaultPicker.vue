<template>
  <CbList
    :model-value="modelValue"
    single
    :placeholder="placeholder"
    :values="choices"
    :source="load"
    @update:model-value="onPick"
  >
    <template #option="{ item }">
      <FaultOption :item="item" />
    </template>
  </CbList>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import { isMobile } from '@/shared/device'
import { dictManager } from '@/shared/dict/DictManager'
import { useLoad } from '@/shared/loadable'
import FaultOption from './FaultOption.vue'
import { faultChoices } from './faults'

/**
 * Поле выбора неисправности на десктопе — штатный CbList: выпадающий список — дерево «служба → группа →
 * неисправность» (faultChoices: службы и группы — заголовки, выбрать их нельзя). Набор текста фильтрует список, курсор
 * ходит по неисправностям стрелками, Enter выбирает, Esc закрывает список. v-model — id справочника FaultType; null —
 * не выбрана. Справочники групп и неисправностей поле загружает само: своя загрузка, спиннер и ошибка — в списке.
 */
withDefaults(defineProps<{ modelValue: number | null; placeholder?: string }>(), {
  placeholder: 'Поиск словами: «окно дует»',
})
const emit = defineEmits<{ 'update:modelValue': [fault: number | null] }>()

const choices = computed(() => faultChoices())

function onPick(value: ChoiceModel) {
  emit('update:modelValue', typeof value === 'number' ? value : null)
  // на телефоне после выбора фокус уходит из поля: экранная клавиатура закрывается
  if (isMobile.value && document.activeElement instanceof HTMLElement) document.activeElement.blur()
}
const load = useLoad(() => dictManager.prepareDicts('FaultGroup', 'FaultType'))
void load.run()
</script>
