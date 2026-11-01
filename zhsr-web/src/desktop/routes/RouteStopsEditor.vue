<template>
  <div class="stops-editor">
    <!-- заголовок и строки — одна общая сетка: колонки заголовка всегда совпадают с полями строк -->
    <div v-if="stops.length > 0" class="stops-editor__table">
      <span />
      <span class="stops-editor__head">{{ t('Станция') }}</span>
      <span class="stops-editor__head stops-editor__right">{{ t('Км') }}</span>
      <span class="stops-editor__head">{{ t('В пути, мин') }}</span>
      <span class="stops-editor__head">{{ t('Стоянка, мин') }}</span>
      <span />

      <template v-for="(stop, index) in stops" :key="index">
        <span class="stops-editor__n">{{ index + 1 }}</span>

        <CbList
          class="stops-editor__station"
          single
          placeholder="Выберите станцию…"
          :model-value="stop.station || null"
          :values="valuesFor(index)"
          @open="loadOptions(index)"
          @update:model-value="choose(index, $event)"
        >
          <!-- строка выбора: станция, под ней станции следования по пути, справа — расстояние от предыдущей остановки -->
          <template #option="{ item }">
            <span class="stops-editor__option">
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

        <span class="stops-editor__km">{{ stop.station ? stop.km : '' }}</span>

        <!-- у начальной станции нет ни пути до неё, ни стоянки; у конечной — стоянки -->
        <TextInput
          v-if="index > 0"
          :model-value="String(stop.runDuration)"
          inputmode="numeric"
          @update:model-value="setMinutes(index, 'runDuration', $event)"
        />
        <span v-else />
        <TextInput
          v-if="index > 0 && index < stops.length - 1"
          :model-value="String(stop.stopDuration)"
          inputmode="numeric"
          @update:model-value="setMinutes(index, 'stopDuration', $event)"
        />
        <span v-else />

        <span class="stops-editor__buttons">
          <button
            type="button"
            class="stops-editor__delete"
            :title="t('Удалить остановку')"
            :disabled="stops.length <= 1"
            @click="remove(index)"
          >
            ✕
          </button>
          <button
            type="button"
            class="stops-editor__insert"
            :title="t('Добавить станцию после этой')"
            @click="insert(index)"
          >
            {{ t('+ Станция') }}
          </button>
        </span>
      </template>
    </div>

    <button v-if="stops.length === 0" type="button" class="stops-editor__insert" @click="insert(-1)">
      {{ t('+ Начальная станция') }}
    </button>

    <div v-if="problem" class="stops-editor__problem">{{ problem }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'

import type { RouteStop } from '@/api/routes'
import { fetchNextStations, recalcStops } from '@/api/routes'
import { t } from '@/boot/i18n'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Редактор «Остановки по порядку». Правит список остановок в памяти (v-model); сохраняет маршрут владелец.
 *
 * - Станция остановки выбирается в CbList из станций, на которые можно перейти от предыдущей остановки (для
 *   первой — из узловых). Список загружается при открытии; в строке — станции следования по пути («через …») и
 *   расстояние от предыдущей остановки.
 * - Время в пути от предыдущей остановки при выборе станции подставляется по умолчанию (его отдаёт сервер вместе с
 *   расстоянием); пользователь может его изменить. Стоянку пользователь вводит сам.
 * - «+ Станция» вставляет пустую остановку после этой, ✕ удаляет остановку.
 * - После каждой правки расстояния пересчитываются (recalcStops); время в пути по умолчанию заново ставится только
 *   там, где сменилась станция или предыдущая остановка.
 * - Под списком — что не так с маршрутом: начальная и конечная станции должны быть узловыми.
 */
const stops = defineModel<RouteStop[]>({ required: true })

// станции для выбора загружаются для той остановки, у которой открыт список
const openRow = ref(-1)
const options = shallowRef<ChoiceItem[]>([])

onMounted(() => dictManager.prepareDict('Station'))

function isHub(station: string): boolean {
  return dictManager.getRec('Station', station)?.hub === true
}

const problem = computed(() => {
  const filled = stops.value.filter((s) => s.station)
  if (filled.length < stops.value.length) return t('Выберите станцию в каждой остановке.')
  if (filled.length < 2) return t('В маршруте должно быть не меньше двух остановок.')
  if (!isHub(filled[0]!.station) || !isHub(filled[filled.length - 1]!.station)) {
    return t('Начальная и конечная станции должны быть узловыми.')
  }
  return ''
})

/** Предыдущая заполненная остановка: от неё считаются доступные станции. */
function previousOf(index: number): RouteStop | null {
  for (let i = index - 1; i >= 0; i--) {
    const stop = stops.value[i]
    if (stop?.station) return stop
  }
  return null
}

function stationItem(station: string, km: number, runDuration: number, distance = 0, via: string[] = []): ChoiceItem {
  return { id: station, text: dictManager.getValue('Station', station), data: { km, runDuration, distance, via } }
}

/** «через Алматы-1 → Отар» — станции следования между предыдущей остановкой и станцией. */
function viaText(item: ChoiceItem): string {
  const via = item.data?.via
  if (!Array.isArray(via) || via.length === 0) return ''
  return t('через {stations}', { stations: via.map((s: string) => dictManager.getValue('Station', s)).join(' → ') })
}

/**
 * Значения списка остановки: пока список закрыт — только текущая станция (CbList показывает выбранное по values),
 * открыт — загруженные станции и текущая, если её среди них нет.
 */
function valuesFor(index: number): ChoiceItem[] {
  const stop = stops.value[index]
  const current = stop?.station ? [stationItem(stop.station, stop.km, stop.runDuration)] : []
  if (openRow.value !== index) return current
  const loaded = options.value
  return loaded.some((o) => o.id === stop?.station) ? loaded : [...current, ...loaded]
}

async function loadOptions(index: number) {
  openRow.value = index
  options.value = []
  const next = await fetchNextStations(previousOf(index))
  options.value = next.map((n) => stationItem(n.station, n.km, n.runDuration, n.distance, n.via))
}

/** Применить правку; changed — остановки, у которых сменилась станция или предыдущая остановка. */
async function update(next: RouteStop[], changed: number[]) {
  stops.value = next
  // пустые остановки пересчитывать не с чем: расстояния считаются по заполненным
  if (next.every((s) => s.station)) stops.value = await recalcStops(next, changed)
}

function choose(index: number, value: ChoiceModel) {
  if (typeof value !== 'string') return
  const data = options.value.find((o) => o.id === value)?.data
  const stop: RouteStop = {
    station: value,
    km: typeof data?.km === 'number' ? data.km : 0,
    runDuration: typeof data?.runDuration === 'number' ? data.runDuration : 0,
    stopDuration: stops.value[index]?.stopDuration ?? 0,
  }
  // у следующей остановки сменилась предыдущая — её время в пути тоже по умолчанию
  void update(
    stops.value.map((s, i) => (i === index ? stop : s)),
    [index, index + 1],
  )
}

function setMinutes(index: number, field: 'runDuration' | 'stopDuration', text: string) {
  const value = Number.parseInt(text, 10)
  const minutes = Number.isFinite(value) && value > 0 ? value : 0
  stops.value = stops.value.map((s, i) => (i === index ? { ...s, [field]: minutes } : s))
}

function insert(index: number) {
  const next = [...stops.value]
  next.splice(index + 1, 0, { station: '', km: 0, runDuration: 0, stopDuration: 0 })
  stops.value = next
}

function remove(index: number) {
  // на место удалённой встала следующая остановка: у неё сменилась предыдущая
  void update(
    stops.value.filter((_, i) => i !== index),
    [index],
  )
}
</script>

<style scoped lang="scss">
.stops-editor {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

// номер, станция, км, в пути, стоянка, кнопки — заголовок и строки в одной сетке
.stops-editor__table {
  display: grid;
  grid-template-columns: 1.3rem minmax(10rem, 1fr) 3.5rem 5rem 5rem auto;
  align-items: center;
  gap: 0.5rem;
}

.stops-editor__head {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 500;
}

.stops-editor__right {
  text-align: right;
}

.stops-editor__n {
  color: $zs-text-muted;
  font-size: 0.8125rem;
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
  padding: 0.125rem 0;
}

.stops-editor__option-text {
  display: flex;
  flex-direction: column;
  gap: 0.0625rem;
}

.stops-editor__via {
  color: $zs-text-muted;
  font-size: 0.75rem;
}

.stops-editor__km {
  color: $zs-text-muted;
  font-size: 0.84375rem;
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

.stops-editor__buttons {
  display: flex;
  gap: 0.5rem;
}

.stops-editor__delete,
.stops-editor__insert {
  flex: none;
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
  font: inherit;
  cursor: pointer;

  &:disabled {
    opacity: 0.4;
    cursor: default;
  }
}

.stops-editor__delete {
  width: var(--zs-control-height);
  color: #a12a1b;
}

.stops-editor__insert {
  align-self: flex-start;
  padding: 0 0.625rem;
  color: $primary;
  font-size: 0.84375rem;
  font-weight: 600;
  white-space: nowrap;
}

.stops-editor__muted {
  color: $zs-text-muted;
  font-variant-numeric: tabular-nums;
}

.stops-editor__problem {
  padding-left: 1.8rem;
  color: $zs-text-muted;
  font-size: 0.78125rem;
}
</style>
