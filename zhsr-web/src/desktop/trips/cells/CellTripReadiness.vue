<template>
  <div class="cell-trip-readiness">
    <span
      v-for="item in items"
      :key="item.key"
      class="cell-trip-readiness__chip q-py-none q-px-sm"
      :title="item.title"
      :class="item.ok ? 'cell-trip-readiness__chip--ok' : 'cell-trip-readiness__chip--bad'"
    >
      {{ item.text }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TripRow } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { TripStatus } from '@/shared/constants/systemDicts'
import { WagonProblemType } from '@/shared/constants/WagonProblemType'
import { formatWagons } from '@/shared/format'
import { wagonProblemText } from '@/shared/trains/wagonProblemText'

/**
 * Готовность рейса к отправлению: пункты цветными чипами — выполнен (зелёный) или не выполнен (красный, с причиной и
 * числом вагонов); разное состояние — разными плашками (например, «Нет ПЭМ» и «Назначен ЛНП»), одинаковое — одной.
 * Данные пунктов присылает сервер (TripReadiness) у рейсов в статусе «План» и «Выполняется», тексты строит ячейка: у
 * выполняющегося рейса пункта о том, где вагоны, нет, из неготовностей вагонов — только занятость в другом поезде. У
 * рейсов в других статусах ячейка пустая. Справочники Station, Depot, Yard и LocationKind готовит владелец таблицы.
 */
const props = defineProps<CellProps<TripRow>>()

const PROBLEM_CHIPS = [
  [WagonProblemType.STATION, 'На другой станции: {wagons}'],
  [WagonProblemType.YARD, 'Нужна передача на станцию: {wagons}'],
  [WagonProblemType.BUSY, 'В другом поезде: {wagons}'],
] as const

interface Item {
  key: string
  ok: boolean
  text: string
  /** подсказка плашки: неготовности вагонов построчно */
  title?: string | undefined
}

const items = computed<Item[]>(() => {
  const r = props.row.readiness
  if (!r) return []
  const inTrip = props.row.tripStatusType === TripStatus.IN_PROGRESS
  const res: Item[] = []
  const add = (key: string, ok: boolean, text: string, title?: string) => res.push({ key, ok, text, title })

  // поезд и планы до отправления: не назначенный поезд — красной плашкой, назначенный — без плашки; не выполненные и
  // выполненные планы — своими плашками
  if (!r.train) add('train', false, t('Поезд не назначен'))
  else {
    if (r.pending.length)
      add('pending', false, t('Не выполнены планы: {wagons}', { wagons: formatWagons(r.pending.length) }))
    if (r.done.length) add('done', true, t('Выполнены планы: {wagons}', { wagons: formatWagons(r.done.length) }))
  }

  if (!inTrip && r.riding === 0) add('wagons', false, t('В поезде нет вагонов'))
  // неготовность вагонов тремя видами: плашка с числом вагонов, подробности — в подсказке
  for (const [type, label] of PROBLEM_CHIPS) {
    const list = r.problems.filter((p) => p.wagonProblemType === type)
    if (!list.length) continue
    const count = new Set(list.map((p) => p.wagon.id)).size
    add(`problem${type}`, false, t(label, { wagons: formatWagons(count) }), list.map(wagonProblemText).join('\n'))
  }
  // расположение проверяется только до отправления
  if (!inTrip && r.riding > 0 && !r.problems.length) add('wagons', true, t('Вагоны на станции'))

  // ПЭМ и ЛНП: одинаковое состояние — одной плашкой, разное — двумя плашками своего цвета
  if (r.pem && r.lnp) add('chief', true, t('Назначены ПЭМ и ЛНП'))
  else if (!r.pem && !r.lnp) add('chief', false, t('Нет ПЭМ и ЛНП'))
  else {
    add('pem', r.pem, r.pem ? t('Назначен ПЭМ') : t('Нет ПЭМ'))
    add('lnp', r.lnp, r.lnp ? t('Назначен ЛНП') : t('Нет ЛНП'))
  }

  // без вагонов проводников не за кем закрепить: норма не выполнена
  if (r.riding === 0) add('norm', false, t('Проводники не назначены'))
  else if (r.normNotOk.length)
    add('norm', false, t('Проводники не по норме: {wagons}', { wagons: formatWagons(r.normNotOk.length) }))
  else add('norm', true, t('Проводники по норме'))

  // экипировка: вагоны без описей и недостача по последним описям едущих вагонов; без вагонов пункта нет
  if (r.riding > 0) {
    if (r.equipNoList.length)
      add('equipNoList', false, t('Опись не проводилась: {wagons}', { wagons: formatWagons(r.equipNoList.length) }))
    if (r.equipShortage) add('equip', false, t('Недостача по {n} позициям', r.equipShortage))
    else if (!r.equipNoList.length) add('equip', true, t('Вагон экипирован'))
  }
  return res
})
</script>

<style scoped lang="scss">
.cell-trip-readiness {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.cell-trip-readiness__chip {
  border-radius: 62.5rem;
  font-size: 0.8rem;
  line-height: 1.35;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }

  &--bad {
    background: #f7dcd8;
    color: $zs-error-fg;
  }
}
</style>
