<template>
  <!-- строки вагонов: слева номер и под ним тип, справа расположение и под ним плашка (WagonOption) -->
  <div class="wagon-list">
    <button
      v-for="w in items"
      :key="w.key"
      type="button"
      class="wagon-list__wagon q-py-sm q-px-md"
      :class="{ 'wagon-list__wagon--on': selected.includes(w.key), 'wagon-list__wagon--off': w.disabled }"
      :disabled="w.disabled"
      :aria-pressed="multiple ? selected.includes(w.key) : undefined"
      @click="emit('pick', w.key)"
    >
      <WagonOption :number="w.number" :type="w.type" :place="w.place ?? ''" :badge="w.badge ?? ''" />
    </button>
    <!-- под строками: «показаны первые», спиннер, ошибка, «не найдены» — у вызывающего -->
    <slot />
  </div>
</template>

<script setup lang="ts">
import WagonOption from '@/shared/components/WagonOption.vue'

/** Строка списка вагонов. */
export interface WagonListItem {
  /** ключ строки и выбора */
  key: string
  number: string
  /** тип вагона — текстом */
  type: string
  /** где вагон — текстом; пусто — не показывается */
  place?: string
  /** плашка справа; пусто — нет */
  badge?: string
  /** выбрать нельзя: строка бледная */
  disabled?: boolean
}

/**
 * Список вагонов на телефоне — один для выбора вагона заявки, чек-листа и описи (WagonModePicker) и для прицепки и
 * отцепки в рейсе. Выбранная строка залита; мультивыбор (multiple) — залито может быть несколько, нажатие отмечает и
 * снимает. Выбор — у вызывающего: событие pick с ключом строки. Прокручивается сам список.
 *
 * @prop items    строки
 * @prop selected ключи выбранных строк
 * @prop multiple мультивыбор
 */
withDefaults(defineProps<{ items: WagonListItem[]; selected: string[]; multiple?: boolean }>(), { multiple: false })

const emit = defineEmits<{ pick: [key: string] }>()
</script>

<style scoped lang="scss">
.wagon-list {
  display: flex;
  flex: 0 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;

  // строки списка не сжимаются по высоте: прокручивается список
  > * {
    flex: none;
  }
}

.wagon-list__wagon {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 3.3rem;
  border: 0;
  border-top: 0.0625rem solid $zs-border-row;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:first-child {
    border-top: 0;
  }

  // выбранный вагон — фоном выбранной кнопки CbButton
  &--on {
    background: $zs-selected-bg;
  }

  &--off {
    cursor: default;
    opacity: 0.55;
  }
}
</style>
