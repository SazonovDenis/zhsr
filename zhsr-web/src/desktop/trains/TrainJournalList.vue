<template>
  <section class="train-journal">
    <!-- подпись — как подпись состава справа -->
    <div class="train-journal__head">
      <span class="train-journal__title">{{ t('Журнал отцепок и прицепок') }}</span>
    </div>
    <!-- прошлое — факты: последние или за период; настоящее — текущее состояние и ожидающие планы -->
    <div class="train-journal__modes">
      <CbSegment v-model="side" single required :values="SIDES" @change="emit('select', null)" />
      <template v-if="side === 'past'">
        <CbSegment v-model="pastMode" single required :values="PAST_MODES" />
        <CbSegment
          v-if="pastMode === 'period'"
          v-model="period"
          single
          required
          :values="PERIODS"
          :max-button-count="4"
        />
      </template>
    </div>

    <div class="train-journal__box">
      <!-- заголовки колонок — как у таблиц; колонки — как у записи журнала -->
      <div class="train-journal__cols q-py-sm q-pl-md q-pr-xs" role="row">
        <span role="columnheader">{{ t('Время') }}</span>
        <span role="columnheader">{{ t('Событие') }}</span>
      </div>

      <div ref="scrollEl" class="train-journal__list">
        <!-- настоящее: «Текущее состояние» сверху, под ним планы -->
        <JournalItem
          v-if="side === 'future'"
          :change="null"
          :selected="selected === null"
          @select="emit('select', null)"
        />
        <div v-if="shown.length === 0" class="train-journal__empty q-px-md q-py-sm">
          {{ side === 'past' ? t('Фактов нет') : t('Планов нет') }}
        </div>
        <JournalItem
          v-for="(c, i) in shown"
          :key="c.id"
          :change="c"
          :hide-time="i > 0 && shown[i - 1]!.dt === c.dt"
          :selected="selected === c.id"
          :has-menu="actionsOf(c).length > 0"
          @select="emit('select', c.id)"
          @menu="openMenu($event, c)"
        />
        <!-- прошлое: факты по времени, «Текущее состояние» внизу -->
        <JournalItem
          v-if="side === 'past'"
          :change="null"
          :selected="selected === null"
          @select="emit('select', null)"
        />
      </div>
    </div>
    <RowMenu ref="menuEl" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import RowMenu from '@/shared/components/table/RowMenu.vue'
import { changeActions, journalOf } from '@/shared/trains/trainJournal'
import type { JournalSide } from './journalSide'
import JournalItem from './TrainJournalItem.vue'

/**
 * Журнал поезда (Ф-ПОЕ-02): над ним подпись «Журнал отцепок и прицепок» и переключатель «Настоящее» / «Прошлое». Прошлое
 * — факты по времени и внизу строка «Текущее состояние»; факты — последние для вагона (у каждого вагона — его
 * последний факт) или за период: неделя, месяц, 3 месяца, год. Время, равное времени записи выше, не повторяется. Настоящее — сверху строка «Текущее состояние», под ней ожидающие планы по времени. В рамке —
 * заголовки колонок «Время» и «Событие». Выбор записи — момент, на который справа показан состав: у факта — по факту,
 * у плана — ожидаемый; «Текущее состояние» — текущий состав. Смена прошлого и будущего выбирает «Текущее состояние».
 * У записи — меню действий: «Отметить» и «Отменить план» у плана, «Удалить факт» у факта; недоступные — с причиной.
 * Список прокручен к строке «Текущее состояние». Журнал поезда сервер отдаёт целиком: период отбирает факты здесь.
 *
 * @prop card     поезд
 * @prop selected выбранная запись (train_wagon_change.id); null — «Текущее состояние»
 * @model side    настоящее или прошлое
 */
const props = defineProps<{ card: TrainCard; selected: number | null; disable?: boolean }>()
const side = defineModel<JournalSide>('side', { required: true })
const emit = defineEmits<{
  select: [id: number | null]
  mark: [change: TrainChange]
  cancel: [change: TrainChange]
  undo: [change: TrainChange]
}>()

const SIDES: ChoiceItem[] = [
  { id: 'future', text: 'Настоящее' },
  { id: 'past', text: 'Прошлое' },
]
const PAST_MODES: ChoiceItem[] = [
  { id: 'last', text: 'Последние для вагона' },
  { id: 'period', text: 'За период' },
]
/** Периоды «За период»: сколько дней назад от сейчас. */
const PERIOD_DAYS: Record<string, number> = { week: 7, month: 31, quarter: 92, year: 366 }
const PERIODS: ChoiceItem[] = [
  { id: 'week', text: 'Неделя' },
  { id: 'month', text: 'Месяц' },
  { id: 'quarter', text: '3 месяца' },
  { id: 'year', text: 'Год' },
]

const pastMode = ref<string>('last')
const period = ref<string>('week')

const journal = computed(() => journalOf(props.card))
/** Показанные записи: прошлое — факты последние или за период, настоящее — текущее состояние и ожидающие планы. */
const shown = computed<TrainChange[]>(() => {
  if (side.value === 'future') return journal.value.plans
  const facts = journal.value.facts
  if (pastMode.value === 'last') {
    // у каждого вагона — его последний факт; порядок по времени сохраняется
    const last = new Map(facts.map((c) => [c.wagon.id, c.id]))
    return facts.filter((c) => last.get(c.wagon.id) === c.id)
  }
  const from = Date.now() - (PERIOD_DAYS[period.value] ?? 7) * 86_400_000
  return facts.filter((c) => new Date(c.dt).getTime() >= from)
})

const scrollEl = useTemplateRef<HTMLElement>('scrollEl')
const menuEl = useTemplateRef<InstanceType<typeof RowMenu>>('menuEl')

const actionsOf = (c: TrainChange) =>
  changeActions(c, props.card.changes, {
    mark: (x) => emit('mark', x),
    cancel: (x) => emit('cancel', x),
    undo: (x) => emit('undo', x),
  })

function openMenu(e: MouseEvent, c: TrainChange) {
  const items = actionsOf(c).map((a) => ({
    label: a.label,
    reason: a.reason || (props.disable ? t('Идёт другое действие') : ''),
    separator: false,
    run: a.run,
  }))
  void menuEl.value?.show(items, e.clientX, e.clientY, true)
}

/** К строке «Текущее состояние»: в прошлом она внизу, в будущем — вверху. */
async function scrollToNow() {
  await nextTick()
  const list = scrollEl.value
  if (list) list.scrollTop = side.value === 'past' ? list.scrollHeight : 0
}
onMounted(scrollToNow)
watch([side, pastMode, period], scrollToNow)
</script>

<style scoped lang="scss">
.train-journal {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-height: 0;
}

// как подпись состава справа (TrainMomentView)
.train-journal__head {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.6rem;
}

.train-journal__title {
  font-size: 0.9rem;
  font-weight: 600;
}

.train-journal__modes {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.train-journal__box {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

// заголовки колонок — как у DataTable; колонки и отступы — как у записи (TrainJournalItem, с полосой выбора слева)
.train-journal__cols {
  display: grid;
  flex: none;
  grid-template-columns: 5.6rem minmax(0, 1fr) 1.2rem;
  gap: 0.6rem;
  border-left: 0.2rem solid transparent;
  background: $zs-table-head-bg;
  box-shadow: inset 0 -0.0625rem 0 $zs-table-head-border;
  color: $zs-table-head-fg;
  font-size: 0.8rem;
  font-weight: 600;
  white-space: nowrap;
}

.train-journal__list {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.train-journal__empty {
  color: $zs-text-faint;
  font-size: 0.8rem;
}
</style>
