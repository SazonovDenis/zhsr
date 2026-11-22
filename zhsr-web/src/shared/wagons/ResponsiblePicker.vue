<template>
  <CbList
    :model-value="listModel"
    placeholder="Выберите ответственных…"
    :values="choices"
    :source="candidatesLoad"
    :disabled="disabled"
    @update:model-value="onPick($event as ChoiceId[])"
  >
    <!-- в списке — «Список ФИО»: ФИО слева, справа должность, под ней — где сотрудник -->
    <template #option="{ item }">
      <UserOption v-bind="rowOf(item)" />
    </template>
  </CbList>
</template>

<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'

import type { UserRef } from '@/api/requests'
import type { ChoiceId, ChoiceItem } from '@/shared/components/choice/choice'
import { displayOf } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import UserOption from '@/shared/components/UserOption.vue'
import { useLoad } from '@/shared/loadable'
import { isMe, userName } from '@/shared/user'
import type { ResponsibleCandidate, ResponsibleChoice } from './responsible'
import { fetchResponsibles, NOT_ASSIGNED, responsibleDuty, responsiblePlace, responsibleWho } from './responsible'

/**
 * Выбор ответственных за вагон галочками: сотрудники депо станции station, бригада рейса trip и бригады рейсов поезда
 * вагонов wagons, первым — «Ответственный не назначен». У сотрудника подписано, кто он: должность и рейс поезда или
 * «депо станции». v-model — ResponsibleChoice: null — ещё не выбраны. «Не назначен» снимает отмеченных сотрудников,
 * отметка сотрудника снимает «Не назначен». Кандидаты грузятся при создании и при смене станции, рейса или вагонов;
 * ошибка загрузки — в самом списке. current — назначенные сейчас: их имена видны до загрузки. Строка списка —
 * UserOption («Список ФИО»).
 */
const props = withDefaults(
  defineProps<{
    station: number | null
    trip?: number | null
    wagons?: string[]
    current?: UserRef[]
    disabled?: boolean
  }>(),
  { trip: null, wagons: () => [], current: () => [], disabled: false },
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

/** Модель списка: «не назначен» — один вариант NOT_ASSIGNED, иначе id сотрудников. */
const listModel = computed<ChoiceId[]>(() => {
  const m = model.value
  if (m === NOT_ASSIGNED) return [NOT_ASSIGNED]
  return m ?? []
})

/** Отметка в списке: «не назначен» и сотрудники друг друга исключают; ничего не отмечено — не выбрано. */
function onPick(ids: ChoiceId[]) {
  const before = listModel.value
  if (ids.includes(NOT_ASSIGNED) && !before.includes(NOT_ASSIGNED)) {
    model.value = NOT_ASSIGNED
    return
  }
  const usrs = ids.filter((id): id is number => typeof id === 'number')
  model.value = usrs.length > 0 ? usrs : null
}

const choices = computed<ChoiceItem[]>(() => {
  const items: ChoiceItem[] = candidates.value.map((c) => {
    const role = responsibleWho(c)
    return {
      id: c.id,
      text: role ? `${userName(c)} • ${role}` : userName(c),
      data: { name: c.name, me: isMe(c), duty: responsibleDuty(c), place: responsiblePlace(c) },
    }
  })
  for (const u of [...props.current].reverse()) {
    if (!candidates.value.some((c) => c.id === u.id)) {
      items.unshift({ id: u.id, text: userName(u), data: { name: u.name, me: isMe(u) } })
    }
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
