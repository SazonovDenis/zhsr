<template>
  <PwaPage
    :back="goBack"
    :title="t('Формирование рейса')"
    :subtitle="crew ? `${crew.trip.train} • ${crew.trip.name}` : ''"
  >
    <div v-if="error" class="crew__error">{{ error }}</div>
    <div v-if="!crew && !error" class="crew__muted">{{ t('Загрузка…') }}</div>

    <template v-if="crew && norm">
      <div class="crew__state">
        <DictBadge :id="crew.trip.state" dict="TripState" />
        <span class="crew__muted">
          {{
            t('отпр. {from} • приб. {to}', {
              from: formatDateTime(crew.trip.departure),
              to: formatDateTime(crew.trip.arrival),
            })
          }}
        </span>
      </div>
      <div v-if="stale" class="crew__stale">{{ t('Нет связи — показаны сохранённые данные') }}</div>
      <div v-else-if="!editable" class="crew__muted">{{ readOnlyReason }}</div>

      <!-- готовность бригады -->
      <div class="crew__card">
        <div class="crew__caption crew__caption--card">{{ t('Готовность') }}</div>
        <div v-for="c in readiness" :key="c.title" class="crew__ready">
          <span class="crew__ready-icon" :class="{ 'crew__ready-icon--ok': c.ok }">{{ c.ok ? '✓' : '!' }}</span>
          <span class="crew__ready-text">
            <span class="crew__ready-title">{{ c.title }}</span>
            <span class="crew__muted">{{ c.detail }}</span>
          </span>
        </div>
      </div>

      <!-- проводники рейса без вагона -->
      <div class="crew__pool">
        <div class="crew__pool-head">
          <span class="crew__caption">{{ t('Нераспределённые проводники') }}</span>
          <span class="crew__muted">{{ t('{a} из {b}', { a: norm.pool.length, b: conductorCount }) }}</span>
        </div>
        <div v-if="norm.pool.length > 0" class="crew__chips">
          <button
            v-for="m in norm.pool"
            :key="m.user"
            type="button"
            class="crew__chip"
            :disabled="!editable || busy"
            @click="poolPick = m"
          >
            <span>{{ label(m) }}</span>
            <span v-if="editable" class="crew__chip-arrow">→ {{ t('вагон') }}</span>
          </button>
        </div>
        <div v-else class="crew__all">{{ t('Все проводники распределены') }}</div>
      </div>

      <div class="crew__caption crew__caption--line">{{ t('Состав') }}</div>
      <div
        v-for="w in norm.wagons"
        :key="w.number"
        class="crew__wagon"
        :class="{ 'crew__wagon--bad': w.passenger && !w.ok }"
      >
        <div class="crew__wagon-head">
          <span class="crew__pos">{{ w.pos }}</span>
          <span class="crew__number zs-mono">{{ w.number }}</span>
          <span class="crew__muted">{{ dictManager.getValue('WagonType', w.type) }}</span>
          <span class="crew__grow" />
          <span v-if="w.tail" class="crew__tail">{{ t('хвостовой') }}</span>
          <span class="crew__status" :class="{ 'crew__status--ok': w.ok }">{{ wagonStatus(w) }}</span>
        </div>
        <template v-if="w.passenger">
          <div v-for="slot in [0, 1]" :key="slot" class="crew__slot">
            <select
              v-if="editable"
              class="crew__select"
              :class="{ 'crew__select--empty': !w.conductors[slot], 'crew__select--bad': slotBad(w, slot) }"
              :value="w.conductors[slot]?.user ?? ''"
              :disabled="busy"
              @change="onSlot(w, slot, $event)"
            >
              <option value="" disabled>{{ t('Назначить проводника') }}</option>
              <option v-for="o in slotOptions(w, slot)" :key="o.user" :value="o.user">{{ o.label }}</option>
            </select>
            <span v-else class="crew__slot-text">{{ w.conductors[slot] ? label(w.conductors[slot]!) : '—' }}</span>
            <button
              v-if="editable && w.conductors[slot]"
              type="button"
              class="crew__clear"
              :title="t('Снять с вагона')"
              :disabled="busy"
              @click="unassign(w.conductors[slot]!, w.number)"
            >
              ✕
            </button>
          </div>
        </template>
      </div>

      <div class="crew__note">
        {{
          t(
            'Норма: 2 проводника на вагон или 3 на два соседних; в хвостовые вагоны — по 2 старших проводника («ст.»). Список проводников рейса назначает планировщик; состав меняется только актами отцепки и прицепки.',
          )
        }}
      </div>
    </template>

    <template v-if="norm" #bar>
      <div class="crew__bar">
        <span class="crew__bar-text" :class="{ 'crew__bar-text--ok': normDone(norm) }">{{ normText }}</span>
        <button type="button" class="crew__done" @click="goBack">{{ t('Готово') }}</button>
      </div>
    </template>
  </PwaPage>

  <!-- проводник из нераспределённых — на вагон -->
  <BottomSheet
    :show="!!poolPick"
    :title="t('Назначить на вагон')"
    :subtitle="poolPick ? label(poolPick) : ''"
    @update:show="(v) => !v && (poolPick = null)"
  >
    <div class="crew__sheet">
      <button
        v-for="w in sheetWagons"
        :key="w.number"
        type="button"
        class="crew__sheet-row"
        :disabled="w.full || busy"
        @click="assignFromPool(w.number)"
      >
        <span class="crew__pos">{{ w.pos }}</span>
        <span class="crew__grow crew__sheet-text">
          <span>
            <span class="crew__number zs-mono">{{ w.number }}</span>
            <span class="crew__muted">{{ dictManager.getValue('WagonType', w.type) }}</span>
          </span>
          <span class="crew__sheet-info" :class="{ 'crew__sheet-info--warn': w.warn }">{{ w.info }}</span>
        </span>
        <span class="crew__muted">{{ w.full ? t('занят') : '›' }}</span>
      </button>
    </div>
    <template #buttons>
      <button type="button" class="crew__cancel" @click="poolPick = null">{{ t('Отмена') }}</button>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { CrewMember, TripCrew } from '@/api/trips'
import { fetchTripCrew, setConductorWagons } from '@/api/trips'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { loadCached } from '@/shared/offline/cache'
import { currentUser, loadCurrentUser } from '@/shared/user'
import BottomSheet from '../components/BottomSheet.vue'
import PwaPage from '../components/PwaPage.vue'
import type { WagonNorm } from './crewNorm'
import { crewNorm, normDone } from './crewNorm'

/**
 * Формирование рейса на телефоне (Ф-РЕЙ-06): начальник поезда распределяет проводников своего рейса по вагонам — и у
 * рейса в статусе «план», и в пути. Сверху — готовность бригады (начальник поезда и ПЭМ, норма проводников, старшие
 * проводники в хвостовых вагонах), затем нераспределённые проводники и состав. У пассажирского вагона два места: в
 * место выбирается проводник из нераспределённых или проводник соседнего вагона («с вагона N»). Остальные видят
 * экран без правки; без связи правка недоступна.
 */

const router = useRouter()

const crew = shallowRef<TripCrew | null>(null)
const stale = ref(false)
const error = ref('')
const busy = ref(false)
const poolPick = ref<CrewMember | null>(null)

const norm = computed(() => (crew.value ? crewNorm(crew.value) : null))
const conductorCount = computed(() => crew.value?.crew.filter((m) => m.duty === 'CONDUCTOR').length ?? 0)

// правит начальник поезда своего рейса, пока рейс не закончен и есть связь
const isChief = computed(
  () => currentUser.value?.trip?.duty === 'LNP' && currentUser.value.trip.id === crew.value?.trip.id,
)
const tripOpen = computed(() => ['PLANNED', 'IN_PROGRESS'].includes(crew.value?.trip.state ?? ''))
const editable = computed(() => isChief.value && tripOpen.value && isOnline.value && !stale.value)
const readOnlyReason = computed(() => {
  if (!isChief.value) return t('Распределяет проводников начальник поезда рейса')
  if (!tripOpen.value) return t('Рейс закончен')
  return t('Для действия нужна связь')
})

const label = (m: CrewMember) => (m.senior ? `${m.name} • ${t('ст.')}` : m.name)

const readiness = computed(() => {
  const n = norm.value!
  const chief = crew.value!.crew.find((m) => m.duty === 'LNP')
  const pem = crew.value!.crew.find((m) => m.duty === 'PEM')
  return [
    {
      ok: n.chiefAndPem,
      title: t('Начальник поезда и ПЭМ'),
      detail: `${t('ЛНП')} ${chief?.name ?? '—'} • ${t('ПЭМ')} ${pem?.name ?? '—'}`,
    },
    {
      ok: n.full && n.notOk.length === 0,
      title: t('Проводники по норме'),
      detail:
        t('назначено {have} из {need}', { have: n.have, need: n.need }) +
        (n.notOk.length ? ` • ${t('не выполнена: {list}', { list: n.notOk.join(', ') })}` : ''),
    },
    {
      ok: n.tailBad.length === 0,
      title: t('Хвостовые вагоны'),
      detail: n.tailBad.length
        ? t('нужно по 2 старших проводника: {list}', { list: n.tailBad.join(', ') })
        : t('старшие проводники назначены'),
    },
  ]
})

const normText = computed(() => {
  const n = norm.value!
  return (
    t('Проводники: {have} из {need}', { have: n.have, need: n.need }) +
    (normDone(n) ? ` • ${t('норма выполнена')}` : '')
  )
})

function wagonStatus(w: WagonNorm): string {
  if (!w.passenger) return t('проводник не нужен')
  if (w.notAdjacent) return t('назначен на несоседние вагоны')
  if (w.needSeniors && w.conductors.length >= 2) return t('нужны старшие')
  return w.ok ? t('норма ✓') : t('норма не выполнена')
}

// место подсвечивается: проводник с несоседними вагонами или не старший в хвостовом вагоне
function slotBad(w: WagonNorm, slot: number): boolean {
  const m = w.conductors[slot]
  if (!m) return false
  return w.notAdjacent || (w.tail && !m.senior)
}

/** Кого можно поставить в место вагона: текущего, нераспределённых и проводников соседнего вагона с одним вагоном. */
function slotOptions(w: WagonNorm, slot: number): { user: string; label: string }[] {
  const order = crew.value!.wagons.map((x) => x.number)
  const i = order.indexOf(w.number)
  const neighbours = [order[i - 1], order[i + 1]].filter((x): x is string => !!x)
  const current = w.conductors[slot]
  const all = crew.value!.crew.filter((m) => m.duty === 'CONDUCTOR')
  return [
    ...(current
      ? [{ user: current.user, label: label(current) + (current.wagons.length === 2 ? ` • ${t('2 вагона')}` : '') }]
      : []),
    ...all.filter((m) => m.wagons.length === 0).map((m) => ({ user: m.user, label: label(m) })),
    ...all
      .filter((m) => m.wagons.length === 1 && neighbours.includes(m.wagons[0]!) && !m.wagons.includes(w.number))
      .map((m) => ({ user: m.user, label: `${label(m)} • ${t('с вагона {n}', { n: m.wagons[0]! })}` })),
  ]
}

async function save(changes: [CrewMember, string[]][]) {
  if (!crew.value || !editable.value) return
  busy.value = true
  error.value = ''
  try {
    let result = crew.value
    for (const [m, wagons] of changes) result = await setConductorWagons(result.trip.id, m.user, wagons)
    crew.value = result
  } catch (e) {
    error.value = e instanceof Error ? t(e.message) : String(e)
  } finally {
    busy.value = false
  }
}

function onSlot(w: WagonNorm, slot: number, e: Event) {
  const user = (e.target as HTMLSelectElement).value
  const prev = w.conductors[slot]
  if (prev?.user === user) return
  const next = crew.value!.crew.find((m) => m.user === user)
  const changes: [CrewMember, string[]][] = []
  // прежний проводник места уходит с этого вагона
  if (prev) changes.push([prev, prev.wagons.filter((x) => x !== w.number)])
  // новый — на этот вагон; если у него уже есть соседний вагон, вагонов становится два
  if (next) changes.push([next, [...next.wagons, w.number]])
  void save(changes)
}

function unassign(m: CrewMember, wagon: string) {
  void save([[m, m.wagons.filter((x) => x !== wagon)]])
}

// лист «Назначить на вагон»: пассажирские вагоны, занятые (2 проводника) недоступны
const sheetWagons = computed(() => {
  const pick = poolPick.value
  return (norm.value?.wagons ?? [])
    .filter((w) => w.passenger)
    .map((w) => {
      const n = w.conductors.length
      const warn = w.tail && !!pick && !pick.senior
      return {
        number: w.number,
        pos: w.pos,
        type: w.type,
        full: n >= 2,
        warn,
        info: t('{n} из 2', { n }) + (w.tail ? ` • ${t('хвостовой, нужен старший')}` : ''),
      }
    })
})

function assignFromPool(wagon: string) {
  const m = poolPick.value
  poolPick.value = null
  if (m) void save([[m, [wagon]]])
}

function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}

onMounted(async () => {
  try {
    await dictManager.prepareDicts('TripState', 'WagonType')
    const u = await loadCurrentUser()
    if (!u.trip) {
      error.value = t('Вы сейчас не в бригаде рейса')
      return
    }
    const id = u.trip.id
    const result = await loadCached(`crew:${id}`, () => fetchTripCrew(id))
    crew.value = result.data
    stale.value = result.stale
  } catch (e) {
    error.value = e instanceof Error ? t(e.message) : String(e)
  }
})
</script>

<style scoped lang="scss">
.crew__muted {
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.crew__stale {
  color: #7a5200;
  font-size: 0.8125rem;
  font-weight: 600;
}

.crew__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe5e1;
  color: #8f2416;
  font-size: 0.8125rem;
}

.crew__state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.crew__caption {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;

  &--card {
    padding: 0.75rem 0.875rem 0.25rem;
  }

  &--line {
    padding-top: 0.875rem;
    border-top: 0.0625rem solid $zs-border;
  }
}

.crew__card {
  display: flex;
  flex-direction: column;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;
}

.crew__ready {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  padding: 0.625rem 0.875rem;
  border-top: 0.0625rem solid $zs-border-row;
}

.crew__ready-icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 1.4rem;
  height: 1.4rem;
  border-radius: 50%;
  background: #fbe9cf;
  color: #8a4b05;
  font-size: 0.75rem;
  font-weight: 700;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }
}

.crew__ready-text {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
}

.crew__ready-title {
  font-weight: 600;
}

.crew__pool {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.crew__pool-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.crew__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.crew__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.8rem;
  padding: 0 0.875rem;
  border: 0.0625rem solid $primary;
  border-radius: 1.4rem;
  background: $zs-selected-bg;
  color: $zs-text;
  font: inherit;
  white-space: nowrap;
  cursor: pointer;

  &:disabled {
    border-color: $zs-border;
    cursor: default;
  }
}

.crew__chip-arrow {
  color: $primary;
  font-weight: 700;
}

.crew__all {
  color: #1d5e2c;
  font-size: 0.875rem;
  font-weight: 600;
}

.crew__wagon {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  padding: 0.75rem 0.875rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;

  &--bad {
    border-color: #e0b070;
  }
}

.crew__wagon-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.625rem;
}

.crew__pos {
  width: 1.1rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.crew__number {
  font-size: 1rem;
  font-weight: 600;
}

.crew__grow {
  flex: 1;
}

.crew__tail {
  padding: 0.125rem 0.5rem;
  border-radius: 0.25rem;
  background: $zs-selected-bg;
  font-size: 0.75rem;
  font-weight: 600;
}

.crew__status {
  color: #8a4b05;
  font-size: 0.78125rem;
  font-weight: 600;

  &--ok {
    color: #1d5e2c;
  }
}

.crew__slot {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.crew__select {
  flex: 1;
  height: 2.8rem;
  padding: 0 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9375rem;

  &--empty {
    border-color: #e0b070;
    color: $zs-text-muted;
  }

  &--bad {
    border-color: #c62828;
  }
}

.crew__slot-text {
  padding: 0.25rem 0;
}

.crew__clear {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 2.8rem;
  height: 2.8rem;
  padding: 0;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text-muted;
  cursor: pointer;
}

.crew__note {
  color: $zs-text-muted;
  font-size: 0.8125rem;
  line-height: 1.45;
}

.crew__bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.crew__bar-text {
  flex: 1;
  min-width: 0;
  color: #7a4f00;
  font-size: 0.875rem;
  font-weight: 600;

  &--ok {
    color: #1d5e2c;
  }
}

.crew__done {
  min-height: 3.3rem;
  padding: 0 1.5rem;
  border: 0;
  border-radius: 0.375rem;
  background: $primary;
  color: #fff;
  font: inherit;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
}

.crew__sheet {
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
}

.crew__sheet-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  min-height: 3.5rem;
  padding: 0.5rem 0.875rem;
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

  &:disabled {
    opacity: 0.4;
    cursor: default;
  }
}

.crew__sheet-text {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.crew__sheet-info {
  color: $zs-text-muted;
  font-size: 0.78125rem;

  &--warn {
    color: #8a4b05;
  }
}

.crew__cancel {
  flex: 1;
  min-height: 3.3rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
</style>
