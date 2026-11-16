<template>
  <CbList
    :model-value="model"
    single
    placeholder="Выберите ответственного…"
    :values="choices"
    :source="candidatesLoad"
    :disabled="disabled"
    @update:model-value="model = $event as ResponsibleChoice"
  />
</template>

<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'

import type { UserRef } from '@/api/requests'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import { useLoad } from '@/shared/loadable'
import { userName } from '@/shared/user'
import type { ResponsibleCandidate, ResponsibleChoice } from './responsible'
import { fetchResponsibles, NOT_ASSIGNED, responsibleWho } from './responsible'

/**
 * Выбор ответственного за вагон: сотрудники депо станции station, бригада рейса trip и бригады рейсов поезда вагонов
 * wagons, первым — «Ответственный не назначен». У сотрудника подписано, кто он: должность и рейс поезда или «депо
 * станции». v-model — ResponsibleChoice: null — ещё не выбран. Кандидаты грузятся при создании и при смене станции,
 * рейса или вагонов; ошибка загрузки — в самом списке. current — выбранный сотрудник из данных: его имя видно до
 * загрузки.
 */
const props = withDefaults(
  defineProps<{
    station: number | null
    trip?: number | null
    wagons?: string[]
    current?: UserRef | null
    disabled?: boolean
  }>(),
  { trip: null, wagons: () => [], current: null, disabled: false },
)

const model = defineModel<ResponsibleChoice>({ default: null })

const candidates = shallowRef<ResponsibleCandidate[]>([])

const candidatesLoad = useLoad(async () => {
  candidates.value = await fetchResponsibles(props.station, props.trip, props.wagons)
})

watch(
  () => [props.station, props.trip, props.wagons.join(',')],
  () => void candidatesLoad.run(),
  { immediate: true },
)

const choices = computed<ChoiceItem[]>(() => {
  const current = props.current
  const items: ChoiceItem[] = candidates.value.map((c) => {
    const role = responsibleWho(c)
    return { id: c.id, text: role ? `${userName(c)} • ${role}` : userName(c) }
  })
  if (current && !candidates.value.some((u) => u.id === current.id)) {
    items.unshift({ id: current.id, text: userName(current) })
  }
  return [{ id: NOT_ASSIGNED, text: 'Ответственный не назначен' }, ...items]
})
</script>
