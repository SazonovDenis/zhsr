<template>
  <q-dialog :model-value="show" maximized @update:model-value="show = $event">
    <div class="calendar-dialog">
      <div class="calendar-dialog__body">
        <DateCalendar v-model="model" :with-time="withTime" :nullable="false" :label="label" />
        <slot />
      </div>
      <div class="calendar-dialog__buttons">
        <button type="button" class="calendar-dialog__btn q-py-none q-px-md" @click="show = false">
          {{ t('Отмена') }}
        </button>
        <button
          type="button"
          class="calendar-dialog__btn calendar-dialog__btn--primary q-py-none q-px-md"
          :disabled="!model || running"
          @click="emit('confirm')"
        >
          {{ t(okText) }}
        </button>
      </div>
    </div>
  </q-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

import { t } from '@/boot/i18n'
import type { HistoryDialog } from '@/shared/components/dialog/dialogHistory'
import { dialogHistory } from '@/shared/components/dialog/dialogHistory'
import DateCalendar from '@/shared/components/period/DateCalendar.vue'

/**
 * Выбор даты на телефоне во весь экран: только календарь (DateCalendar) и снизу кнопки «Отмена» и основного действия
 * (событие confirm). Заголовка нет: что выбирается, говорит подпись календаря label. Слот default — под календарём,
 * например ошибка. Кнопка «назад» закрывает окно (dialogHistory).
 */
withDefaults(
  defineProps<{
    label: string
    withTime?: boolean
    okText?: string
    running?: boolean
  }>(),
  { withTime: false, okText: 'Подтвердить', running: false },
)

const emit = defineEmits<{ confirm: [] }>()

const show = defineModel<boolean>('show', { default: false })
const model = defineModel<string | null>({ default: null })

const historyEntry: HistoryDialog = { isShown: () => show.value, close: () => (show.value = false) }

watch(
  show,
  (open) => {
    if (open) dialogHistory.push(historyEntry)
    else dialogHistory.pop(historyEntry)
  },
  { immediate: true },
)

onBeforeUnmount(() => dialogHistory.forget(historyEntry))
</script>

<style scoped lang="scss">
.calendar-dialog {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #fff;
}

.calendar-dialog__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.8rem;
  min-height: 0;
  overflow-y: auto;
}

.calendar-dialog__buttons {
  display: flex;
  flex: none;
  gap: 0.6rem;
  padding: 0.8rem 0.5rem calc(0.8rem + env(safe-area-inset-bottom));
  border-top: 0.0625rem solid $zs-border;
}

.calendar-dialog__btn {
  flex: 1;
  min-height: 3.3rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;

  &--primary {
    border: 0;
    background: $primary;
    color: #fff;
  }

  &:disabled {
    opacity: 0.4;
  }
}
</style>
