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
          class="route-edit__map"
          :class="{ 'route-edit__map--wide': !chainCollapsed }"
          @station="selectedStation = $event.station"
        />
        <div class="route-edit__stops">
          <RouteStops v-model:collapsed="chainCollapsed" :points="previewPoints" layout="horizontal" />
        </div>
      </div>

      <div class="route-edit__columns">
        <div class="route-edit__column">
          <div class="route-edit__section-title">{{ t('Остановки по порядку') }}</div>
          <div class="route-edit__card q-pa-md">
            <RouteStopsEditor v-model="stops" />
          </div>
          <div class="route-edit__note">
            {{
              t(
                'Начальная и конечная станции — узловые (со своим депо). Следующая станция — любая, достижимая по маршруту от предыдущей.',
              )
            }}
          </div>
        </div>

        <div class="route-edit__column">
          <div class="route-edit__section-title">{{ t('Атрибуты') }}</div>
          <div class="route-edit__card route-edit__form q-pa-md">
            <FormSection columns="7rem minmax(0, 1fr)">
              <FormField label="Номер поезда">
                <TextInput v-model="form.train" mono />
              </FormField>
              <FormField label="Сообщение">
                <TextInput v-model="form.name" placeholder="Например, Астана — Караганда" />
              </FormField>
            </FormSection>

            <FormField label="Вид маршрута">
              <CbSegment
                v-model="form.kind"
                class="route-edit__kind"
                single
                required
                :values="dictChoices('RouteKind')"
              />
            </FormField>

            <FormSection columns="repeat(2, max-content)">
              <FormField label="Дата ввода маршрута">
                <DatePicker
                  v-model="form.since"
                  date-text="Дата ввода маршрута"
                  :quick="['today', 'todayPlus1', 'todayPlus2', 'nextMonthStart']"
                />
              </FormField>
              <FormField label="Дата завершения">
                <DatePicker
                  v-model="form.until"
                  date-text="Дата завершения"
                  :quick="['monthEnd', 'yearEnd', 'today']"
                />
              </FormField>
            </FormSection>

            <FormSection title="Время в пути и нормы часов" divided columns="repeat(2, minmax(0, 1fr))">
              <!-- время в пути вводит пользователь; по остановкам (перегоны и стоянки) — подсказка и значение по умолчанию -->
              <FormField label="Время в пути, ч:мм">
                <TextInput v-model="form.duration" placeholder="ч:мм" @update:model-value="onDurationInput" />
                <span class="route-edit__hint">{{ t('по остановкам — {time}', { time: durationText }) }}</span>
              </FormField>
              <FormField label="Проводник, ч">
                <TextInput v-model="form.hc" inputmode="numeric" />
              </FormField>
              <FormField label="ЛНП, ч">
                <TextInput v-model="form.hl" inputmode="numeric" />
              </FormField>
              <FormField label="ПЭМ, ч">
                <TextInput v-model="form.hp" inputmode="numeric" />
              </FormField>
            </FormSection>

            <ErrorBox v-if="error" :error="error" class="route-edit__error q-py-sm q-px-md" />
          </div>
          <div class="route-edit__save">
            <q-btn
              unelevated
              no-caps
              color="primary"
              :label="t('Сохранить маршрут')"
              :loading="saving"
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
import { Notify } from 'quasar'
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { Route, RoutePoint, RouteStop } from '@/api/routes'
import { fetchRoute, saveRoute } from '@/api/routes'
import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { RouteKind } from '@/shared/constants/RouteKind'
import { provideBusy } from '@/shared/busy'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import RouteMap from '@/shared/components/map/RouteMap.vue'
import { dictManager } from '@/shared/dict/DictManager'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { formatHm, totalMinutes } from './duration'
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
  // время в пути «ч:мм»
  duration: '',
  hc: '',
  hl: '',
  hp: '',
})
const saving = ref(false)
// станции следования в цепочке сверху свёрнуты; развёрнуты — карта крупнее и показывает их
const chainCollapsed = ref(true)
// станция, выбранная щелчком на карте; null — не выбрана
const selectedStation = ref<number | null>(null)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

const title = computed(() => (route.value ? t('Маршрут № {train}', { train: route.value.trainNumber }) : t('Маршрут')))

/**
 * Остановки для показа сверху и на карте. Станции следования берутся из маршрута до правки: между соседними
 * остановками — станции самого короткого куска маршрута между ними, от последнего появления предыдущей станции до
 * первого появления следующей; петли станций следования в этом куске вырезаются. Удалили остановку — она и станции
 * следования вокруг неё становятся станциями следования между её соседями; соседи — одна станция (маршрут
 * схлопнулся в редакторе) — вся петля между ними пропадает. Для пар остановок, которых в маршруте до правки не было
 * (новая станция), станции следования построит сервер при сохранении.
 */
const previewPoints = computed<RoutePoint[]>(() => {
  const original = route.value?.points ?? []
  const filled = stops.value.filter((s) => s.station)
  const result: RoutePoint[] = []
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
      }
    }
    // время в пути остановки — от предыдущей остановки; часть его приходится на перегоны до станций следования
    const passingMin = passing.reduce((sum, p) => sum + p.runDuration, 0)
    const last = i === filled.length - 1
    result.push(...passing, {
      ...stop,
      haveStop: true,
      runDuration: Math.max(0, stop.runDuration - passingMin),
      stopDuration: i === 0 || last ? 0 : stop.stopDuration,
    })
  })
  return result
})

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
  return { passing: passing.map((p) => ({ ...p, km: p.km + shift, haveStop: false, stopDuration: 0 })), end }
}

/**
 * Станции без петель: станция встретилась снова — всё, что было между её появлениями, и само повторное появление
 * выпадают. Время в пути выпавших перегонов не учитывается, расстояния следующих станций уменьшаются на длину петли:
 * путь короче.
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

/** Остановки из точек маршрута: время в пути остановки — от предыдущей остановки, вместе со станциями следования. */
function toStops(points: RoutePoint[]): RouteStop[] {
  const result: RouteStop[] = []
  let runDuration = 0
  for (const p of points) {
    runDuration += p.runDuration
    if (!p.haveStop) continue
    result.push({
      station: p.station,
      km: p.km,
      runDuration: result.length === 0 ? 0 : runDuration,
      stopDuration: p.stopDuration,
    })
    runDuration = 0
  }
  return result
}

function fill(r: Route) {
  route.value = r
  stops.value = toStops(r.points)
  Object.assign(form, {
    train: r.trainNumber,
    name: r.name,
    kind: r.routeKind,
    since: r.dbeg,
    until: r.dend ?? '',
    duration: r.duration > 0 ? formatHm(r.duration) : '',
    hc: r.conductorHours === null ? '' : String(r.conductorHours),
    hl: r.lnpHours === null ? '' : String(r.lnpHours),
    hp: r.pemHours === null ? '' : String(r.pemHours),
  })
  durationTouched = r.duration > 0
  if (!durationTouched) form.duration = durationText.value
}

// время в пути не вводили (новый маршрут) — оно идёт за подсказкой по остановкам
let durationTouched = false

function onDurationInput() {
  durationTouched = true
}

function back() {
  void router.push({ name: 'routes' })
}

/** Время в пути по остановкам, мин: путь от начальной станции до конечной вместе с промежуточными стоянками. */
const routeMinutes = computed(() => totalMinutes(stops.value))

/** «19:05» — часы и минуты, тот же формат, что у времени в пути на схеме маршрута */
const durationText = computed(() => formatHm(routeMinutes.value))
watch(routeMinutes, () => {
  if (!durationTouched) form.duration = durationText.value
})

/** Норма часов: целое неотрицательное число; иначе null. */
function hours(value: string): number | null {
  const text = value.trim()
  return /^\d+$/.test(text) ? Number(text) : null
}

/** Время в пути «ч:мм» → минуты; не «ч:мм» или 0 — null. */
function durationMinutes(value: string): number | null {
  const m = /^(\d+):([0-5]\d)$/.exec(value.trim())
  const minutes = m ? Number(m[1]) * 60 + Number(m[2]) : 0
  return minutes > 0 ? minutes : null
}

async function save() {
  const r = route.value
  if (!r) return
  const values = [form.hc, form.hl, form.hp].map(hours)
  if (!form.train || !form.name || !form.since) {
    error.value = formError(t('Заполните номер поезда, сообщение и дату ввода маршрута'))
    return
  }
  const duration = durationMinutes(form.duration)
  if (duration === null) {
    error.value = formError(t('Укажите время в пути в виде ч:мм, например 19:05'))
    return
  }
  if (values.some((v) => v === null)) {
    error.value = formError(t('Нормы часов — целые неотрицательные числа'))
    return
  }
  if (stops.value.length < 2 || stops.value.some((s) => !s.station)) {
    error.value = formError(t('Заполните остановки маршрута'))
    return
  }
  error.value = null
  saving.value = true
  try {
    const [hc, hl, hp] = values as number[]
    const saved = await saveRoute(
      {
        ...r,
        trainNumber: form.train,
        name: form.name,
        routeKind: form.kind,
        dbeg: form.since,
        dend: form.until || null,
        duration,
        conductorHours: hc!,
        lnpHours: hl!,
        pemHours: hp!,
      },
      previewPoints.value,
    )
    fill(saved)
    Notify.create({ message: t('Маршрут сохранён'), color: 'primary' })
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
    const [, r] = await busy.run(Promise.all([dictManager.prepareDict('Station'), fetchRoute(Number(props.id))]))
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
  width: 12rem;
  margin: -0.8rem 0 -0.8rem -1rem;
}

.route-edit__map--wide {
  width: 30rem;
}

.route-edit__columns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(21.3rem, 1fr));
  gap: 1.3rem;
  align-items: start;
}

.route-edit__column {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;
}

.route-edit__section-title {
  // отступ под подписью группы — на треть меньше промежутка между блоками (0.8rem)
  margin-bottom: calc(-0.8rem / 3);
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.route-edit__card {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.route-edit__form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.route-edit__kind {
  align-self: flex-start;
}

.route-edit__hint {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.route-edit__note {
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
  justify-content: flex-end;
}

.route-edit__save-btn {
  min-height: var(--zs-control-height);
  font-weight: 600;
}
</style>
