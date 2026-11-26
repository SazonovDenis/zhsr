<template>
  <ul class="cell-wagon-readiness q-my-none q-pl-none">
    <li v-for="(p, i) in problems" :key="i" class="cell-wagon-readiness__item">
      <div>
        <ReadinessChip :ok="false">{{ t(PROBLEM_TITLES[p.wagonProblemType]!) }}</ReadinessChip>
      </div>
      <div class="cell-wagon-readiness__detail">{{ detail(p) }}</div>
      <div v-if="p.wagonProblemType === WagonProblemType.YARD" @click.stop>
        <SoftLink
          text="Оформить передачу"
          :disabled="!!transfer.reason(row)"
          :title="transfer.reason(row) || undefined"
          @click="transfer.open(row)"
        />
      </div>
    </li>
    <li v-if="normIssues.length" class="cell-wagon-readiness__item">
      <div>
        <ReadinessChip :ok="false">{{ t('Проводники не по норме') }}</ReadinessChip>
      </div>
      <div v-for="issue in normIssues" :key="issue" class="cell-wagon-readiness__detail">{{ issue }}</div>
      <div @click.stop>
        <SoftLink
          text="Назначить"
          :disabled="!!conductors.reason()"
          :title="conductors.reason() || undefined"
          @click="conductors.assign(row)"
        />
      </div>
    </li>
    <li v-if="lists && lists.length === 0" class="cell-wagon-readiness__item">
      <ReadinessChip :ok="false">{{ t('Опись не проводилась') }}</ReadinessChip>
    </li>
    <li v-for="l in shortages" :key="l.id" class="cell-wagon-readiness__item">
      <div>
        <ReadinessChip :ok="false">{{ t('Недостача по {n} позициям', l.shortageCount) }}</ReadinessChip>
      </div>
      <div @click.stop>
        <SoftLink text="Опись" :title="textOf(l.equipListTemplate)" @click="equip.open(l, row.number)" />
      </div>
    </li>
    <li v-if="ready.length" class="cell-wagon-readiness__item cell-wagon-readiness__ready">
      <ReadinessChip v-for="text in ready" :key="text" ok>{{ t(text) }}</ReadinessChip>
    </li>
  </ul>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

import type { TrainWagon, WagonProblem } from '@/api/trains'
import { t } from '@/boot/i18n'
import { textOf } from '@/shared/checks/texts'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import ReadinessChip from '@/shared/components/ReadinessChip.vue'
import { WagonProblemType } from '@/shared/constants/WagonProblemType'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonProblemDetail } from '@/shared/trains/wagonProblemText'
import type { CellProps } from '@/shared/components/table/types'
import { TRIP_CONDUCTORS, TRIP_EQUIP, TRIP_TRANSFER } from '../tripMoment'

/**
 * Готовность вагона состава к отправлению рейса. Неготовности вагона по планам (расчёт сервера, Ф-ПОЕ-03): «На другой
 * станции», «Нужна передача на станцию» или «В другом поезде» (на каждый поезд своя запись), под названием —
 * подробности; у «На другой станции» — только где окажется вагон. У «Нужна передача на станцию» ссылка «Оформить передачу» — акт передачи на станцию отправления
 * (Ф-АКТ-02); нет права — ссылка неактивна, причина — в подсказке. Проводники: у вагона не выполнена норма — предупреждение, под ним причины
 * (не хватает проводников, в хвостовом нет 2 старших, у проводника несоседние вагоны или больше двух) и ссылка
 * «Назначить» — окно ответственных за вагон; нет права — ссылка неактивна, причина — в подсказке. Экипировка: описей у вагона нет — «Опись не проводилась»; в последней описи
 * по шаблону недостача — «Недостача по N позициям» и ссылка «Опись» на эту опись, шаблон — в подсказке. Пункты разделены небольшим интервалом.
 * Названия неготовностей — красными плашками, как в списке рейсов (ReadinessChip), пояснения и ссылки — под ними.
 * Под неготовностями — выполненные пункты зелёными плашками: «Вагон на станции» (только у рейса в статусе «План»),
 * «Проводники по норме» (у пассажирского вагона), «Вагон экипирован» (описи есть, недостачи нет).
 */
const PROBLEM_TITLES: Record<number, string> = {
  [WagonProblemType.STATION]: 'На другой станции',
  [WagonProblemType.YARD]: 'Нужна передача на станцию',
  [WagonProblemType.BUSY]: 'В другом поезде',
}

const props = defineProps<CellProps<TrainWagon>>()

const transfer = inject(TRIP_TRANSFER)!
const equip = inject(TRIP_EQUIP)!
const conductors = inject(TRIP_CONDUCTORS)!

const problems = computed(() => transfer.problems(props.row))
const norm = computed(() => conductors.norm(props.row))
const normIssues = computed(() => norm.value?.issues ?? [])
const lists = computed(() => equip.lists(props.row.wagon))
const shortages = computed(() => lists.value?.filter((l) => l.shortageCount > 0) ?? [])

/** Выполненные пункты готовности вагона. */
const ready = computed(() => {
  const res: string[] = []
  if (transfer.inPlace(props.row)) res.push('Вагон на станции')
  // норма требуется только пассажирскому вагону
  if (norm.value?.passenger && norm.value.ok) res.push('Проводники по норме')
  // описи ещё не загружены или не проводились — пункта нет
  if (lists.value?.length && !shortages.value.length) res.push('Вагон экипирован')
  return res
})

/** Подробности под названием неготовности. Станция, где нужен вагон, — станция отправления рейса, она не пишется. */
function detail(p: WagonProblem): string {
  if (p.wagonProblemType !== WagonProblemType.STATION) return wagonProblemDetail(p)
  return p.station === null
    ? t('окажется в рейсе')
    : t('окажется на станции {station}', { station: dictManager.getValue('Station', p.station) })
}
</script>

<style scoped lang="scss">
.cell-wagon-readiness {
  list-style: none;
}

.cell-wagon-readiness__item + .cell-wagon-readiness__item {
  margin-top: 0.4rem;
}

.cell-wagon-readiness__ready {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.cell-wagon-readiness__detail {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
