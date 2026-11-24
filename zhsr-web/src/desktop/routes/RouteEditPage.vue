<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title route-edit__h1">{{ title }}</h1>
    </template>

    <template v-if="route">
      <div v-if="previewPoints.length > 0" class="route-edit__chain">
        <!-- маршрут на карте — по остановкам из редактора, перерисовывается при их правке. В левом углу, маленький;
             станции следования развёрнуты — карта крупнее и показывает их всегда, свёрнуты — только при увеличении.
             Щелчок по станции выбирает её -->
        <RouteMap
          :points="previewPoints"
          :selected="selectedStation"
          :show-passing="!chainCollapsed"
          expandable
          class="route-edit__map"
          :class="{ 'route-edit__map--wide': !chainCollapsed }"
          @station="selectedStation = $event.station"
          @expand="chainCollapsed = !chainCollapsed"
        />
        <div class="route-edit__stops">
          <RouteStops v-model:collapsed="chainCollapsed" :points="previewPoints" layout="horizontal" />
          <ErrorBox v-if="legsLoad.error" :error="legsLoad.error" :retry="legsLoad.retry" class="q-mt-sm" />
        </div>
      </div>

      <div class="route-edit__columns">
        <div class="route-edit__column route-edit__column--stops">
          <div class="route-edit__section-title">{{ t('Остановки по порядку') }}</div>
          <div class="route-edit__card route-edit__stops-card q-pa-md">
            <RouteStopsEditor v-model="stops" :readonly="readonly" />
          </div>
        </div>

        <div class="route-edit__column route-edit__column--attrs">
          <div class="route-edit__section-title">{{ t('Атрибуты') }}</div>
          <!-- правка недоступна (readonly) — вместо полей их значения текстом -->
          <div class="route-edit__card route-edit__form q-pa-md">
            <FormSection columns="7rem 16rem">
              <FormField label="Номер поезда">
                <span v-if="readonly" class="route-edit__value route-edit__value--mono">{{ form.train }}</span>
                <TextInput v-else v-model="form.train" mono />
              </FormField>
              <FormField label="Сообщение">
                <span v-if="readonly" class="route-edit__value">{{ form.name }}</span>
                <TextInput v-else v-model="form.name" placeholder="Например, Астана — Караганда" />
              </FormField>
            </FormSection>

            <!-- дата ввода и вид маршрута — в одну строку; поля своей ширины, а не во всю форму -->
            <FormSection columns="repeat(2, max-content)">
              <FormField label="Дата ввода маршрута">
                <span v-if="readonly" class="route-edit__value">{{ dateText(form.since) }}</span>
                <DatePicker
                  v-else
                  v-model="form.since"
                  date-text="Дата ввода маршрута"
                  :quick="['today', 'todayPlus1', 'todayPlus2', 'nextMonthStart']"
                />
              </FormField>
              <FormField label="Вид маршрута">
                <span v-if="readonly" class="route-edit__value">{{
                  dictManager.getValue('RouteKind', form.kind)
                }}</span>
                <CbSegment v-else v-model="form.kind" single required :values="dictChoices('RouteKind')" />
              </FormField>
            </FormSection>

            <!-- поле даты — своей ширины, а не во всю форму -->
            <FormSection columns="max-content">
              <FormField label="Дата завершения">
                <span v-if="readonly" class="route-edit__value">{{ dateText(form.until) }}</span>
                <DatePicker
                  v-else
                  v-model="form.until"
                  date-text="Дата завершения"
                  :quick="['monthEnd', 'yearEnd', 'today']"
                />
              </FormField>
            </FormSection>

            <FormSection title="Нормы часов" divided columns="repeat(3, 9rem)">
              <FormField label="Проводник, час:мин">
                <span v-if="readonly" class="route-edit__value">{{ normText(form.hc) }}</span>
                <HoursInput v-else v-model="form.hc" width="4rem" />
              </FormField>
              <FormField label="ЛНП, час:мин">
                <span v-if="readonly" class="route-edit__value">{{ normText(form.hl) }}</span>
                <HoursInput v-else v-model="form.hl" width="4rem" />
              </FormField>
              <FormField label="ПЭМ, час:мин">
                <span v-if="readonly" class="route-edit__value">{{ normText(form.hp) }}</span>
                <HoursInput v-else v-model="form.hp" width="4rem" />
              </FormField>
            </FormSection>

            <div v-if="!readonly" class="route-edit__note">
              {{ t('Можно вводить доли часа, напр. «2,5» будет означать «2 часа 30 мин».') }}
            </div>

            <ErrorBox v-if="error" :error="error" class="route-edit__error q-py-sm q-px-md" />
          </div>
          <div class="route-edit__save">
            <q-btn
              unelevated
              no-caps
              color="primary"
              :label="t('Сохранить маршрут')"
              :loading="saving"
              :disable="!!saveReason"
              :title="saveReason || undefined"
              class="route-edit__save-btn"
              @click="save"
            />
          </div>
        </div>
      </div>
    </template>

    <!-- главное показывающее страницы — место маршрута: общий спиннер на всю цепочку, ошибка загрузки -->
    <LoadSpinner v-else-if="busy.active.value" />
    <ErrorBox v-else-if="loadError" :error="loadError" :retry="init" class="route-edit__load-error q-ma-md" />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { LegStation, Route, RouteLeg, RoutePoint, RouteStop } from '@/api/routes'
import { fetchLegs, fetchRoute, saveRoute } from '@/api/routes'
import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import HoursInput from '@/shared/components/form/HoursInput.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { RouteKind } from '@/shared/constants/RouteKind'
import { formatHoursMinutes } from '@/shared/format'
import { provideBusy } from '@/shared/busy'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import RouteMap from '@/shared/components/map/RouteMap.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { RoleType } from '@/shared/constants/systemDicts'
import { useLoad } from '@/shared/loadable'
import { roleReason } from '@/shared/roleReason'
import { loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { leaveOf } from './duration'
import RouteStops from './RouteStops.vue'
import RouteStopsEditor from './RouteStopsEditor.vue'

/**
 * Редактор маршрута. Открывается из списка маршрутов («проваливание»): левое меню на этой странице скрыто
 * (meta.hideMenu в routes.ts), назад — кнопка со стрелкой.
 *
 * Остановки и атрибуты правятся в памяти; «Сохранить маршрут» отправляет их на сервер. Сверху — остановки
 * маршрута так, как он будет выглядеть после сохранения.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()

// маршрут правят администратор и планировщик вагонов (Ф-МАР-02); остальным кнопка неактивна, причина рядом, а
// остановки и атрибуты показаны текстом
const saveReason = computed(() => roleReason([RoleType.WAGON_PLANNER], 'Правит планировщик вагонов'))
const readonly = computed(() => !!saveReason.value)

/** Дата ISO «2026-10-30» — «30.10.2026»; пусто — прочерк. */
function dateText(iso: string): string {
  return iso ? iso.slice(0, 10).split('-').reverse().join('.') : '—'
}

const route = shallowRef<Route | null>(null)
// общее ожидание страницы: спиннер на месте маршрута — на всю цепочку загрузок
const busy = provideBusy()
// ошибка загрузки маршрута; показывается на месте маршрута
const loadError = ref<ShownError | null>(null)
const stops = ref<RouteStop[]>([])
const form = reactive({
  train: '',
  name: '',
  kind: RouteKind.REGULAR,
  since: '',
  until: '',
  hc: null as number | null,
  hl: null as number | null,
  hp: null as number | null,
})
const saving = ref(false)
// станции следования в цепочке сверху свёрнуты; развёрнуты — карта крупнее и показывает их
const chainCollapsed = ref(true)
// станция, выбранная щелчком на карте; null — не выбрана
const selectedStation = ref<number | null>(null)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

const title = computed(() => (route.value ? t('Маршрут № {train}', { train: route.value.trainNumber }) : t('Маршрут')))

// станции следования новых пар остановок, по legKey
const legs = shallowRef(new Map<string, LegStation[]>())

function legKey(leg: RouteLeg): string {
  return `${leg.from}-${leg.to}`
}

/**
 * Остановки для показа сверху и на карте. Станции следования берутся из маршрута до правки: между соседними
 * остановками — станции самого короткого куска маршрута между ними, от последнего появления предыдущей станции до
 * первого появления следующей; петли станций следования в этом куске вырезаются. Удалили остановку — она и станции
 * следования вокруг неё становятся станциями следования между её соседями; соседи — одна станция (маршрут
 * схлопнулся в редакторе) — вся петля между ними пропадает. Для пар остановок, которых в маршруте до правки не было
 * (новая станция), станции следования прокладывает сервер (legs); ещё не загруженные пары — missing.
 */
const preview = computed(() => {
  const original = route.value?.points ?? []
  const filled = stops.value.filter((s) => s.station)
  const result: RoutePoint[] = []
  const missing: RouteLeg[] = []
  // место в маршруте до правки, с которого ищется следующая пара остановок: пары идут по порядку
  let cursor = 0
  filled.forEach((stop, i) => {
    const prev = filled[i - 1]
    let passing: RoutePoint[] = []
    if (prev) {
      const found = passingBetween(original, cursor, prev, stop.station)
      if (found) {
        passing = found.passing
        cursor = found.end
      } else {
        const leg = { from: prev.station, to: stop.station }
        const stations = legs.value.get(legKey(leg))
        if (!stations) missing.push(leg)
        passing = (stations ?? []).map((s) => ({
          station: s.station,
          km: prev.km + s.km,
          haveStop: false,
          arrivalMin: null,
          departureMin: null,
        }))
      }
    }
    const last = i === filled.length - 1
    result.push(...(prev ? passing.map((p) => passingTime(p, prev, stop)) : []), {
      ...stop,
      haveStop: true,
      arrivalMin: i === 0 ? null : stop.arrivalMin,
      departureMin: last ? null : stop.departureMin,
    })
  })
  return { points: result, missing }
})
const previewPoints = computed(() => preview.value.points)

// остановки сменились, пока шла загрузка, — загрузка повторяется для новых пар
const legsLoad = useLoad(async () => {
  while (preview.value.missing.length > 0) {
    const missing = preview.value.missing
    const loaded = await fetchLegs(missing)
    const next = new Map(legs.value)
    missing.forEach((leg, i) => next.set(legKey(leg), loaded[i] ?? []))
    legs.value = next
  }
})

watch(
  () => preview.value.missing.map(legKey).join(),
  (missing) => {
    if (missing) void legsLoad.run()
  },
)

/**
 * Время станции следования между остановками from и to — по расстоянию между отправлением с from и прибытием на to,
 * как считает сервер при сохранении; прибытие и отправление совпадают.
 */
function passingTime(p: RoutePoint, from: RouteStop, to: RouteStop): RoutePoint {
  const start = leaveOf(from)
  const end = to.arrivalMin
  if (start == null || end == null) return { ...p, arrivalMin: null, departureMin: null }
  const length = to.km - from.km
  const time = start + (length > 0 ? Math.round(((end - start) * (p.km - from.km)) / length) : 0)
  return { ...p, arrivalMin: time, departureMin: time }
}

/**
 * Станции следования между станциями from и to по маршруту до правки, начиная с места cursor: самый короткий кусок
 * маршрута — первое появление to после from и последнее появление from перед ним; все станции куска — станции
 * следования, в том числе бывшие остановки; петли вырезаются. Расстояние станции следования от начала маршрута —
 * от предыдущей остановки from (её расстояние уже пересчитано в редакторе) плюс путь по маршруту до правки без
 * вырезанных петель. end — место станции to.
 *
 * @return null — пары from, to в маршруте до правки нет
 */
function passingBetween(
  points: RoutePoint[],
  cursor: number,
  from: RouteStop,
  to: number,
): { passing: RoutePoint[]; end: number } | null {
  const first = points.findIndex((p, i) => i >= cursor && p.station === from.station)
  if (first === -1) return null
  const end = points.findIndex((p, i) => i > first && p.station === to)
  if (end === -1) return null
  let start = first
  for (let i = first; i < end; i++) if (points[i]!.station === from.station) start = i
  const shift = from.km - points[start]!.km
  const passing = withoutLoops(points.slice(start + 1, end))
  return { passing: passing.map((p) => ({ ...p, km: p.km + shift, haveStop: false })), end }
}

/**
 * Станции без петель: станция встретилась снова — всё, что было между её появлениями, и само повторное появление
 * выпадают. Расстояния следующих станций уменьшаются на длину петли: путь короче.
 */
function withoutLoops(points: RoutePoint[]): RoutePoint[] {
  const result: RoutePoint[] = []
  // длина вырезанных петель, км
  let removed = 0
  for (const p of points) {
    const km = p.km - removed
    const seen = result.findIndex((r) => r.station === p.station)
    if (seen === -1) {
      result.push({ ...p, km })
    } else {
      removed += km - result[seen]!.km
      result.length = seen + 1
    }
  }
  return result
}

/** Остановки из точек маршрута: станции следования не входят. */
function toStops(points: RoutePoint[]): RouteStop[] {
  return points
    .filter((p) => p.haveStop)
    .map((p) => ({ station: p.station, km: p.km, arrivalMin: p.arrivalMin, departureMin: p.departureMin }))
}

function fill(r: Route) {
  route.value = r
  stops.value = toStops(r.points)
  // остановок нет — сразу пустая: выбор начальной станции
  if (stops.value.length === 0 && !readonly.value) {
    stops.value = [{ station: 0, km: 0, arrivalMin: null, departureMin: null }]
  }
  Object.assign(form, {
    train: r.trainNumber,
    name: r.name,
    kind: r.routeKind,
    since: r.dbeg,
    until: r.dend ?? '',
    hc: r.conductorHours,
    hl: r.lnpHours,
    hp: r.pemHours,
  })
}

function back() {
  void router.push({ name: 'routes' })
}

/** Норма часов при readonly: «2:30»; нормы нет — прочерк. */
function normText(hours: number | null): string {
  return hours === null ? '—' : formatHoursMinutes(hours)
}

async function save() {
  const r = route.value
  if (!r) return
  if (!form.train || !form.name || !form.since) {
    error.value = formError(t('Заполните номер поезда, сообщение и дату ввода маршрута'))
    return
  }
  if (form.hc === null || form.hl === null || form.hp === null) {
    error.value = formError(t('Заполните нормы часов'))
    return
  }
  if (stops.value.length < 2 || stops.value.some((s) => !s.station)) {
    error.value = formError(t('Заполните остановки маршрута'))
    return
  }
  error.value = null
  saving.value = true
  try {
    await saveRoute(
      {
        ...r,
        trainNumber: form.train,
        name: form.name,
        routeKind: form.kind,
        dbeg: form.since,
        dend: form.until || null,
        conductorHours: form.hc,
        lnpHours: form.hl,
        pemHours: form.hp,
      },
      previewPoints.value,
    )
    back()
  } catch (e) {
    error.value = shownError(e)
  } finally {
    saving.value = false
  }
}

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки
async function init() {
  loadError.value = null
  try {
    // станции нужны редактору (названия, узловые станции) — грузятся параллельно с маршрутом
    const [, r] = await busy.run(
      Promise.all([dictManager.prepareDict('Station'), fetchRoute(Number(props.id)), loadCurrentUser()]),
    )
    fill(r)
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
.route-edit__h1 {
  font-size: 1.3rem;
}

.route-edit__chain {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
  padding: 0.8rem 1rem;
  border-radius: 0.3rem;
  background: #fff;
}

.route-edit__stops {
  flex: 1;
  min-width: 0;
}

// карта в левом углу цепочки: узкая, при развёрнутых станциях следования — шире. По высоте — во весь блок цепочки,
// прижата к его верху, низу и левому краю: поля блока (.route-edit__chain) с этих сторон снимаются; между картой и
// станциями — отступ блока (gap)
.route-edit__map {
  flex: none;
  align-self: stretch;
  width: 16rem;
  margin: -0.8rem 0 -0.8rem -1rem;
}

.route-edit__map--wide {
  width: 30rem;
}

// колонки — не уже своего содержимого, растут вместе (остановки — до своей наибольшей ширины); не помещаются рядом —
// атрибуты ниже
.route-edit__columns {
  display: flex;
  flex-wrap: wrap;
  gap: 1.3rem;
  align-items: flex-start;
}

.route-edit__column {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;

  // остановки — от наименьшей ширины своей таблицы до наибольшей: ширину станции в таблице ограничивает
  // RouteStopsEditor; свободное место сверх неё забирают атрибуты
  &--stops {
    flex: 1 1 auto;
    width: min-content;
    max-width: max-content;
  }

  // атрибуты — не уже своего содержимого, растут вместе с остановками
  &--attrs {
    flex: 1 1 auto;
    max-width: 100%;
  }
}

.route-edit__section-title {
  // отступ под подписью группы — на треть меньше промежутка между блоками (0.8rem)
  margin-bottom: calc(-0.8rem / 3);
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.route-edit__card {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

// редактор остановок шире узкой колонки — прокрутка вбок внутри карточки
.route-edit__stops-card {
  overflow-x: auto;
}

// значение вместо поля при readonly: той же высоты, что и поле, — форма не меняет высоту
.route-edit__value {
  display: flex;
  align-items: center;
  min-height: var(--zs-control-height);
  color: $zs-text;
  font-size: var(--zs-control-font-size);
  font-weight: 400;

  &--mono {
    font-family: $zs-font-mono;
  }
}

.route-edit__form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

// примечание не влияет на ширину колонки: переносится по её ширине
.route-edit__note {
  contain: inline-size;
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: 1.45;
}

.route-edit__error {
  border-radius: 0.3rem;
  background: #f7dcd8;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.route-edit__save {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

.route-edit__save-btn {
  min-height: var(--zs-control-height);
  font-weight: 600;
}
</style>
