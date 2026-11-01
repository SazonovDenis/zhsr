<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title route-edit__h1">{{ title }}</h1>
    </template>

    <template v-if="route">
      <div v-if="previewPoints.length > 0" class="route-edit__chain">
        <RouteStops :points="previewPoints" layout="horizontal" />
      </div>

      <div class="route-edit__columns">
        <div class="route-edit__column">
          <div class="route-edit__section-title">{{ t('Остановки по порядку') }}</div>
          <div class="route-edit__card">
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
          <div class="route-edit__card route-edit__form">
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
              <!-- время в пути не вводится: оно считается по остановкам — путь по перегонам и стоянки -->
              <FormField label="Время в пути">
                <span class="route-edit__duration">{{ durationText }}</span>
              </FormField>
              <FormField label="Проводник, ч">
                <TextInput v-model="form.hc" inputmode="decimal" />
              </FormField>
              <FormField label="ЛНП, ч">
                <TextInput v-model="form.hl" inputmode="decimal" />
              </FormField>
              <FormField label="ПЭМ, ч">
                <TextInput v-model="form.hp" inputmode="decimal" />
              </FormField>
            </FormSection>

            <div v-if="error" class="route-edit__error">{{ error }}</div>
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

    <div v-else-if="!loading" class="route-edit__missing">{{ t('Маршрут не найден') }}</div>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { Route, RoutePoint, RouteStop } from '@/api/routes'
import { fetchRoute, saveRoute } from '@/api/routes'
import { t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
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
const loading = ref(true)
const stops = ref<RouteStop[]>([])
const form = reactive({
  train: '',
  name: '',
  kind: 'REGULAR',
  since: '',
  until: '',
  hc: '',
  hl: '',
  hp: '',
})
const saving = ref(false)
const error = ref('')

const title = computed(() => (route.value ? t('Маршрут № {train}', { train: route.value.train }) : t('Маршрут')))

/**
 * Остановки для показа сверху. Станции следования известны только для пар соседних остановок, которые были в
 * маршруте до правки; для новых пар их построит сервер при сохранении.
 */
const previewPoints = computed<RoutePoint[]>(() => {
  const original = route.value?.points ?? []
  const filled = stops.value.filter((s) => s.station)
  return filled.flatMap((stop, i) => {
    const prev = filled[i - 1]
    const passing = prev ? passingBetween(original, prev, stop) : []
    // время в пути остановки — от предыдущей остановки; часть его приходится на перегоны до станций следования
    const passingMin = passing.reduce((sum, p) => sum + p.runDuration, 0)
    const last = i === filled.length - 1
    return [
      ...passing,
      {
        ...stop,
        stop: true,
        runDuration: Math.max(0, stop.runDuration - passingMin),
        stopDuration: i === 0 || last ? 0 : stop.stopDuration,
      },
    ]
  })
})

function passingBetween(points: RoutePoint[], from: RouteStop, to: RouteStop): RoutePoint[] {
  const start = points.findIndex((p) => p.stop && p.station === from.station && p.km === from.km)
  if (start === -1) return []
  const end = points.findIndex((p, i) => i > start && p.stop)
  if (end === -1 || points[end]!.station !== to.station) return []
  return points.slice(start + 1, end)
}

/** Остановки из точек маршрута: время в пути остановки — от предыдущей остановки, вместе со станциями следования. */
function toStops(points: RoutePoint[]): RouteStop[] {
  const result: RouteStop[] = []
  let runDuration = 0
  for (const p of points) {
    runDuration += p.runDuration
    if (!p.stop) continue
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
    train: r.train,
    name: r.name,
    kind: r.kind,
    since: r.since,
    until: r.until ?? '',
    hc: String(r.hoursConductor),
    hl: String(r.hoursLnp),
    hp: String(r.hoursPem),
  })
}

function back() {
  void router.push({ name: 'routes' })
}

/** Время в пути по остановкам, мин: путь от начальной станции до конечной вместе с промежуточными стоянками. */
const routeMinutes = computed(() => totalMinutes(stops.value))

/** «19:05» — часы и минуты, тот же формат, что у времени в пути на схеме маршрута */
const durationText = computed(() => formatHm(routeMinutes.value))

function hours(value: string): number | null {
  const n = Number(value.replace(',', '.'))
  return value && Number.isFinite(n) && n >= 0 ? n : null
}

async function save() {
  const r = route.value
  if (!r) return
  const values = [form.hc, form.hl, form.hp].map(hours)
  if (!form.train || !form.name || !form.since) {
    error.value = t('Заполните номер поезда, сообщение и дату ввода маршрута')
    return
  }
  if (values.some((v) => v === null)) {
    error.value = t('Время в пути и нормы часов — неотрицательные числа')
    return
  }
  if (stops.value.length < 2 || stops.value.some((s) => !s.station)) {
    error.value = t('Заполните остановки маршрута')
    return
  }
  error.value = ''
  saving.value = true
  try {
    const [hc, hl, hp] = values as number[]
    const saved = await saveRoute(
      {
        ...r,
        train: form.train,
        name: form.name,
        kind: form.kind,
        since: form.since,
        until: form.until || null,
        // время в пути — по остановкам, в часах с точностью до десятых
        durationHours: Math.round(routeMinutes.value / 6) / 10,
        hoursConductor: hc!,
        hoursLnp: hl!,
        hoursPem: hp!,
      },
      stops.value,
    )
    fill(saved)
    Notify.create({ message: t('Маршрут сохранён'), color: 'primary' })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await dictManager.prepareDicts('RouteKind', 'Station')
  try {
    const r = await fetchRoute(props.id)
    if (r) fill(r)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.route-edit__h1 {
  font-size: 1.25rem;
}

.route-edit__chain {
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
}

.route-edit__columns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(21.3rem, 1fr));
  gap: 1.25rem;
  align-items: start;
}

.route-edit__column {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.route-edit__section-title {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.route-edit__card {
  padding: 0.75rem 0.875rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
}

.route-edit__form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem 1.125rem;
}

.route-edit__kind {
  align-self: flex-start;
}

.route-edit__duration {
  display: flex;
  align-items: center;
  height: var(--zs-control-height);
  color: $zs-text;
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
}

.route-edit__note {
  color: $zs-text-muted;
  font-size: 0.78125rem;
  line-height: 1.45;
}

.route-edit__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #f7dcd8;
  color: #8f2416;
  font-size: 0.84375rem;
}

.route-edit__save {
  display: flex;
  justify-content: flex-end;
}

.route-edit__save-btn {
  min-height: var(--zs-control-height);
  font-weight: 600;
}

.route-edit__missing {
  color: $zs-text-muted;
}
</style>
