<template>
  <!-- readonly — правка недоступна: вместо полей — их значения текстом, кнопок правки нет -->
  <div class="stops-editor">
    <!-- заголовок и строки — одна общая сетка: колонки заголовка всегда совпадают с полями строк -->
    <div v-if="stops.length > 0" class="stops-editor__table">
      <span />
      <span class="stops-editor__head">{{ t('Станция') }}</span>
      <span class="stops-editor__head stops-editor__right">{{ t('Расстояние') }}</span>
      <span class="stops-editor__head stops-editor__right q-pr-md">{{ t('В пути') }}</span>
      <span class="stops-editor__head">{{ t('Время прибытия') }}</span>
      <span class="stops-editor__head">{{ t('Стоянка, мин') }}</span>
      <span class="stops-editor__head">{{ t('Время отправления') }}</span>
      <span />

      <template v-for="(stop, index) in stops" :key="index">
        <span class="stops-editor__n">{{ index + 1 }}</span>

        <span v-if="readonly" class="stops-editor__text">{{ dictManager.getValue('Station', stop.station) }}</span>
        <CbList
          v-else
          class="stops-editor__station"
          single
          no-clear
          placeholder="Выберите станцию…"
          :model-value="stop.station || null"
          :values="valuesFor(index)"
          :source="openRow === index ? optionsLoad : null"
          @open="loadOptions(index)"
          @update:model-value="choose(index, $event)"
        >
          <!-- строка выбора: станция, под ней станции следования по пути, справа — расстояние от предыдущей остановки -->
          <template #option="{ item }">
            <span class="stops-editor__option q-pa-none">
              <span class="stops-editor__option-text">
                <span>{{ item.text }}</span>
                <span v-if="viaText(item)" class="stops-editor__via">{{ viaText(item) }}</span>
              </span>
              <span v-if="item.data?.distance" class="stops-editor__muted">
                {{ t('+{km} км', { km: item.data.distance }) }}
              </span>
            </span>
          </template>
        </CbList>

        <!-- у начальной станции нет ни расстояния, ни времени в пути, ни прибытия; у конечной расстояние и время в пути —
             итог маршрута, жирным -->
        <span class="stops-editor__km" :class="{ 'stops-editor__final': isFinal(index) }">{{
          index > 0 && stop.station ? t('{km} км', { km: stop.km }) : ''
        }}</span>

        <!-- время в пути от отправления с начальной станции -->
        <span class="stops-editor__km q-pr-md" :class="{ 'stops-editor__final': isFinal(index) }">{{
          index > 0 ? elapsedText(stop) : ''
        }}</span>

        <!-- у конечной станции — только прибытие; часы — от полуночи дня отправления, следующие сутки — больше 24:
             «26:55» -->
        <span v-if="readonly" class="stops-editor__text">{{ index > 0 ? timeText(stop.arrivalMin) : '' }}</span>
        <TimeInput
          v-else-if="index > 0 && stop.arrivalMin !== null"
          :model-value="stop.arrivalMin"
          over-day
          :nullable="false"
          @update:model-value="setTime(index, 'arrivalMin', $event)"
        />
        <span v-else />

        <span v-if="readonly" class="stops-editor__text">{{
          index > 0 && index < stops.length - 1 && stop.arrivalMin !== null && stop.departureMin !== null
            ? stopOf(stop)
            : ''
        }}</span>
        <TextInput
          v-else-if="index > 0 && index < stops.length - 1 && stop.arrivalMin !== null && stop.departureMin !== null"
          :model-value="String(stopOf(stop))"
          inputmode="numeric"
          @update:model-value="setStop(index, $event)"
        />
        <span v-else />
        <span v-if="readonly" class="stops-editor__text">{{
          index < stops.length - 1 ? timeText(stop.departureMin) : ''
        }}</span>
        <TimeInput
          v-else-if="index < stops.length - 1 && stop.departureMin !== null"
          :model-value="stop.departureMin"
          over-day
          :nullable="false"
          @update:model-value="setTime(index, 'departureMin', $event)"
        />
        <span v-else />

        <span v-if="readonly" />
        <span v-else class="stops-editor__buttons">
          <!-- удалить — красная, добавить — тёмно-зелёная; обе квадратные: отступ вокруг иконки — до высоты поля -->
          <q-btn
            unelevated
            outline
            color="remove"
            class="zs-soft-border"
            icon="close"
            padding="0.4rem"
            :title="t('Удалить остановку')"
            :disable="stops.length <= 1"
            @click="remove(index)"
          />
          <q-btn
            unelevated
            outline
            color="ok"
            class="zs-soft-border"
            icon="add"
            padding="0.4rem"
            :title="t('Добавить станцию после этой')"
            @click="insert(index)"
          />
        </span>
      </template>
    </div>

    <div v-for="warning in hubWarnings" :key="warning.keypath" class="stops-editor__warning q-px-md q-py-sm">
      <i18n-t :keypath="warning.keypath" tag="span" scope="global">
        <template #station>
          <b>{{ dictManager.getValue('Station', warning.station) }}</b>
        </template>
      </i18n-t>
    </div>
    <!-- пояснения — одним блоком: что не так с маршрутом и правило выбора станции -->
    <div class="stops-editor__notes q-pl-lg">
      <div v-if="problem">{{ problem }}</div>
      <div>{{ t('Следующая станция — любая, достижимая по маршруту от предыдущей.') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'

import type { RouteStop } from '@/api/routes'
import { fetchNextStations, recalcStops } from '@/api/routes'
import { t } from '@/boot/i18n'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import TimeInput from '@/shared/components/form/TimeInput.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { compareText } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import { formatDuration, formatTime, stopOf } from './duration'

/**
 * Редактор «Остановки по порядку». Правит список остановок в памяти (v-model); сохраняет маршрут владелец.
 *
 * - Станция остановки выбирается в CbList из станций, на которые можно перейти от предыдущей остановки (для
 *   первой — из всех станций маршрутной сети). Список загружается при открытии; в строке — станции следования по пути («через …») и
 *   расстояние от предыдущей остановки.
 * - Время остановки — минуты от полуночи дня отправления с начальной станции (1440 и больше — следующие сутки).
 *   У начальной станции вводится отправление, у промежуточных — прибытие, стоянка и отправление, у конечной —
 *   прибытие. Время вводится и показывается как «чч:мм», часы — от полуночи дня отправления: следующие сутки — часы
 *   больше 24 («26:55»).
 * - Времена связаны: прибытие сдвигает отправление (стоянка прежняя), стоянка — отправление, отправление — прибытие
 *   (стоянка прежняя); следующие остановки сдвигаются на ту же разницу. «В пути» — время в пути от отправления с
 *   начальной станции; у конечной станции расстояние и время в пути — итог маршрута, жирным.
 * - У маршрута без остановок владелец передаёт одну пустую остановку: начальная станция выбирается сразу.
 * - «+» вставляет пустую остановку после этой, ✕ удаляет остановку.
 * - После выбора станции и удаления остановки расстояния и времена пересчитываются (recalcStops): время в пути от
 *   предыдущей остановки по умолчанию (по расстоянию) — там, где сменилась станция или предыдущая остановка.
 * - Под списком — предупреждения о неузловых начальной и конечной станциях (сохранить маршрут можно) и что не так с
 *   маршрутом: время по остановкам не должно убывать.
 */
const stops = defineModel<RouteStop[]>({ required: true })

/** правка недоступна: значения показаны текстом, кнопок правки нет */
const { readonly = false } = defineProps<{ readonly?: boolean }>()

// станции для выбора загружаются для той остановки, у которой открыт список
const openRow = ref(-1)
const options = shallowRef<ChoiceItem[]>([])

function isHub(station: number): boolean {
  return dictManager.getRec('Station', station)?.hub === true
}

/** Предупреждения: начальная и конечная станции не узловые; сохранению маршрута не мешают. */
const hubWarnings = computed(() => {
  const last = stops.value.length - 1
  return [
    { keypath: 'Начальная станция {station} не является узловой — не имеет депо.', station: stops.value[0]?.station },
    {
      keypath: 'Конечная станция {station} не является узловой — не имеет депо.',
      // единственная остановка — начальная
      station: last > 0 ? stops.value[last]?.station : undefined,
    },
  ].flatMap(({ keypath, station }) => (station && !isHub(station) ? [{ keypath, station }] : []))
})

const problem = computed(() => {
  const filled = stops.value.filter((s) => s.station)
  if (filled.length < stops.value.length) return t('Выберите станцию в каждой остановке.')
  if (filled.length < 2) return t('В маршруте должно быть не меньше двух остановок.')
  if (decreasing.value) return t('Время по остановкам не должно убывать.')
  return ''
})

/** Время по остановкам убывает: прибытие раньше отправления с предыдущей остановки или отправление раньше прибытия. */
const decreasing = computed(() => {
  let prev = -Infinity
  for (const stop of stops.value) {
    for (const time of [stop.arrivalMin, stop.departureMin]) {
      if (time == null) continue
      if (time < prev) return true
      prev = time
    }
  }
  return false
})

/** Предыдущая заполненная остановка: от неё считаются доступные станции. */
function previousOf(index: number): RouteStop | null {
  for (let i = index - 1; i >= 0; i--) {
    const stop = stops.value[i]
    if (stop?.station) return stop
  }
  return null
}

function stationItem(station: number, km: number, distance = 0, via: number[] = []): ChoiceItem {
  return { id: station, text: dictManager.getValue('Station', station), data: { km, distance, via } }
}

/** «через Алматы-1 → Отар» — станции следования между предыдущей остановкой и станцией. */
function viaText(item: ChoiceItem): string {
  const via = item.data?.via
  if (!Array.isArray(via) || via.length === 0) return ''
  return t('через {stations}', { stations: via.map((s: number) => dictManager.getValue('Station', s)).join(' → ') })
}

/**
 * Значения списка остановки: пока список закрыт — только текущая станция (CbList показывает выбранное по values),
 * открыт — загруженные станции и текущая, если её среди них нет.
 */
function valuesFor(index: number): ChoiceItem[] {
  const stop = stops.value[index]
  const current = stop?.station ? [stationItem(stop.station, stop.km)] : []
  if (openRow.value !== index) return current
  const loaded = options.value
  return loaded.some((o) => o.id === stop?.station) ? loaded : [...current, ...loaded]
}

// станции для открытого списка; ошибка загрузки — в самом выпадающем списке вместо вариантов
const optionsLoad = useLoad(async () => {
  options.value = []
  const previous = previousOf(openRow.value)
  const next = await fetchNextStations(previous)
  const items = next.map((n) => stationItem(n.station, n.km, n.distance, n.via))
  // следующие станции сервер отдаёт по близости к предыдущей; начальные — по названию
  if (!previous) items.sort((a, b) => compareText(a.text ?? '', b.text ?? ''))
  options.value = items
})

function loadOptions(index: number) {
  openRow.value = index
  void optionsLoad.run()
}

/** Применить правку; changed — остановки, у которых сменилась станция или предыдущая остановка. */
async function update(next: RouteStop[], changed: number[]) {
  stops.value = next
  // пустые остановки пересчитывать не с чем: расстояния считаются по заполненным
  if (next.every((s) => s.station)) stops.value = await recalcStops(next, changed)
}

function choose(index: number, value: ChoiceModel) {
  if (typeof value !== 'number') return
  const data = options.value.find((o) => o.id === value)?.data
  const old = stops.value[index]
  // время остановки — прежнее (стоянка сохраняется), время в пути до неё сервер ставит по умолчанию
  const stop: RouteStop = {
    station: value,
    km: typeof data?.km === 'number' ? data.km : 0,
    arrivalMin: old?.arrivalMin ?? null,
    departureMin: old?.departureMin ?? null,
  }
  // у следующей остановки сменилась предыдущая — её время в пути тоже по умолчанию
  void update(
    stops.value.map((s, i) => (i === index ? stop : s)),
    [index, index + 1],
  )
}

type TimeField = 'arrivalMin' | 'departureMin'

/** Время остановки текстом «чч:мм»; нет времени — пусто. */
function timeText(minutes: number | null): string {
  return minutes == null ? '' : formatTime(minutes)
}

/** Время в пути от отправления с начальной станции до прибытия на остановку — «3 ч 42 м». */
function elapsedText(stop: RouteStop): string {
  const start = stops.value[0]?.departureMin
  return stop.arrivalMin == null || start == null ? '' : formatDuration(stop.arrivalMin - start)
}

/** Конечная ли остановка: её расстояние и время в пути — итог маршрута. */
function isFinal(index: number): boolean {
  return index > 0 && index === stops.value.length - 1
}

/** Остановки, где остановка index получила время changed, а следующие за ней сдвинуты на delta, мин. */
function shifted(index: number, changed: RouteStop, delta: number): RouteStop[] {
  const add = (v: number | null) => (v == null ? null : v + delta)
  return stops.value.map((s, i) =>
    i === index ? changed : i < index ? s : { ...s, arrivalMin: add(s.arrivalMin), departureMin: add(s.departureMin) },
  )
}

function setTime(index: number, field: TimeField, time: number | null) {
  const stop = stops.value[index]
  if (time === null || !stop) return
  // прибытие сдвигает отправление, отправление — прибытие: стоянка прежняя; у начальной станции прибытия нет, у
  // конечной — отправления
  const delta = time - (stop[field] ?? time)
  const add = (v: number | null) => (v == null ? null : v + delta)
  const changed =
    field === 'arrivalMin'
      ? { ...stop, arrivalMin: time, departureMin: add(stop.departureMin) }
      : { ...stop, arrivalMin: add(stop.arrivalMin), departureMin: time }
  stops.value = shifted(index, changed, delta)
}

/** Стоянка — отправление сдвигается, следующие остановки — на ту же разницу. */
function setStop(index: number, text: string) {
  const stop = stops.value[index]
  if (!stop || stop.arrivalMin == null || stop.departureMin == null) return
  const value = Number.parseInt(text, 10)
  const dwell = Number.isFinite(value) && value > 0 ? value : 0
  const departure = stop.arrivalMin + dwell
  stops.value = shifted(index, { ...stop, departureMin: departure }, departure - stop.departureMin)
}

function insert(index: number) {
  const next = [...stops.value]
  // станция 0 — ещё не выбрана; время поставит пересчёт после выбора станции
  next.splice(index + 1, 0, { station: 0, km: 0, arrivalMin: null, departureMin: null })
  stops.value = next
}

/**
 * Удалить остановку. На её место встала следующая остановка: у неё сменилась предыдущая. Соседи удалённой — одна и та
 * же станция (Шортанды — Айсары — Шортанды): маршрут схлопывается, две остановки сливаются в первую, на место второй
 * встаёт следующая за ней.
 */
function remove(index: number) {
  const next = stops.value.filter((_, i) => i !== index)
  const prev = next[index - 1]
  const same = prev && prev.station && next[index]?.station === prev.station
  void update(same ? next.filter((_, i) => i !== index) : next, [index])
}
</script>

<style scoped lang="scss">
.stops-editor {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

// номер, станция, расстояние, в пути, прибытие, стоянка, отправление, кнопки — заголовок и строки в одной сетке
// станция — от 15 до 25rem: вся таблица — от 56 до 66rem
.stops-editor__table {
  display: grid;
  grid-template-columns: 1.3rem minmax(15rem, 25rem) 5rem 5.5rem 5.5rem 4rem 5.5rem auto;
  align-items: center;
  gap: 0.5rem;
}

// заголовки в две строки и в одну — по нижнему краю, вплотную к полям
.stops-editor__head {
  align-self: end;
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 500;
}

.stops-editor__right {
  text-align: right;
}

.stops-editor__n {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
}

.stops-editor__station {
  min-width: 0;
}

.stops-editor__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.stops-editor__option-text {
  display: flex;
  flex-direction: column;
  gap: 0.0625rem;
}

.stops-editor__via {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.stops-editor__km {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

// значение вместо поля при readonly: той же высоты, что и поле, — строки не меняют высоту
.stops-editor__text {
  display: flex;
  align-items: center;
  min-height: var(--zs-control-height);
  color: $zs-text;
  font-size: var(--zs-control-font-size);
}

// итог маршрута у конечной остановки: расстояние и время в пути
.stops-editor__final {
  color: $zs-text;
  font-weight: 700;
}

.stops-editor__buttons {
  display: flex;
  gap: 0.5rem;
}

.stops-editor__muted {
  color: $zs-text-muted;
  font-variant-numeric: tabular-nums;
}

.stops-editor__warning {
  border-radius: 0.3rem;
  background: $zs-warning-bg;
  color: $zs-text;
  font-size: 0.8rem;
}

// пояснения не влияют на ширину таблицы: переносятся по её ширине
.stops-editor__notes {
  contain: inline-size;
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: 1.45;
}
</style>
