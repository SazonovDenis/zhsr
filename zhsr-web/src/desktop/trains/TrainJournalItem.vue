<template>
  <div
    class="journal-item q-py-sm q-pl-md q-pr-xs"
    :class="{ 'journal-item--selected': selected, 'journal-item--plan': plan }"
    role="button"
    tabindex="0"
    @click="emit('select')"
    @keydown.enter.prevent="emit('select')"
    @contextmenu.prevent="hasMenu && emit('menu', $event)"
  >
    <!-- строка «сейчас»: как запись, время идёт само, выделена только жирным -->
    <template v-if="!change">
      <span class="journal-item__time journal-item__now">{{
        wrap(formatDateTime(new Date(nowMinute).toISOString()))
      }}</span>
      <span class="journal-item__now">{{ t('Текущее состояние') }}</span>
    </template>
    <template v-else>
      <span class="journal-item__time">{{ hideTime ? '' : wrap(formatDateTime(change.dt)) }}</span>
      <span class="journal-item__main">
        <span class="journal-item__what">
          <ChangeTitle :change="change" />
        </span>
        <!-- отклонение от плана — сразу под видом и вагоном -->
        <span v-if="deviation" class="journal-item__muted">{{ deviation }}</span>
        <span>{{ t('Станция: {station}', { station: dictManager.getValue('Station', change.station) }) }}</span>
        <!-- рейс — своей строкой под станцией: номер мягкой ссылкой (выбрать рейс) и дата; клик по ссылке не выбирает
             запись -->
        <span v-if="change.trip">
          {{ t('Рейс:') }}
          <span @click.stop
            ><SoftLink text="{n}" :params="{ n: change.trip.trainNumber }" @click="emit('trip', change.trip.id)"
          /></span>
          {{ t('от {date}', { date: formatDateTime(change.trip.plannedDt) }) }}
        </span>
        <span v-if="change.overdue" class="journal-item__overdue">{{ t('план просрочен') }}</span>
        <span v-if="change.comment" class="journal-item__muted">{{ change.comment }}</span>
      </span>
    </template>
    <button
      v-if="hasMenu"
      type="button"
      class="journal-item__menu q-pa-none"
      :title="t('Действия')"
      :aria-label="t('Действия')"
      @click.stop="emit('menu', $event)"
    >
      <q-icon name="more_vert" size="1.2rem" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { nowMinute } from '@/shared/since'
import ChangeTitle from '@/shared/trains/ChangeTitle.vue'
import { deviationText, isWaiting } from '@/shared/trains/trainJournal'
import SoftLink from '@/shared/components/form/SoftLink.vue'

/**
 * Запись журнала поезда: время, «Прицепка вагона 12345 на 3» или «Отцепка вагона 12345» (слово вида — зелёным или
 * красным, остальное — обычным цветом), под ней отклонение от плана серым, затем где («Станция: Астана», в рейсе ниже
 * строкой — «Рейс: 655/656 от 22.12 12:45», номер — мягкой ссылкой, событие trip), просрочка, основания серым. Без
 * записи (change null) — строка «Текущее состояние» с текущим временем, которое идёт само. hideTime — время то же, что
 * у записи выше: не показывается. Клик выбирает запись, три точки и правая кнопка — меню действий. Справочник Station
 * готовит страница.
 */
const props = defineProps<{
  change: TrainChange | null
  selected: boolean
  hasMenu?: boolean
  hideTime?: boolean
}>()
const emit = defineEmits<{ select: []; menu: [e: MouseEvent]; trip: [id: number] }>()

const plan = computed(() => !!props.change && isWaiting(props.change))
const deviation = computed(() => (props.change ? deviationText(props.change) : ''))

/** Время в колонке «Время» переносится: между датой и временем обычный пробел вместо неразрывного. */
function wrap(text: string): string {
  return text.replace('\u00a0', ' ')
}
</script>

<style scoped lang="scss">
.journal-item {
  display: grid;
  grid-template-columns: 2.8rem minmax(0, 1fr) auto;
  gap: 0.6rem;
  align-items: start;
  border-top: 0.0625rem solid $zs-border-row;
  border-left: 0.2rem solid transparent;
  font-size: 0.8rem;
  cursor: pointer;

  &:hover {
    background: $zs-row-hover-bg;
  }

  &--selected {
    border-left-color: $zs-row-selected-bar;
    background: $zs-row-selected-bg;
  }
}

.journal-item__time {
  color: $zs-text-muted;
}

.journal-item__now {
  font-weight: 700;
}

.journal-item__main {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.journal-item__what {
  font-weight: 600;
}

.journal-item__muted {
  color: $zs-text-muted;
}

.journal-item__overdue {
  color: $zs-error-fg;
  font-weight: 600;
}

.journal-item__menu {
  border: 0;
  background: none;
  color: $zs-text-muted;
  cursor: pointer;
  opacity: 0.6;

  &:hover {
    opacity: 1;
  }
}
</style>
