<template>
  <div class="cell-trip-readiness">
    <span
      v-for="item in items"
      :key="item.key"
      class="cell-trip-readiness__chip q-py-none q-px-sm"
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
import { formatWagons } from '@/shared/format'

/**
 * Готовность рейса к отправлению: пункты цветными чипами — выполнен (зелёный) или не выполнен (красный, с причиной и
 * числом вагонов); разное состояние — разными плашками (например, «Нет ПЭМ» и «Назначен ЛНП»), одинаковое — одной.
 * Данные пунктов присылает сервер (TripReadiness) у рейсов в статусе «План» и «Выполняется», тексты строит ячейка: у
 * выполняющегося рейса пункта о том, где вагоны, нет. У рейсов в других статусах ячейка пустая.
 */
const props = defineProps<CellProps<TripRow>>()

interface Item {
  key: string
  ok: boolean
  text: string
}

const items = computed<Item[]>(() => {
  const r = props.row.readiness
  if (!r) return []
  const inTrip = props.row.tripStatusType === TripStatus.IN_PROGRESS
  const res: Item[] = []
  const add = (key: string, ok: boolean, text: string) => res.push({ key, ok, text })

  // поезд и планы до отправления: не назначенный поезд — красной плашкой, назначенный — без плашки; не выполненные и
  // выполненные планы — своими плашками
  if (!r.train) add('train', false, t('Поезд не назначен'))
  else {
    if (r.pending.length)
      add('pending', false, t('Не выполнены планы: {wagons}', { wagons: formatWagons(r.pending.length) }))
    if (r.done.length) add('done', true, t('Выполнены планы: {wagons}', { wagons: formatWagons(r.done.length) }))
  }

  // где вагоны — только до отправления: на станции отправления
  if (!inTrip) {
    if (r.riding === 0) add('wagons', false, t('В поезде нет вагонов'))
    else if (r.away.length)
      add('wagons', false, t('Не на станции отправления: {wagons}', { wagons: formatWagons(r.away.length) }))
    else add('wagons', true, t('Вагоны на станции'))
  }

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
