<template>
  <CbList v-if="isMobile" v-model="model" single no-clear :values="items" class="depot-choice" />
  <CbSegment v-else v-model="model" single required :values="items" :max-button-count="6" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ChoiceModel } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { isMobile } from '@/shared/device'
import { dictManager } from '@/shared/dict/DictManager'
import { ALL_DEPOTS } from './depots'

/**
 * Выбор депо в диаграмме: «Все депо» (ALL_DEPOTS) и депо из данных диаграммы (depots — id справочника Depot, с
 * повторами и null). На телефоне — выпадающий список, на десктопе — кнопки. Справочник Depot загружает диаграмма.
 */
const props = defineProps<{ depots: (number | null)[] }>()
const depot = defineModel<number>({ required: true })

const model = computed<ChoiceModel>({
  get: () => depot.value,
  // депо выбрано всегда: пустое значение — все депо
  set: (v) => (depot.value = typeof v === 'number' ? v : ALL_DEPOTS),
})

const items = computed(() => {
  const ids = [...new Set(props.depots.filter((d): d is number => d !== null))]
  return [
    { id: ALL_DEPOTS, text: 'Все депо' },
    ...ids.map((id) => ({ id, text: dictManager.getValue('Depot', id) })).sort((a, b) => a.text.localeCompare(b.text)),
  ]
})
</script>

<style scoped lang="scss">
.depot-choice {
  width: 20rem;
  max-width: 100%;
}
</style>
