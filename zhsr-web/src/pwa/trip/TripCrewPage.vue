<template>
  <PwaPage
    :back="goBack"
    :title="crew ? `${crew.trip.trainNumber} • ${crew.trip.name}` : ''"
    :subtitle="crew ? times(crew.trip) : ''"
    :stale="stale"
  >
    <ErrorBox v-if="error" :error="error" :retry="crew ? null : loadCrew" class="crew__error q-py-sm q-px-md" />
    <LoadSpinner v-if="frameBusy.active.value" />

    <template v-if="crew && norm">
      <div class="crew__badges"><DictBadge :id="crew.trip.tripStatusType" dict="TripState" /></div>
      <div v-if="!stale && !editable" class="crew__muted">{{ readOnlyReason }}</div>

      <!-- готовность бригады -->
      <div class="crew__card">
        <div class="crew__caption crew__caption--card">{{ t('Готовность') }}</div>
        <div v-for="c in readiness" :key="c.title" class="crew__ready q-py-sm q-px-md">
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
            :key="m.usr.id"
            type="button"
            class="crew__chip q-py-none q-px-md"
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
        class="crew__wagon q-pa-md"
        :class="{ 'crew__wagon--bad': w.passenger && !w.ok }"
      >
        <div class="crew__wagon-head">
          <span class="crew__pos">{{ w.pos }}</span>
          <span class="crew__number zs-mono">{{ w.number }}</span>
          <span class="crew__muted">{{ dictManager.getValue('WagonType', w.type) }}</span>
          <span class="crew__grow" />
          <span v-if="w.tail" class="crew__tail q-py-none q-px-sm">{{ t('хвостовой') }}</span>
          <span class="crew__status" :class="{ 'crew__status--ok': w.ok }">{{ wagonStatus(w) }}</span>
        </div>
        <template v-if="w.passenger">
          <div v-for="slot in [0, 1]" :key="slot" class="crew__slot">
            <span class="crew__slot-text q-py-xs q-px-none" :class="{ 'crew__slot-text--bad': slotBad(w, slot) }">
              {{ w.conductors[slot] ? label(w.conductors[slot]!) : t('не назначен') }}
            </span>
            <SoftLink
              v-if="editable"
              text="Назначить"
              class="crew__assign"
              :disabled="busy"
              @click="slotPick = { wagon: w, slot }"
            />
          </div>
        </template>
      </div>

      <div class="crew__note">
        {{
          t(
            'Норма: 2 проводника на вагон или 3 на два соседних; в хвостовые вагоны — по 2 старших проводника («ст.»). Список проводников рейса назначает планировщик проводников; состав меняется только актами отцепки и прицепки.',
          )
        }}
      </div>
    </template>

    <template v-if="norm" #bar>
      <div class="crew__bar">
        <span class="crew__bar-text" :class="{ 'crew__bar-text--ok': normDone(norm) }">{{ normText }}</span>
        <button type="button" class="crew__done q-py-none q-px-lg" @click="goBack">{{ t('Готово') }}</button>
      </div>
    </template>
  </PwaPage>

  <!-- проводник на место в вагоне -->
  <PickSheet
    :show="!!slotPick"
    :title="t('Назначить проводника')"
    :subtitle="slotPick ? t('Вагон {n}', { n: slotPick.wagon.number }) : ''"
    :options="slotPickOptions"
    :selected="slotPick?.wagon.conductors[slotPick.slot]?.usr.id ?? null"
    :disabled="busy"
    @update:show="(v) => !v && (slotPick = null)"
    @pick="onSlot"
  />

  <!-- проводник из нераспределённых — на вагон -->
  <PickSheet
    :show="!!poolPick"
    :title="t('Назначить на вагон')"
    :subtitle="poolPick ? label(poolPick) : ''"
    :options="sheetWagons"
    :selected="null"
    :disabled="busy"
    @update:show="(v) => !v && (poolPick = null)"
    @pick="assignFromPool"
  />
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { CrewMember, TripCrew } from '@/api/trips'
import { fetchTripCrew, setConductorWagons } from '@/api/trips'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { CrewDuty, TripStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { loadCached } from '@/shared/offline/cache'
import type { WagonNorm } from '@/shared/trips/crewNorm'
import { crewNorm, normDone, slotOptions } from '@/shared/trips/crewNorm'
import { currentUser, loadCurrentUser } from '@/shared/user'
import type { PickOption } from '../components/PickSheet.vue'
import PickSheet from '../components/PickSheet.vue'
import PwaPage from '../components/PwaPage.vue'

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

const times = (trip: { departure: string; arrival: string }) =>
  t('отпр. {from} • приб. {to}', { from: formatDateTime(trip.departure), to: formatDateTime(trip.arrival) })
const error = ref<ShownError | null>(null)
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const frameBusy = provideBusy()
const busy = ref(false)
const poolPick = ref<CrewMember | null>(null)

const norm = computed(() => (crew.value ? crewNorm(crew.value) : null))
const conductorCount = computed(() => crew.value?.crew.filter((m) => m.crewDutyType === CrewDuty.CONDUCTOR).length ?? 0)

// правит начальник поезда своего рейса, пока рейс не закончен и есть связь
const isChief = computed(
  () => currentUser.value?.trip?.crewDutyType === CrewDuty.LNP && currentUser.value.trip.id === crew.value?.trip.id,
)
const tripOpen = computed(() =>
  ([TripStatus.PLANNED, TripStatus.IN_PROGRESS] as number[]).includes(crew.value?.trip.tripStatusType ?? 0),
)
const editable = computed(() => isChief.value && tripOpen.value && isOnline.value && !stale.value)
const readOnlyReason = computed(() => {
  if (!isChief.value) return t('Распределяет проводников начальник поезда рейса')
  if (!tripOpen.value) return t('Рейс закончен')
  return t('Для действия нужна связь')
})

const label = (m: CrewMember) => (m.senior ? `${m.usr.name} • ${t('ст.')}` : m.usr.name)

const readiness = computed(() => {
  const n = norm.value!
  const chief = crew.value!.crew.find((m) => m.crewDutyType === CrewDuty.LNP)
  const pem = crew.value!.crew.find((m) => m.crewDutyType === CrewDuty.PEM)
  return [
    {
      ok: n.chiefAndPem,
      title: t('Начальник поезда и ПЭМ'),
      detail: `${t('ЛНП')} ${chief?.usr.name ?? '—'} • ${t('ПЭМ')} ${pem?.usr.name ?? '—'}`,
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

async function save(changes: [CrewMember, string[]][]) {
  if (!crew.value || !editable.value) return
  busy.value = true
  error.value = null
  try {
    let result = crew.value
    for (const [m, wagons] of changes) result = await setConductorWagons(result.trip.id, m.usr.id, wagons)
    crew.value = result
  } catch (e) {
    error.value = shownError(e)
  } finally {
    busy.value = false
  }
}

// место в вагоне, на которое выбирается проводник; null — лист закрыт
const slotPick = shallowRef<{ wagon: WagonNorm; slot: number } | null>(null)

const slotPickOptions = computed<PickOption<number | null>[]>(() => {
  const pick = slotPick.value
  if (!pick) return []
  const choices = slotOptions(crew.value!, pick.wagon, pick.slot, label)
  return [{ id: null, name: t('Не назначен') }, ...choices.map((o) => ({ id: o.user, name: o.label }))]
})

// null — снять проводника с места
function onSlot(user: number | null) {
  const pick = slotPick.value
  if (!pick) return
  const wagon = pick.wagon.number
  const prev = pick.wagon.conductors[pick.slot]
  slotPick.value = null
  if ((prev?.usr.id ?? null) === user) return
  const next = crew.value!.crew.find((m) => m.usr.id === user)
  const changes: [CrewMember, string[]][] = []
  // прежний проводник места уходит с этого вагона
  if (prev) changes.push([prev, prev.wagons.filter((x) => x !== wagon)])
  // новый — на этот вагон; если у него уже есть соседний вагон, вагонов становится два
  if (next) changes.push([next, [...next.wagons, wagon]])
  void save(changes)
}

// лист «Назначить на вагон»: пассажирские вагоны, занятые (2 проводника) недоступны
const sheetWagons = computed<PickOption<string | null>[]>(() => {
  const pick = poolPick.value
  return (norm.value?.wagons ?? [])
    .filter((w) => w.passenger)
    .map((w) => {
      const n = w.conductors.length
      const type = dictManager.getValue('WagonType', w.type)
      return {
        id: w.number,
        name: `${w.pos}. ${w.number} • ${type}`,
        hint: t('{n} из 2', { n }) + (w.tail ? ` • ${t('хвостовой, нужен старший')}` : ''),
        warn: w.tail && !!pick && !pick.senior,
        disabled: n >= 2,
      }
    })
})

function assignFromPool(wagon: string | null) {
  const m = poolPick.value
  poolPick.value = null
  if (m && wagon) void save([[m, [wagon]]])
}

function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}

// загрузка — сразу в setup и в счёт ожидания страницы: спиннер виден с первой отрисовки
// загрузка бригады; она же — повтор по клику на ошибку загрузки
function loadCrew() {
  error.value = null
  return frameBusy.run(async () => {
    try {
      // справочник типов вагонов для состава — параллельно с пользователем
      const [u] = await Promise.all([loadCurrentUser(), dictManager.prepareDict('WagonType')])
      if (!u.trip) {
        error.value = { text: t('Вы сейчас не в бригаде рейса'), request: '' }
        return
      }
      const id = u.trip.id
      const result = await loadCached(`crew:${id}`, () => fetchTripCrew(id))
      crew.value = result.data
      stale.value = result.stale
    } catch (e) {
      error.value = shownError(e)
    }
  })
}
void loadCrew()
</script>

<style scoped lang="scss">
.crew__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.crew__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.crew__badges {
  display: flex;
  font-size: 0.8rem;
}

.crew__caption {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;

  // отступ под подписью группы — на треть меньше
  &--card {
    padding: 0.8rem 0.9rem calc(0.3rem * 2 / 3);
  }

  &--line {
    // отступ под подписью группы — на треть меньше промежутка между блоками (1rem)
    margin-bottom: calc(-1rem / 3);
    padding-top: 0.9rem;
    border-top: 0.0625rem solid $zs-border;
  }
}

.crew__card {
  display: flex;
  flex-direction: column;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
}

.crew__ready {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
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
  font-size: 0.8rem;
  font-weight: 700;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }
}

.crew__ready-text {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.crew__ready-title {
  font-weight: 600;
}

.crew__pool {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.crew__pool-head {
  // отступ под подписью группы — на треть меньше промежутка между блоками (0.6rem)
  margin-bottom: calc(-0.6rem / 3);
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
  font-size: 0.9rem;
  font-weight: 600;
}

.crew__wagon {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;

  &--bad {
    border-color: #e0b070;
  }
}

.crew__wagon-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.crew__pos {
  width: 1.1rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.crew__number {
  font-size: 1rem;
  font-weight: 600;
}

.crew__grow {
  flex: 1;
}

.crew__tail {
  border-radius: 0.3rem;
  background: $zs-selected-bg;
  font-size: 0.8rem;
  font-weight: 600;
}

.crew__status {
  color: #8a4b05;
  font-size: 0.8rem;
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

.crew__slot-text {
  flex: 1;

  &--bad {
    color: $zs-error-fg;
  }
}

.crew__assign {
  flex: none;
}

.crew__note {
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: 1.45;
}

.crew__bar {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.crew__bar-text {
  flex: 1;
  min-width: 0;
  color: #7a4f00;
  font-size: 0.9rem;
  font-weight: 600;

  &--ok {
    color: #1d5e2c;
  }
}

.crew__done {
  min-height: 3.3rem;
  border: 0;
  border-radius: 0.4rem;
  background: $primary;
  color: #fff;
  font: inherit;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
}
</style>
