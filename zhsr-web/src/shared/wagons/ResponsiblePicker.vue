<template>
  <CbList
    :model-value="model"
    single
    placeholder="Выберите ответственного…"
    :values="choices"
    :source="candidatesLoad"
    :disabled="disabled"
    @update:model-value="model = $event as ResponsibleChoice"
  >
    <!-- и в списке, и в поле — «Список ФИО»: ФИО слева, справа должность, под ней — где сотрудник -->
    <template #option="{ item }">
      <UserOption v-bind="rowOf(item)" />
    </template>
    <template #value="{ item }">
      <UserOption v-bind="rowOf(item)" compact />
    </template>
  </CbList>
</template>

<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'

import type { UserRef } from '@/api/requests'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { displayOf } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import UserOption from '@/shared/components/UserOption.vue'
import { useLoad } from '@/shared/loadable'
import { isMe, userName } from '@/shared/user'
import type { ResponsibleCandidate, ResponsibleChoice } from './responsible'
import { fetchResponsibles, NOT_ASSIGNED, responsibleDuty, responsiblePlace, responsibleWho } from './responsible'

/**
 * Выбор ответственного за вагон: сотрудники депо станции station, бригада рейса trip и бригады рейсов поезда вагонов
 * wagons, первым — «Ответственный не назначен». У сотрудника подписано, кто он: должность и рейс поезда или «депо
 * станции». v-model — ResponsibleChoice: null — ещё не выбран. Кандидаты грузятся при создании и при смене станции,
 * рейса или вагонов; ошибка загрузки — в самом списке. current — выбранный сотрудник из данных: его имя видно до
 * загрузки. Строка списка и поля — UserOption («Список ФИО»).
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
    return {
      id: c.id,
      text: role ? `${userName(c)} • ${role}` : userName(c),
      data: { name: c.name, me: isMe(c), duty: responsibleDuty(c), place: responsiblePlace(c) },
    }
  })
  if (current && !candidates.value.some((u) => u.id === current.id)) {
    items.unshift({ id: current.id, text: userName(current), data: { name: current.name, me: isMe(current) } })
  }
  return [{ id: NOT_ASSIGNED, text: 'Ответственный не назначен' }, ...items]
})

/** Строка «Списка ФИО» по варианту: сотрудник — ФИО, должность и место; «Ответственный не назначен» — только текст. */
function rowOf(item: ChoiceItem): { name: string; me: boolean; duty: string; place: string; plain: boolean } {
  if (item.id === NOT_ASSIGNED) return { name: displayOf(item), me: false, duty: '', place: '', plain: true }
  return {
    name: (item.data?.name as string | undefined) ?? displayOf(item),
    me: (item.data?.me as boolean | undefined) ?? false,
    duty: (item.data?.duty as string | undefined) ?? '',
    place: (item.data?.place as string | undefined) ?? '',
    plain: false,
  }
}
</script>
