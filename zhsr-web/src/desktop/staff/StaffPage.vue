<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Сотрудники') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!adminReason"
          :title="adminReason || undefined"
          @click="edit(null)"
        >
          {{ t('Новый пользователь') }}
        </q-btn>
      </div>
    </template>

    <FilterBar>
      <TextInput v-model="filter.search" placeholder="Поиск: ФИО, логин, телефон" width="16rem" />
      <CbList
        v-model="filter.station"
        single
        placeholder="Станция"
        :values="dictChoices('Station')"
        :source="dictSource('Station')"
        style="width: 11.9rem"
      />
      <CbList
        v-model="filter.trip"
        single
        placeholder="Рейс"
        :values="tripChoices"
        :source="tripLoad"
        style="width: 26.6rem"
      >
        <template #option="{ item }">
          <TripOptionView :trip="tripOptionOf(item)" class="trip-option--list" />
        </template>
      </CbList>
      <CbList
        v-model="filter.train"
        single
        placeholder="Поезд"
        :values="trainChoices"
        :source="trainLoad"
        style="width: 11.9rem"
      />
      <template #right>{{ t('{n} сотрудников', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="columns"
      :rows="rows"
      :error="loadError"
      :retry="init"
      row-key="id"
      :sort="sort"
      :struck="(row: StaffRow) => row.disabled"
      :loading="busy.active.value"
      :row-menu="rowMenu"
      empty="Нет сотрудников по выбранному фильтру"
      @row-dblclick="onDblclick($event as StaffRow)"
      @sort="sort = $event"
    />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'

import type { AdminUser } from '@/api/admin'
import { fetchAdminUser } from '@/api/admin'
import type { StaffFilter, StaffRow } from '@/api/staff'
import { fetchStaff } from '@/api/staff'
import type { TrainRow } from '@/api/trains'
import { fetchTrains } from '@/api/trains'
import type { TripOption } from '@/api/trips'
import { fetchTripOptions } from '@/api/trips'
import { t } from '@/boot/i18n'
import AdminUserDialog from '@/admin/users/AdminUserDialog.vue'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { RowKey, RowMenuItem, TableSort } from '@/shared/components/table/types'
import { RoleType, TripStatus } from '@/shared/constants/systemDicts'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { notifyError, shownError } from '@/shared/errors'
import { useLoad } from '@/shared/loadable'
import { roleReason } from '@/shared/roleReason'
import { loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { tripOptionItem, tripOptionOf } from '../trips/tripOptionItem'
import TripOptionView from '../trips/TripOptionView.vue'
import { staffColumns } from './staffColumns'
import StaffPeriodDialog from './StaffPeriodDialog.vue'

/**
 * Сотрудники: фильтры (поиск по ФИО, логину и телефону, станция текущего депо, рейс, поезд) и таблица — ФИО, роли,
 * категория, статус («Свободен» или «В рейсе»), где сотрудник сейчас (факт) и на какой ближайший рейс назначен (план).
 * Доступно планировщикам и администратору. Меню строки: «Переместить» (сотрудника не в рейсе — в другое депо),
 * «Сменить категорию»; администратору — «Редактировать» (карточка пользователя, она же по двойному клику) и «Новый
 * пользователь». Отключённых сотрудников видит только администратор, строки зачёркнуты.
 */

const PLANNER_REASON = 'Сотрудников перемещает планировщик'

// новый пользователь и карточка пользователя — администратору
const adminReason = computed(() => roleReason([], 'Пользователей заводит и правит администратор'))
const plannerReason = () => roleReason([RoleType.WAGON_PLANNER, RoleType.CREW_PLANNER], PLANNER_REASON)

const columns = staffColumns()

/** Меню строки: недоступное — с причиной. */
const rowMenu: RowMenuItem<StaffRow>[] = [
  {
    label: 'Переместить',
    disabled: (row: StaffRow) =>
      plannerReason() || (row.factTrip ? t('Сотрудник в рейсе: переместить его можно после прибытия') : ''),
    run: (row: StaffRow) => void changePeriod(row, 'depot'),
  },
  {
    label: 'Сменить категорию',
    disabled: () => plannerReason(),
    run: (row: StaffRow) => void changePeriod(row, 'category'),
  },
  {
    label: 'Редактировать',
    separator: true,
    disabled: () => adminReason.value,
    run: (row: StaffRow) => void edit(row),
  },
  { label: 'Новый пользователь', disabled: () => adminReason.value, run: () => void edit(null) },
]

const filter = reactive<StaffFilter>({ search: '', station: null, trip: null, train: null })
const rows = shallowRef<StaffRow[]>([])
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок (справочники, список)
const busy = provideBusy()
const selectedId = ref<RowKey | null>(null)
const sort = ref<TableSort | null>(null)
// ошибка загрузки показывается на месте строк таблицы
const loadError = ref<ShownError | null>(null)

async function load() {
  loadError.value = null
  try {
    rows.value = await busy.run(fetchStaff(filter, sort.value))
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

/** Сотрудник после изменения — на место прежней строки. */
function replaceRow(row: StaffRow) {
  rows.value = rows.value.map((r) => (r.id === row.id ? row : r))
}

async function changePeriod(row: StaffRow, kind: 'depot' | 'category') {
  const saved = await openDialog<StaffRow>(StaffPeriodDialog, { row, kind })
  if (saved) replaceRow(saved)
}

// двойной клик — карточка пользователя (администратору)
function onDblclick(row: StaffRow) {
  if (!adminReason.value) void edit(row)
}

/** Карточка пользователя: row = null — новый пользователь. Сохранили — список загружается заново. */
async function edit(row: StaffRow | null) {
  let user: AdminUser | null = null
  if (row) {
    try {
      user = await fetchAdminUser(row.id)
    } catch (e) {
      notifyError(e)
      return
    }
  }
  const saved = await openDialog<AdminUser>(AdminUserDialog, { user })
  if (!saved) return
  await load()
  selectedId.value = saved.id
}

// фильтр «Рейс»: рейсы в статусе «план» и «выполняется», у каждого — его маршрут
const trips = shallowRef<TripOption[]>([])
const tripChoices = computed(() => trips.value.map((trip) => tripOptionItem(trip, true)))
const tripLoad = useLoad(async () => {
  const [, list] = await Promise.all([
    dictManager.prepareDict('Station'),
    fetchTripOptions({
      route: null,
      station: null,
      from: null,
      to: null,
      states: [TripStatus.PLANNED, TripStatus.IN_PROGRESS],
    }),
  ])
  trips.value = list
})

// фильтр «Поезд»: действующие поезда
const trains = shallowRef<TrainRow[]>([])
const trainChoices = computed(() => trains.value.map((tr) => ({ id: tr.id, text: tr.name })))
const trainLoad = useLoad(async () => {
  trains.value = await fetchTrains({ states: ['active'], search: '' })
})

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки; она же — повтор по клику на ошибку в таблице
async function init() {
  await Promise.all([loadDicts(), load()])
}

async function loadDicts() {
  try {
    await busy.run(Promise.all([dictManager.prepareDicts('Station', 'Depot'), loadCurrentUser()]))
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
// варианты фильтров — свои загрузки, список их не ждёт; ошибка — в выпадающем списке
void tripLoad.run()
void trainLoad.run()
</script>
