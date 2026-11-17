<template>
  <PwaPage>
    <!-- список не по вагонам (все или только свои) — с периодом, по умолчанию последний месяц -->
    <PeriodPicker
      v-if="byPeriod"
      v-model:start-date="from"
      v-model:end-date="to"
      class="checks__period"
      :periods="PERIODS"
    />

    <!-- что видно: подпись группы, как «Открытые» в заявках, и число строк -->
    <div class="zs-group-head">
      <span>{{ t(scopeTitle) }}</span>
      <span class="checks__count q-py-none q-px-sm">{{ rows.length }}</span>
    </div>

    <LoadSpinner v-if="busy.active.value && rows.length === 0" />
    <ErrorBox v-else-if="error" :error="error" :retry="load" class="checks__error q-py-sm q-px-md" />
    <div v-else-if="rows.length === 0 && !busy.active.value" class="checks__empty">
      {{ t('Чек-листов и описей нет') }}
    </div>

    <div v-if="rows.length > 0" class="checks__list">
      <router-link v-for="r in rows" :key="r.key" :to="r.to" class="checks__row q-px-md">
        <!-- название и справа — вид: чек-лист или опись -->
        <div class="checks__title-row">
          <span class="checks__title">{{ r.title }}</span>
          <span class="checks__kind">{{ t(r.kind) }}</span>
        </div>
        <div class="checks__meta">
          <!-- дата • вагон (жирным, как в списке заявок) • ФИО -->
          {{ formatDateTime(r.at) }} •
          <template v-if="r.checkedUsr"> <UserName :user="r.checkedUsr" /> • </template>
          <template v-else-if="r.subject">
            <span :class="{ checks__wagon: r.isWagon }">{{ r.subject }}</span> •
          </template>
          <UserName :user="r.author" />
        </div>
        <div v-if="r.violations > 0" class="checks__violations">
          {{ t('Нарушений: {n}', { n: r.violations }) }}
        </div>
      </router-link>
    </div>

    <!-- новый чек-лист или опись — одна кнопка, как новая заявка: вид и шаблон выбираются в мастере -->
    <template v-if="canAdd" #fab>
      <router-link :to="{ name: 'pwa-check-start' }" class="checks__fab" :title="t('Чек-лист или опись')">
        <q-icon name="add" size="1.8rem" />
      </router-link>
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, shallowRef, watch } from 'vue'

import UserName from '@/shared/components/UserName.vue'
import type { CheckListRow } from '@/api/checks'
import { fetchCheckLists } from '@/api/checks'
import type { EquipListRow } from '@/api/equip'
import type { UserRef } from '@/api/requests'
import { fetchEquipLists } from '@/api/equip'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { anyCheckReason, equipReason } from '@/shared/checks/rights'
import { textOf } from '@/shared/checks/texts'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { wagonShortText } from '@/shared/requests/requestView'
import { tripRefText } from '@/shared/trips/tripRefText'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import { CrewDuty, RoleType } from '@/shared/constants/systemDicts'
import { currentUser, hasRole, loadCurrentUser, userName } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'

/**
 * Вкладка «Описи» на телефоне: кнопка «+» (мастер CheckStartPage: чек-лист Ф-ЧЕК-03 или опись Ф-ЭКП-02) — тем, кто их
 * проводит; над списком — подпись, что именно видно; список не по вагонам — с периодом (по умолчанию месяц). Список
 * проведённых чек-листов и описей, новые первыми (Ф-ЧЕК-04, Ф-ЭКП-03; видимость — как на
 * сервере). Нажатие на строку открывает просмотр.
 */

/**
 * Что видно пользователю — как на сервере (CheckRights): все — администратор, руководитель, инструктор, мастер депо,
 * приёмщик; проводник — по своим вагонам рейса; ЛНП и ПЭМ — по вагонам своего рейса; остальные — проведённые сами.
 * И всем — проведённые ими самими.
 */
type Scope = 'all' | 'wagons' | 'trip' | 'own'

const SEE_ALL: number[] = [RoleType.ADMIN, RoleType.MANAGER, RoleType.INSTRUCTOR, RoleType.MASTER, RoleType.RECEIVER]

const scope = computed<Scope>(() => {
  const u = currentUser.value
  if (SEE_ALL.some((r) => hasRole(u, r))) return 'all'
  const duty = u?.trip?.crewDutyType
  if (duty === CrewDuty.CONDUCTOR) return 'wagons'
  if (duty === CrewDuty.LNP || duty === CrewDuty.PEM) return 'trip'
  return 'own'
})

const SCOPE_TITLE: Record<Scope, string> = {
  all: 'Все за период',
  wagons: 'Ваших вагонов и проведённые вами',
  trip: 'Вагонов вашего рейса и проведённые вами',
  own: 'Проведённые вами за период',
}
const scopeTitle = computed(() => SCOPE_TITLE[scope.value])

/** Список не ограничен вагонами: нужен период. */
const byPeriod = computed(() => scope.value === 'all' || scope.value === 'own')

// период — встроенные периоды выбора периода: сегодня, 3 дня, неделя, месяц; по умолчанию месяц — по сегодня
const PERIODS = ['d1', 'd3', 'd7', 'm1']
const today = new Date()
const from = ref<string | null>(date.formatDate(date.addToDate(today, { months: -1 }), 'YYYY-MM-DD'))
const to = ref<string | null>(date.formatDate(today, 'YYYY-MM-DD'))

/** Строка списка: проверка или опись. */
interface Row {
  key: string
  /** «Чек-лист» или «Опись» — на русском, переводится при показе */
  kind: string
  /** просмотр */
  to: { name: string; params: { id: string } }
  title: string
  /** что проверено: вагон, сотрудник или рейс */
  subject: string
  /** объект — вагон: выделяется */
  isWagon: boolean
  /** когда окончена, ISO */
  at: string
  /** проверенный сотрудник — у чек-листа сотрудника; иначе null, объект — subject */
  checkedUsr: UserRef | null
  author: UserRef
  violations: number
}

const busy = provideBusy()
const checks = shallowRef<CheckListRow[]>([])
const equips = shallowRef<EquipListRow[]>([])
const error = ref<ShownError | null>(null)

// кнопка «+» — тем, кто проводит чек-листы или описи
const canAdd = computed(() => anyCheckReason() === '' || equipReason() === '')

function checkSubject(r: CheckListRow): string {
  if (r.wagon) return wagonShortText(r.wagon)
  if (r.checkedUsr) return userName(r.checkedUsr)
  return r.trip ? tripRefText(r.trip) : ''
}

const rows = computed<Row[]>(() =>
  [
    ...checks.value.map((r) => ({
      key: `check-${r.id}`,
      kind: 'Чек-лист',
      to: { name: 'pwa-check-list', params: { id: String(r.id) } },
      title: textOf(r.checkListTemplate),
      subject: checkSubject(r),
      isWagon: !!r.wagon,
      checkedUsr: r.wagon ? null : r.checkedUsr,
      at: r.completedDt,
      author: r.createdByUsr,
      violations: r.violationCount,
    })),
    ...equips.value.map((r) => ({
      key: `equip-${r.id}`,
      kind: 'Опись',
      to: { name: 'pwa-equip-list', params: { id: String(r.id) } },
      title: textOf(r.equipListTemplate),
      subject: wagonShortText(r.wagon),
      isWagon: true,
      checkedUsr: null,
      at: r.completedDt,
      author: r.createdByUsr,
      violations: r.violationCount,
    })),
  ].sort((a, b) => Date.parse(b.at) - Date.parse(a.at)),
)

// проверки, описи и справочник типов вагонов — параллельно, в счёт ожидания страницы
async function load() {
  error.value = null
  try {
    // пользователь — раньше списка: от его роли зависит, нужен ли период
    await busy.run(Promise.all([loadCurrentUser(), dictManager.prepareDict('WagonType')]))
    const filter = {
      template: null,
      wagon: null,
      query: '',
      from: byPeriod.value ? from.value : null,
      to: byPeriod.value ? to.value : null,
    }
    const [c, e] = await busy.run(Promise.all([fetchCheckLists(filter), fetchEquipLists(filter)]))
    checks.value = c
    equips.value = e
  } catch (e) {
    checks.value = []
    equips.value = []
    error.value = shownError(e)
  }
}

void load()

// сменили период — список заново
watch([from, to], () => void load())
</script>

<style scoped lang="scss">
.checks__fab {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  background: $primary;
  box-shadow: 0 0.3rem 0.8rem rgba(0, 0, 0, 0.3);
  color: #fff;
  text-decoration: none;
}

// выбор периода — по ширине кнопок: колонка страницы растягивала его рамку на всю ширину, справа оставалось пусто
.checks__period {
  align-self: flex-start;
}

.checks__count {
  min-width: 1.4rem;
  height: 1.4rem;
  border-radius: 0.7rem;
  background: $zs-selected-bg;
  color: $zs-text;
  font-size: 0.8rem;
  line-height: 1.4rem;
  text-align: center;
}

.checks__list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.checks__row {
  // по вертикали — чуть плотнее q-pa-md, как строка заявки
  padding-top: 0.7rem;
  padding-bottom: 0.7rem;
  display: flex;
  color: $zs-text;
  text-decoration: none;
  flex-direction: column;
  gap: 0.2rem;
  border-top: 0.0625rem solid $zs-border-row;

  &:first-child {
    border-top: 0;
  }
}

// как неисправность в строке заявки (RequestRow)
.checks__title {
  flex: 1;
  min-width: 0;
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.3;
}

.checks__meta {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
}

// как вагон в строке заявки (RequestRow): только жирным
.checks__wagon {
  font-weight: 700;
}

.checks__title-row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.checks__kind {
  flex: none;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.checks__violations {
  color: $zs-error-fg;
  font-size: 0.8rem;
  font-weight: 600;
}

.checks__empty {
  color: $zs-text-muted;
  text-align: center;
}

.checks__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
