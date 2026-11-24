<template>
  <CbSegment v-model="mode" single required :values="modes" class="resp-board__fixed" />

  <!-- как первый шаг экрана «Ответственные за вагоны»: вагон — строкой выбора вагона (WagonOption), справа его
       ответственные; сотрудник — «Списком ФИО» (UserOption), справа его вагоны; касание — выбор в листе снизу -->
  <div class="resp-board__list" :class="{ 'resp-board__list--main': fill }">
    <LoadSpinner v-if="candidatesLoad.loading && candidates.length === 0" size="1.5rem" />
    <ErrorBox v-else-if="candidatesLoad.error" :error="candidatesLoad.error" :retry="candidatesLoad.run" />
    <template v-else>
      <button
        v-for="r in rows"
        :key="r.id"
        type="button"
        class="resp-board__row q-py-sm q-px-md"
        @click="openPick(r.id)"
      >
        <WagonOption
          v-if="mode === 'wagons'"
          :number="r.id"
          :type="dictManager.getValue('WagonType', wagonOf(r.id)?.wagonType ?? 0)"
          :place="r.sub"
        />
        <UserOption v-else :name="r.title" :me="isMe({ id: Number(r.id) })" :duty="r.duty" :place="r.sub" />
      </button>
      <div v-if="rows.length === 0" class="resp-board__muted q-py-lg q-px-md">{{ t(emptyText) }}</div>
    </template>
  </div>

  <!-- выбор сотрудников вагона: подсвечено может быть несколько -->
  <PickSheet
    v-model:show="userPickOpen"
    multiple
    :title="pickTitle"
    ok-text="Выбрать"
    :options="userOptions"
    :groups="userGroups"
    :selected="0"
    :selected-list="pickSelected"
    @pick-list="applyUsers"
  >
    <template #option="{ option }">
      <UserOption
        :name="option.name"
        :me="isMe({ id: option.id })"
        :duty="responsibleDuty(byId.get(option.id) ?? null)"
        :place="responsiblePlace(byId.get(option.id) ?? null)"
      />
    </template>
  </PickSheet>

  <!-- выбор вагонов сотрудника — тем же списком вагонов: подсвечено может быть несколько -->
  <BottomSheet v-model:show="wagonPickOpen" :title="pickTitle">
    <div class="resp-board__list">
      <button
        v-for="w in wagons"
        :key="w.number"
        type="button"
        class="resp-board__row q-py-sm q-px-md"
        :class="{ 'resp-board__row--on': wagonPicked.includes(w.number) }"
        @click="toggleWagon(w.number)"
      >
        <WagonOption :number="w.number" :type="dictManager.getValue('WagonType', w.wagonType)" />
      </button>
    </div>
    <template #buttons>
      <q-btn
        unelevated
        outline
        no-caps
        color="ink"
        class="resp-board__sheet-btn zs-pwa-btn zs-soft-border"
        :label="t('Отмена')"
        @click="wagonPickOpen = false"
      />
      <q-btn
        unelevated
        no-caps
        color="primary"
        class="resp-board__sheet-btn resp-board__sheet-btn--primary zs-pwa-btn"
        :label="t('Выбрать')"
        @click="confirmWagons"
      />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import UserOption from '@/shared/components/UserOption.vue'
import WagonOption from '@/shared/components/WagonOption.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { useLoad } from '@/shared/loadable'
import { isMe, userName } from '@/shared/user'
import type { ResponsibleCandidate } from '@/shared/wagons/responsible'
import { candidateDuty, fetchResponsibles, responsibleDuty, responsiblePlace } from '@/shared/wagons/responsible'
import BottomSheet from './BottomSheet.vue'
import type { PickGroup, PickOption } from './PickSheet.vue'
import PickSheet from './PickSheet.vue'

/**
 * Ответственные за вагоны на телефоне — точно видом первого шага экрана «Ответственные за вагоны» (ResponsiblesPage):
 * переключатель «Вагоны» / «Бригада» / «Сотрудники станции» (всегда, у станции без депо — пустой список) и строки — у
 * вагона его ответственные, у сотрудника его вагоны; касание строки — выбор нескольких в листе снизу. Кандидаты —
 * сотрудники депо станции station, бригада рейса trip и бригады рейсов поезда вагонов. v-model — ответственные по
 * номеру вагона; вагона нет или пусто — ответственный не назначен.
 *
 * @prop wagons  вагоны, за которые выбирают: id, номер и тип
 * @prop station станция, сотрудники депо которой могут быть ответственными
 * @prop trip    рейс: его бригада может быть ответственной
 * @prop fill    список занимает оставшуюся высоту страницы (PwaPage с fill) и прокручивается; иначе — по содержимому
 */
const props = withDefaults(
  defineProps<{
    wagons: { wagon: number; number: string; wagonType: number }[]
    station: number | null
    trip?: number | null
    fill?: boolean
  }>(),
  { trip: null, fill: true },
)

const model = defineModel<Record<string, number[]>>({ default: () => ({}) })

const candidates = shallowRef<ResponsibleCandidate[]>([])
const candidatesLoad = useLoad(async () => {
  candidates.value = await fetchResponsibles(
    props.station,
    props.trip,
    props.wagons.map((w) => w.wagon),
  )
})
watch(
  () => [props.station, props.trip, props.wagons.map((w) => w.wagon).join(',')],
  () => void candidatesLoad.run(),
  { immediate: true },
)

const byId = computed(() => new Map(candidates.value.map((c) => [c.id, c])))
const wagonOf = (number: string) => props.wagons.find((w) => w.number === number)
const usrsOf = (number: string) => model.value[number] ?? []
const nameOf = (id: number) => {
  const c = byId.value.get(id)
  return c ? userName(c) : String(id)
}

type Mode = 'wagons' | 'conductors' | 'station'
const mode = ref<Mode>('wagons')
const hasStation = computed(() => candidates.value.some((c) => c.depotStaff && c.crewDutyType === null))
// «Сотрудники станции» — всегда: у станции без депо список пуст, с подписью «Нет сотрудников станции»
const modes: ChoiceItem[] = [
  { id: 'wagons', text: 'Вагоны' },
  { id: 'conductors', text: 'Бригада' },
  { id: 'station', text: 'Сотрудники станции' },
]
const emptyText = computed(() => {
  if (mode.value === 'wagons') return 'Вагоны не выбраны'
  return mode.value === 'conductors' ? 'Бригада рейса не назначена' : 'Нет сотрудников станции'
})

/** Сотрудники режима: бригада рейса или сотрудники станции, которые не в бригаде. */
const persons = computed(() => {
  const staff = mode.value === 'station'
  return candidates.value.filter((c) => (staff ? c.depotStaff && c.crewDutyType === null : c.crewDutyType !== null))
})

const rows = computed<{ id: string; title: string; sub: string; duty: string }[]>(() => {
  if (mode.value === 'wagons') {
    return props.wagons.map((w) => {
      const usrs = usrsOf(w.number)
      return {
        id: w.number,
        title: w.number,
        sub: usrs.length > 0 ? usrs.map(nameOf).join(', ') : t('не назначен'),
        duty: '',
      }
    })
  }
  return persons.value.map((c) => {
    const mine = props.wagons.filter((w) => usrsOf(w.number).includes(c.id))
    return {
      id: String(c.id),
      title: userName(c),
      sub: mine.length > 0 ? mine.map((w) => w.number).join(', ') : '—',
      duty: candidateDuty(c),
    }
  })
})

// листы выбора: строка, для которой выбирают; у вагона — сотрудники, у сотрудника — вагоны
const userPickOpen = ref(false)
const wagonPickOpen = ref(false)
const pickRow = ref<string | null>(null)
const wagonPicked = ref<string[]>([])
const pickTitle = computed(() => {
  const row = pickRow.value
  if (row === null) return ''
  if (mode.value === 'wagons') return t('Ответственные за вагон {n}', { n: row })
  return t('Вагоны для {name}', { name: nameOf(Number(row)) })
})
const userOptions = computed<PickOption<number>[]>(() => candidates.value.map((c) => ({ id: c.id, name: userName(c) })))
const crewIds = computed(() => new Set(candidates.value.filter((c) => c.crewDutyType !== null).map((c) => c.id)))
const userGroups = computed<PickGroup<number>[]>(() =>
  hasStation.value
    ? [
        { id: 'conductors', text: 'Бригада', filter: (o) => crewIds.value.has(o.id) },
        { id: 'station', text: 'Сотрудники станции', filter: (o) => !crewIds.value.has(o.id) },
      ]
    : [],
)
const pickSelected = computed<number[]>(() => (pickRow.value === null ? [] : usrsOf(pickRow.value)))

function openPick(id: string) {
  pickRow.value = id
  if (mode.value === 'wagons') {
    userPickOpen.value = true
    return
  }
  const usr = Number(id)
  wagonPicked.value = props.wagons.filter((w) => usrsOf(w.number).includes(usr)).map((w) => w.number)
  wagonPickOpen.value = true
}

function toggleWagon(number: string) {
  wagonPicked.value = wagonPicked.value.includes(number)
    ? wagonPicked.value.filter((x) => x !== number)
    : [...wagonPicked.value, number]
}

/** Сотрудники вагона из листа. */
function applyUsers(ids: number[]) {
  const row = pickRow.value
  if (row === null) return
  model.value = { ...model.value, [row]: ids }
}

/** Вагоны сотрудника из листа: у отмеченных вагонов он ответственный, у остальных — нет. */
function confirmWagons() {
  wagonPickOpen.value = false
  const row = pickRow.value
  if (row === null) return
  const usr = Number(row)
  const next = { ...model.value }
  for (const w of props.wagons) {
    const usrs = usrsOf(w.number)
    const on = wagonPicked.value.includes(w.number)
    if (on && !usrs.includes(usr)) next[w.number] = [...usrs, usr]
    if (!on && usrs.includes(usr)) next[w.number] = usrs.filter((x) => x !== usr)
  }
  model.value = next
}
</script>

<style scoped lang="scss">
// переключатель не сжимается: прокручивается только список
.resp-board__fixed {
  flex: none;
}

// список и строки — как на экране «Ответственные за вагоны» (и в выборе вагона заявки)
.resp-board__list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

// список занимает оставшуюся высоту и прокручивается
.resp-board__list--main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.resp-board__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 3.3rem;
  border: 0;
  border-top: 0.0625rem solid $zs-border-row;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:first-child {
    border-top: 0;
  }

  // подсвеченная строка в листе выбора вагонов — фоном выбранной кнопки CbButton
  &--on {
    background: $zs-selected-bg;
  }
}

.resp-board__sheet-btn {
  flex: 1;

  &--primary {
    flex: 2;
  }
}

.resp-board__muted {
  color: $zs-text-muted;
  font-size: 0.9rem;
}
</style>
