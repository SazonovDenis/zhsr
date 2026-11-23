<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Описи') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!newReason"
          :title="newReason || undefined"
          @click="router.push({ name: 'equip-new' })"
        >
          {{ t('Новая опись') }}
        </q-btn>
      </div>
    </template>

    <FilterBar>
      <TextInput v-model="filter.query" placeholder="Номер вагона" width="12rem" />
      <span v-if="wagonFilter" class="list-wagon q-py-xs q-pl-sm q-pr-xs">
        {{ t('Вагон № {number}', { number: wagonFilter.number }) }}
        <button
          type="button"
          class="list-wagon__clear q-pa-none"
          :title="t('Снять фильтр')"
          @click="wagonFilter = null"
        >
          ✕
        </button>
      </span>
      <CbList
        v-model="templateChoice"
        single
        placeholder="Шаблон описи"
        :values="templateChoices"
        :source="templateLoad"
        style="width: 16rem"
      />
      <PeriodPicker v-model:start-date="filter.from" v-model:end-date="filter.to" label="Период:" clearable />
      <template #right>{{ t('{n} описей', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="columns"
      :rows="rows"
      row-key="id"
      :loading="busy.active.value"
      :error="loadError"
      :retry="load"
      :sort="sort"
      empty="Описей по выбранному фильтру нет"
      @sort="sort = $event"
    />

    <template #right>
      <EquipListPanel :equip-list-id="selectedId === null ? null : Number(selectedId)" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { EquipListFilter, EquipListRow, EquipTemplate } from '@/api/equip'
import { fetchEquipLists, fetchEquipTemplates } from '@/api/equip'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { equipReason } from '@/shared/checks/rights'
import { textOf } from '@/shared/checks/texts'
import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { TableSort } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import { wagonRequestsPeriod } from '@/shared/requests/requestView'
import { loadCurrentUser, userName } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import EquipListPanel from './EquipListPanel.vue'
import { useKeptSelection } from '../frame/keptSelection'

/**
 * Список описей экипировки (Ф-ЭКП-03): поиск по номеру вагона, шаблон, период (по умолчанию — месяц). Видимость — как у
 * проверок. Справа — карточка описи. «Новая опись» — составление описи (Ф-ЭКП-02).
 */

const router = useRouter()
const route = useRoute()
const busy = provideBusy()
const filter = reactive<EquipListFilter>({ template: null, wagon: null, query: '', ...wagonRequestsPeriod() })
const rows = shallowRef<EquipListRow[]>([])
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection(route.query.id ? Number(route.query.id) : null)
const panel = useSidePanel(selectedId)
// сортировка по колонке; null — сначала новые
const sort = ref<TableSort | null>(null)
const loadError = ref<ShownError | null>(null)
const newReason = computed(() => equipReason())

const columns = defineColumns<EquipListRow>([
  {
    field: 'createdDt',
    title: 'Начата',
    // по ширине даты «17.11 17:32» (цифры по 1ch, точка, двоеточие и пробел уже) и отступов слева 1rem и справа (dense);
    // дата не переносится
    width: 'calc(9.5ch + 1.3rem)',
    dense: true,
    cellClass: 'text-no-wrap',
    sortable: true,
    format: (v) => formatDateTime(v as string),
  },
  // вид описи — без сортировки
  {
    field: 'equipListTemplate',
    title: 'Шаблон описи',
    width: 'minmax(14rem, 2fr)',
    format: (_, row) => textOf(row.equipListTemplate),
  },
  {
    field: 'wagon',
    title: 'Вагон',
    width: '8rem',
    cellClass: 'zs-mono',
    sortable: true,
    format: (_, row) => row.wagon.number,
  },
  {
    field: 'createdByUsr',
    title: 'Составил',
    width: 'minmax(10rem, 1fr)',
    sortable: true,
    format: (_, row) => userName(row.createdByUsr),
  },
  // числа — по ширине заголовка; ширина постоянная: у каждой строки таблицы своя сетка, ширина по содержимому
  // разъехалась бы по строкам. Заявка по недостаче у описи одна: 1 или 0
  // нарушений нет — «нет»; заявки нет — пусто
  {
    field: 'violationCount',
    title: 'Нарушений',
    width: '6.5rem',
    align: 'right',
    format: (_, row) => (row.violationCount ? String(row.violationCount) : t('нет')),
  },
  {
    field: 'repairRequest',
    title: 'Заявок',
    width: '5rem',
    align: 'right',
    format: (_, row) => (row.repairRequest ? '1' : ''),
  },
])

const templates = shallowRef<EquipTemplate[]>([])
const templateLoad = useLoad(async () => {
  templates.value = await fetchEquipTemplates()
})
void templateLoad.run()
const templateChoices = computed<ChoiceItem[]>(() => templates.value.map((x) => ({ id: x.id, text: textOf(x) })))
const templateChoice = computed({
  get: () => filter.template,
  set: (v: ChoiceModel) => (filter.template = typeof v === 'number' ? v : null),
})

async function load() {
  loadError.value = null
  try {
    const [list] = await busy.run(Promise.all([fetchEquipLists(filter, sort.value), loadCurrentUser()]))
    rows.value = list
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}
/** Фильтр по вагону — переход из панели вагона (?wagon=id&number=номер); снимается крестиком. */
const wagonFilter = ref<{ id: number; number: string } | null>(null)
function applyRouteQuery() {
  const id = Number(route.query.wagon)
  const number = route.query.number
  if (!id || typeof number !== 'string') return
  wagonFilter.value = { id, number }
  void router.replace({ name: 'equip' })
}
applyRouteQuery()

/** Переход из диаграммы: номер вагона в поиске и период (?query=номер&from=дата&to=дата). */
function applySearchQuery() {
  const { query, from, to } = route.query
  if (typeof query !== 'string' || typeof from !== 'string' || typeof to !== 'string') return
  Object.assign(filter, { query, from, to })
  void router.replace({ name: 'equip' })
}
applySearchQuery()
watch(() => route.query.query, applySearchQuery)
watch(() => route.query.wagon, applyRouteQuery)
watch(wagonFilter, (w) => (filter.wagon = w?.id ?? null), { immediate: true })

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })
void load()
</script>

<style scoped lang="scss">
.list-wagon {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0.0625rem solid $primary;
  border-radius: 1rem;
  background: $zs-selected-bg;
  font-size: 0.8rem;
}

.list-wagon__clear {
  border: 0;
  background: none;
  color: $zs-text-muted;
  font: inherit;
  cursor: pointer;
}
</style>
