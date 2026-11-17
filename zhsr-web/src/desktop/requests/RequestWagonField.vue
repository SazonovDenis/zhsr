<template>
  <div class="wagon-pick">
    <CbSegment :model-value="mode" single required :values="modes" @update:model-value="setMode" />
    <CbList
      ref="listEl"
      class="wagon-pick__list"
      :model-value="model"
      single
      :values="choices"
      :source="source"
      :placeholder="placeholder"
      :disabled="mode === 'my' && myNumbers.length === 1"
      @update:model-value="choose"
      @search="onSearch"
    >
      <template #option="{ item }">
        <div v-if="item.data?.hint" class="zs-sub">{{ item.text }}</div>
        <!-- строка вагона: слева номер и под ним тип, справа расположение и под ним плашка -->
        <WagonOption
          v-else
          :number="(item.data?.number as string | undefined) ?? ''"
          :type="(item.data?.type as string | undefined) ?? ''"
          :place="(item.data?.place as string | undefined) ?? ''"
          :badge="(item.data?.badge as string | undefined) ?? ''"
        />
      </template>
    </CbList>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef, watch } from 'vue'

import type { WagonRef } from '@/api/requests'
import type { RequestWagon } from '@/api/wagons'
import { fetchTripWagons, findWagons, WAGON_SEARCH_LIMIT } from '@/api/wagons'
import WagonOption from '@/shared/components/WagonOption.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import { CrewDuty } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import type { Loadable } from '@/shared/loadable'
import { tripRefText } from '@/shared/trips/tripRefText'
import { t } from '@/boot/i18n'
import { wagonBadge } from '@/shared/requests/requestView'
import { currentUser } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'

/**
 * Вагон на десктопе — в диалоге заявки, в чек-листе и в описи: три режима ввода и в каждом — выпадающий список вагонов.
 *
 * - «Мой вагон» — вагоны проводника в текущем рейсе: вагон один — выбирается сам, список недоступен; вагонов несколько —
 *   выбирает пользователь в списке.
 * - «Мой рейс» — вагоны состава текущего рейса пользователя.
 * - «Другой вагон» — поиск по цифрам номера среди всех вагонов: вагоны ищет сервер по тексту, введённому в список;
 *   пока цифры не введены, список пуст.
 *
 * Смена режима очищает выбранный вагон и введённые цифры. Режим без рейса или без своих вагонов недоступен. По умолчанию — первый доступный режим; у исправляемой заявки —
 * «Другой вагон» с вагоном заявки. v-model — id вагона; событие pick — выбранный вагон целиком (рейс вагона нужен форме).
 * initial — вагон исправляемой заявки: он есть в списке, пока не выбран другой.
 */
const props = withDefaults(defineProps<{ initial?: WagonRef | null }>(), { initial: null })
const model = defineModel<number | null>({ default: null })
const emit = defineEmits<{ pick: [wagon: RequestWagon] }>()

type Mode = 'my' | 'trip' | 'other'

const trip = computed(() => currentUser.value?.trip ?? null)
/** номера вагонов проводника в текущем рейсе */
const myNumbers = computed(() => (trip.value?.crewDutyType === CrewDuty.CONDUCTOR ? trip.value.wagons : []))

const modes = computed<ChoiceItem[]>(() => [
  { id: 'my', text: 'Мой вагон', disabled: myNumbers.value.length === 0 },
  { id: 'trip', text: 'Мой рейс', disabled: !trip.value },
  { id: 'other', text: 'Другой вагон' },
])

function defaultMode(): Mode {
  if (props.initial) return 'other'
  if (myNumbers.value.length > 0) return 'my'
  return trip.value ? 'trip' : 'other'
}

const mode = ref<ChoiceModel>(defaultMode())
const listEl = ref<InstanceType<typeof CbList> | null>(null)
// режим выбрал пользователь: данные пользователя, пришедшие позже, режим уже не меняют
let modeTouched = false

function setMode(value: ChoiceModel) {
  if (value === mode.value) return
  modeTouched = true
  mode.value = value
  // смена режима очищает выбранный вагон, введённые цифры и найденные вагоны; «Мой вагон» выбирается сам после загрузки
  choose(null)
  clearTimeout(timer)
  query = ''
  found.value = []
  // фокус — сразу в список вагонов: в «Моём вагоне» список недоступен, фокусировать нечего
  void nextTick(() => listEl.value?.focus())
}

// пользователь загружается вместе с формой: режим по умолчанию — по его рейсу
watch(trip, () => {
  if (!modeTouched) mode.value = defaultMode()
})

/** вагоны состава текущего рейса — для «Мой вагон» и «Мой рейс» */
const tripWagons = shallowRef<RequestWagon[]>([])
/** вагоны, найденные по номеру, — для «Другой вагон» */
const found = shallowRef<RequestWagon[]>([])
/** выбранный вагон: остаётся в списке при смене поиска */
const picked = shallowRef<RequestWagon | null>(null)

const wagons = computed<RequestWagon[]>(() => {
  if (mode.value === 'my') return tripWagons.value.filter((w) => myNumbers.value.includes(w.number))
  if (mode.value === 'trip') return tripWagons.value
  return found.value
})

function choiceOf(w: RequestWagon): ChoiceItem {
  const type = dictManager.getValue('WagonType', w.wagonType)
  const place = placeText(w.locationType, w.place, w.trip ? tripRefText(w.trip) : null)
  const badge = wagonBadge(currentUser.value, w, mode.value === 'other')
  return { id: w.id, text: `${w.number} • ${type}`, data: { number: w.number, type, place, badge } }
}

const choices = computed<ChoiceItem[]>(() => {
  const list = wagons.value.map(choiceOf)
  const p = picked.value
  if (p && p.id === model.value && !list.some((c) => c.id === p.id)) list.unshift(choiceOf(p))
  const init = props.initial
  if (init && init.id === model.value && !p && !list.some((c) => c.id === init.id)) {
    const type = dictManager.getValue('WagonType', init.wagonType)
    list.unshift({ id: init.id, text: `${init.number} • ${type}`, data: { number: init.number, type, place: '' } })
  }
  // сервер отдал ровно предел поиска — вагонов, видимо, больше: последней строкой просьба уточнить номер; строку не
  // выбрать, а поиск по тексту списка её не скрывает (search — введённый текст)
  if (mode.value === 'other' && found.value.length === WAGON_SEARCH_LIMIT) {
    list.push({
      id: -1,
      text: t('Показаны первые {n} — уточните номер', { n: WAGON_SEARCH_LIMIT }),
      disabled: true,
      search: searchText.value,
      data: { hint: true },
    })
  }
  return list
})

const placeholder = computed(() => (mode.value === 'other' ? 'Введите цифры номера вагона' : 'Выберите вагон'))

function choose(value: ChoiceModel) {
  if (value === null) {
    picked.value = null
    model.value = null
    return
  }
  const w = [...wagons.value, ...(picked.value ? [picked.value] : [])].find((x) => x.id === value)
  if (!w) return
  picked.value = w
  model.value = w.id
  emit('pick', w)
}

// --- загрузка: свой источник вариантов списка; спиннер и ошибка — в самом списке, форма загрузку не ждёт

const state = reactive({ loading: false, error: null as ShownError | null })
let seq = 0
/** текст поиска «Другой вагон»: только цифры */
let query = ''
/** текст, введённый в список, как есть */
const searchText = ref('')

async function load() {
  const my = ++seq
  state.loading = true
  state.error = null
  try {
    // справочники строк списка (тип вагона, место) — параллельно с вагонами
    const [, list] = await Promise.all([
      dictManager.prepareDicts('WagonType', 'Station', 'Depot', 'Yard'),
      loadWagons(),
    ])
    if (my !== seq) return
    if (mode.value === 'other') found.value = list
    else tripWagons.value = list
    autoPick()
  } catch (e) {
    if (my === seq) state.error = shownError(e)
  } finally {
    if (my === seq) state.loading = false
  }
}

function loadWagons(): Promise<RequestWagon[]> {
  // «Другой вагон»: пока цифры не введены, список пуст
  if (mode.value === 'other') return query ? findWagons(query) : Promise.resolve([])
  const id = trip.value?.id
  if (!id) return Promise.resolve([])
  return tripWagons.value.length > 0 ? Promise.resolve(tripWagons.value) : fetchTripWagons(id)
}

// «Мой вагон»: вагон проводника один — выбирается сам; вагонов несколько — выбирает пользователь
function autoPick() {
  if (mode.value !== 'my' || model.value !== null || wagons.value.length !== 1) return
  choose(wagons.value[0]!.id)
}

const source: Loadable = {
  get loading() {
    return state.loading
  },
  get error() {
    return state.error
  },
  retry() {
    if (state.error) void load()
  },
  // загрузку запускает поле само: при создании и при смене режима
  ensure() {},
}

// поиск по номеру — после паузы в вводе
const SEARCH_DELAY = 300
let timer: ReturnType<typeof setTimeout> | undefined

function onSearch(text: string) {
  searchText.value = text
  if (mode.value !== 'other') return
  const digits = text.replace(/\D/g, '')
  if (digits === query) return
  query = digits
  clearTimeout(timer)
  timer = setTimeout(() => void load(), SEARCH_DELAY)
}

watch(mode, () => void load())
void load()
</script>

<style scoped lang="scss">
.wagon-pick {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.wagon-pick__list {
  flex: 1 1 auto;
  min-width: 0;
}

// режимы — с узкими боковыми отступами: комбо вагона в той же строке
.wagon-pick :deep(.cb-segment__item) {
  padding: 0 0.6rem;
}
</style>
