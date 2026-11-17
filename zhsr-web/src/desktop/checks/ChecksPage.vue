<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Чек-листы') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!newReason"
          :title="newReason || undefined"
          @click="router.push({ name: 'check-new' })"
        >
          {{ t('Новая проверка') }}
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
        placeholder="Чек-лист"
        :values="templateChoices"
        :source="templateLoad"
        style="width: 16rem"
      />
      <PeriodPicker v-model:start-date="filter.from" v-model:end-date="filter.to" label="Период:" clearable />
      <template #right>{{ t('{n} проверок', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="checksColumns"
      :rows="rows"
      row-key="id"
      :loading="busy.active.value"
      :error="loadError"
      :retry="load"
      :sort="sort"
      empty="Проверок по выбранному фильтру нет"
      @sort="sort = $event"
    />

    <template #right>
      <CheckListPanel :check-list-id="selectedId === null ? null : Number(selectedId)" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { CheckListFilter, CheckListRow, CheckTemplate } from '@/api/checks'
import { fetchCheckLists, fetchCheckTemplates } from '@/api/checks'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { anyCheckReason } from '@/shared/checks/rights'
import { textOf } from '@/shared/checks/texts'
import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { RowKey, TableSort } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { useLoad } from '@/shared/loadable'
import { wagonRequestsPeriod } from '@/shared/requests/requestView'
import { loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import CheckListPanel from './CheckListPanel.vue'
import { checksColumns } from './checksColumns'

/**
 * Список проверок по чек-листу (Ф-ЧЕК-04): поиск по номеру вагона, чек-лист, период начала (по умолчанию — месяц).
 * Пользователь видит свои проверки; администратор, руководитель, инструктор, мастер депо и приёмщик — все. Справа —
 * карточка проверки. «Новая проверка» — проведение проверки (Ф-ЧЕК-03).
 */

const router = useRouter()
const route = useRoute()
const busy = provideBusy()
const filter = reactive<CheckListFilter>({ template: null, wagon: null, query: '', ...wagonRequestsPeriod() })
const rows = shallowRef<CheckListRow[]>([])
const selectedId = ref<RowKey | null>(route.query.id ? Number(route.query.id) : null)
const panel = useSidePanel(selectedId)
// сортировка по колонке; null — сначала новые
const sort = ref<TableSort | null>(null)
const loadError = ref<ShownError | null>(null)
const newReason = computed(() => anyCheckReason())

// шаблоны — варианты фильтра «Чек-лист»: своя загрузка, список её не ждёт
const templates = shallowRef<CheckTemplate[]>([])
const templateLoad = useLoad(async () => {
  templates.value = await fetchCheckTemplates()
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
    const [list] = await busy.run(Promise.all([fetchCheckLists(filter, sort.value), loadCurrentUser()]))
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
  void router.replace({ name: 'checks' })
}
applyRouteQuery()
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
