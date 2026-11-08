<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Журнал вагона') }}</h1>
    </template>

    <FilterBar>
      <PeriodPicker
        v-model:start-date="period.from"
        v-model:end-date="period.to"
        label="Период:"
        clearable
        :presets="PRESETS"
      />
      <CbButton v-model="locationKinds" label="Расположение" :values="locationChoices" :max-button-count="4" />
      <CbButton v-model="states" label="Состояние" :values="stateChoices" :max-button-count="6" />
      <template #right>{{ t('{n} записей', shown.length) }}</template>
    </FilterBar>

    <DataTable
      :columns="journalColumns"
      :rows="shown"
      :error="loadError"
      :retry="load"
      row-key="key"
      :loading="busy.active.value"
      empty="Записей нет"
    />

    <template #right>
      <WagonPanel :wagon="wagon" journal />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { WagonRow } from '@/api/wagons'
import { fetchWagonJournal, fetchWagons } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbButton from '@/shared/components/choice/CbButton.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import type { PeriodPreset } from '@/shared/components/period/period'
import { presetDates } from '@/shared/components/period/period'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { dayStart } from '@/shared/format'
import DesktopFrame from '../frame/DesktopFrame.vue'
import type { JournalRow } from './journalColumns'
import { journalColumns } from './journalColumns'
import WagonPanel from './WagonPanel.vue'

/**
 * Журнал вагона (Ф-ВАГ-05): события расположения и состояния за период с основаниями, новые сверху. Фильтры
 * «Расположение» (где вагон после события) и «Состояние» — по загруженным за период строкам, с числом строк. Справа —
 * карточка вагона.
 */
const props = defineProps<{ number: string }>()

const router = useRouter()
const busy = provideBusy()
const floating = ref(false)

const CURRENT_MONTH: PeriodPreset = { value: 'curMonth', label: 'Текущий месяц', from: 0, to: 0, months: true }
const PREV_MONTH: PeriodPreset = { value: 'prevMonth', label: 'Прошлый месяц', from: -1, to: -1, months: true }
const PRESETS = [CURRENT_MONTH, PREV_MONTH]

const period = reactive<{ from: string | null; to: string | null }>(presetDates(CURRENT_MONTH))
const locationKinds = ref<number[]>([])
const states = ref<number[]>([])

const wagon = shallowRef<WagonRow | null>(null)
const rows = shallowRef<JournalRow[]>([])
const loadError = ref<ShownError | null>(null)

const shown = computed(() =>
  rows.value.filter(
    (r) =>
      (!locationKinds.value.length || (r.location && locationKinds.value.includes(r.location.locationType))) &&
      (!states.value.length || states.value.includes(r.wagonStatusType)),
  ),
)

const locationChoices = computed(() =>
  dictChoices('LocationKind').map((c) => ({
    ...c,
    count: rows.value.filter((r) => r.location?.locationType === Number(c.id)).length,
  })),
)
const stateChoices = computed(() =>
  dictChoices('WagonState').map((c) => ({
    ...c,
    count: rows.value.filter((r) => r.wagonStatusType === Number(c.id)).length,
  })),
)

const back = () => void router.push({ name: 'wagons' })

/** Вагон по номеру: поиск по номеру ищет среди всех вагонов, нужен точный. */
async function loadWagon() {
  const list = await fetchWagons(
    { number: props.number, station: null, trip: null, locationKinds: [], states: [] },
    null,
  )
  const found = list.find((w) => w.number === props.number)
  if (!found) throw new Error(t('Вагон № {number} не найден', { number: props.number }))
  wagon.value = found
}

async function load() {
  loadError.value = null
  try {
    await busy.run(async () => {
      await Promise.all([
        wagon.value ? null : loadWagon(),
        dictManager.prepareDicts(
          'Station',
          'Depot',
          'Yard',
          'WagonType',
          'WagonModel',
          'LocationKind',
          'WagonState',
          'TripState',
        ),
      ])
      const list = await fetchWagonJournal(
        wagon.value!.id,
        period.from ? dayStart(period.from) : null,
        period.to ? dayStart(period.to, 1) : null,
      )
      rows.value = list.map((r, i) => ({ ...r, key: i }))
    })
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

watch(
  () => props.number,
  () => {
    wagon.value = null
    void load()
  },
)
watch(period, load, { deep: true })
void load()
</script>
