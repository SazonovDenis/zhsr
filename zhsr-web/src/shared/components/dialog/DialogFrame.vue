<template>
  <q-dialog :model-value="show" :maximized="isMaximized" @update:model-value="onUpdateShow" @show="focusFirst">
    <div
      ref="cardEl"
      class="dialog-frame"
      :class="{ 'dialog-frame--maximized': isMaximized }"
      :style="isMaximized ? undefined : { width }"
      @keydown.stop="onKeydown"
    >
      <DialogHeader :title="title" @close="close" />

      <div class="dialog-frame__body">
        <slot />
      </div>

      <slot name="actions-above" />

      <DialogActions v-if="actions.length > 0" :actions="actions" :action-running="actionRunning" @action="onAction">
        <template #left>
          <div v-if="actionsStateText" class="dialog-frame__state">{{ actionsStateText }}</div>
        </template>
      </DialogActions>
    </div>
  </q-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { isMobile } from '@/shared/device'
import type { HistoryDialog } from './dialogHistory'
import { dialogHistory } from './dialogHistory'
import type { DialogAction } from './types'
import DialogActions from './DialogActions.vue'
import DialogHeader from './DialogHeader.vue'

/**
 * Диалог с оформлением по макету: заголовок с крестиком, тело (слот default) и кнопки действий в подвале.
 *
 *   <DialogFrame v-model:show="open" :title="t('Ввод вагона')" :actions="actions" @action="onAction">...</DialogFrame>
 *
 * - Показ управляется v-model:show: диалог сам себя не закрывает, а отправляет update:show — закрывает владелец.
 * - Escape закрывает, Enter выполняет основное действие (default), Tab ходит по кругу по элементам с классом
 *   zs-focusable внутри диалога. При открытии фокус встаёт на первый такой элемент — первый контрол формы. Класс
 *   zs-focusable ставят сами контролы: TextInput, DatePicker, CbList, CbSegment, CbButton, кнопки действий.
 * - На телефоне диалог развёрнут на весь экран, а кнопка «назад» закрывает его (см. dialogHistory).
 * - Пока выполняется действие actionRunning, диалог новых действий не принимает: повторный клик отправил бы второй
 *   такой же запрос. Ставит и снимает actionRunning владелец — только он знает, когда действие закончилось.
 *
 * Открыть диалог из кода, без v-if в шаблоне, — см. openDialog.
 */
const props = withDefaults(
  defineProps<{
    show?: boolean
    title?: string
    /** развернуть на весь экран; по умолчанию — только на телефоне */
    maximized?: boolean | null
    /** ширина диалога, если он не развёрнут */
    width?: string
    actions?: DialogAction[]
    /** текст слева от кнопок, например почему основное действие недоступно */
    actionsStateText?: string
    /** имя действия, которое сейчас выполняется */
    actionRunning?: string | null
  }>(),
  {
    show: false,
    title: '',
    maximized: null,
    width: '32rem',
    actions: () => [],
    actionsStateText: '',
    actionRunning: null,
  },
)

const emit = defineEmits<{ 'update:show': [show: boolean]; action: [name: string] }>()

const cardEl = ref<HTMLElement | null>(null)

const isMaximized = computed(() => props.maximized ?? isMobile.value)

const historyEntry: HistoryDialog = { isShown: () => props.show, close }

// кнопка «назад» на телефоне закрывает диалог; на десктопе «назад» — переход по истории, диалоги её не трогают.
// immediate: диалог из openDialog создаётся уже показанным
watch(
  () => props.show,
  (show) => {
    if (show) {
      if (isMobile.value) dialogHistory.push(historyEntry)
    } else {
      dialogHistory.pop(historyEntry)
    }
  },
  { immediate: true },
)

/**
 * Фокус на первый контрол — по событию show диалога: оно приходит и для диалога, созданного уже показанным
 * (openDialog), и после того, как Quasar поставил фокус по-своему.
 */
function focusFirst() {
  focusables()[0]?.focus()
}

onBeforeUnmount(() => dialogHistory.forget(historyEntry))

/** Элементы, по которым ходит Tab: с классом zs-focusable, видимые, доступные и не исключённые из Tab (tabindex -1). */
function focusables(): HTMLElement[] {
  return Array.from(cardEl.value?.querySelectorAll<HTMLElement>('.zs-focusable') ?? []).filter(
    (el) => !el.matches(':disabled') && el.tabIndex >= 0 && el.getClientRects().length > 0,
  )
}

function onUpdateShow(show: boolean) {
  emit('update:show', show)
}

function close() {
  emit('update:show', false)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    close()
  } else if (e.key === 'Enter') {
    const action = props.actions.find((a) => a.default && !a.disabled)
    if (action) onAction(action)
  } else if (e.key === 'Tab') {
    // фокус по Tab ходит по кругу внутри диалога
    const items = focusables()
    const first = items[0]
    const last = items[items.length - 1]
    if (!first || !last) return
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault()
      first.focus()
    }
  }
}

function onAction(action: DialogAction) {
  if (props.actionRunning) return

  if (action.exec) action.exec(action)
  else if (action.name) emit('action', action.name)
  else close()
}
</script>

<style scoped lang="scss">
.dialog-frame {
  display: flex;
  flex-direction: column;
  max-width: 100%;
  border-radius: 0.25rem;
  background: #fff;
  box-shadow: 0 1.5rem 3.75rem rgba(0, 0, 0, 0.2);

  &--maximized {
    width: 100%;
    height: 100%;
    border-radius: 0;
  }
}

.dialog-frame__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1rem;
  min-height: 0;
  padding: 1.125rem 1.375rem;
  overflow-y: auto;
}

.dialog-frame__state {
  color: #8f2416;
  font-size: 0.8125rem;
}
</style>
