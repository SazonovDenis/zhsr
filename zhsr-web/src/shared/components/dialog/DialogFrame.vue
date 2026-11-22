<template>
  <!-- Esc закрывает диалог только через onKeydown: открытый выпадающий список останавливает Esc у себя и закрывается
       сам, не закрывая диалог (штатное закрытие Quasar по Esc это не учитывает — оно отключено) -->
  <q-dialog
    :model-value="show"
    :maximized="isMaximized"
    no-esc-dismiss
    @update:model-value="onUpdateShow"
    @show="focusFirst"
  >
    <div
      ref="cardEl"
      class="dialog-frame"
      :class="{ 'dialog-frame--maximized': isMaximized }"
      :style="isMaximized ? undefined : { width, height: dialogHeight(height) }"
      @keydown.stop="onKeydown"
    >
      <DialogHeader :title="title" @close="close" />

      <div class="dialog-frame__body q-py-md q-px-lg">
        <slot />
      </div>

      <slot name="actions-above" />

      <!-- предупреждения — полосой над ошибкой и кнопками, как ошибка, но без крестика: убирает их владелец -->
      <div v-if="$slots.warnings" class="dialog-frame__warnings q-py-sm q-px-lg" role="status">
        <slot name="warnings" />
      </div>

      <!-- ошибка — полосой над кнопками, в своём месте: тело диалога уступает ей высоту -->
      <ErrorBox
        v-if="error"
        :error="error"
        closable
        class="dialog-frame__error q-py-sm q-px-lg"
        @close="emit('errorClose')"
      />

      <DialogActions v-if="actions.length > 0" :actions="actions" :action-running="actionRunning" @action="onAction">
        <template #left>
          <slot name="actions-left" />
          <!-- общий спиннер диалога: идёт загрузка данных формы -->
          <q-spinner v-if="busy" color="primary" size="1.5rem" class="dialog-frame__busy" />
          <div v-if="actionsStateText" class="dialog-frame__state">{{ actionsStateText }}</div>
        </template>
      </DialogActions>
    </div>
  </q-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import ErrorBox from '@/shared/components/ErrorBox.vue'
import { isMobile } from '@/shared/device'
import type { ShownError } from '@/shared/errors'
import type { HistoryDialog } from './dialogHistory'
import { dialogHistory } from './dialogHistory'
import type { DialogAction, DialogHeightValue } from './types'
import { dialogHeight } from './types'
import DialogActions from './DialogActions.vue'
import DialogHeader from './DialogHeader.vue'

/**
 * Диалог с оформлением по макету: заголовок с крестиком, тело (слот default) и кнопки действий в подвале.
 *
 *   <DialogFrame v-model:show="open" :title="t('Ввод вагона')" :actions="actions" @action="onAction">...</DialogFrame>
 *
 * - Показ управляется v-model:show: диалог сам себя не закрывает, а отправляет update:show — закрывает владелец.
 * - Escape закрывает, Enter выполняет основное действие (default) — и когда фокус потерян (ушёл на body): клавиши
 *   тогда получает верхний открытый диалог (onWindowKeydown, isTopDialog). Tab ходит по кругу по элементам с классом
 *   zs-focusable внутри диалога. При открытии фокус встаёт на первый такой элемент — первый контрол формы. Класс
 *   zs-focusable ставят сами контролы: TextInput, DatePicker, CbList, CbSegment, CbButton, кнопки действий.
 * - На телефоне диалог развёрнут на весь экран, а кнопка «назад» закрывает его (см. dialogHistory).
 * - Высота постоянная (height, DialogHeight): от высоты экрана в пределах min и max, содержимое её не меняет — лишнее
 *   прокручивается в теле.
 * - Ошибка (error) — полосой во всю ширину над кнопками, с крестиком (событие errorClose): тело уступает ей высоту.
 * - Предупреждения (слот warnings) — такой же полосой над ошибкой, на фоне предупреждения, без крестика; длинный
 *   список прокручивается.
 * - Escape закрывает диалог и тогда, когда фокус ушёл на сам диалог Quasar вне карточки (щелчок по месту без
 *   контрола).
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
    /** постоянная высота диалога, если он не развёрнут: готовая (DialogHeight) или своя — значение CSS */
    height?: DialogHeightValue
    actions?: DialogAction[]
    /** текст слева от кнопок, например почему основное действие недоступно */
    actionsStateText?: string
    /** имя действия, которое сейчас выполняется */
    actionRunning?: string | null
    /** идёт загрузка данных диалога: спиннер в подвале */
    busy?: boolean
    /** ошибка полосой над кнопками; null — нет */
    error?: ShownError | null
  }>(),
  {
    show: false,
    title: '',
    maximized: null,
    width: '32rem',
    height: 'md',
    actions: () => [],
    actionsStateText: '',
    actionRunning: null,
    busy: false,
    error: null,
  },
)

const emit = defineEmits<{ 'update:show': [show: boolean]; action: [name: string]; errorClose: [] }>()

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

/** Клавиши, которые обрабатывает диалог. */
const DIALOG_KEYS = ['Escape', 'Enter', 'Tab']

/**
 * Клавиши при потерянном фокусе. Элемент с фокусом исчез из страницы (например, закрытая нажатием ошибка) — фокус
 * уходит на body, и клавиши диалога (keydown на его карточке) до него не доходят: Escape закрыл бы панель страницы, а не
 * диалог. Поэтому верхний открытый диалог ловит клавиши на уровне окна, пока фокус на body: возвращает фокус в себя и
 * обрабатывает клавишу как свою — Escape закрывает его, Enter выполняет основное действие, Tab ведёт по его элементам.
 * Другие клавиши не трогает. Фокус в выпадающем меню или в другом месте страницы — не трогает.
 */
function onWindowKeydown(e: KeyboardEvent) {
  // только клавиши диалога: остальные (Ctrl+C у выделенного текста и другие) идут своим путём — перенос фокуса снял бы
  // выделение до копирования
  if (!DIALOG_KEYS.includes(e.key)) return
  if (!props.show || !isTopDialog()) return
  if (!isLostFocus(e.target)) return
  e.stopPropagation()
  if (e.key !== 'Escape') focusFirst()
  onKeydown(e)
}

/**
 * Фокус потерян для диалога: на body или на обёртке диалога Quasar вне карточки — туда фокус уходит после щелчка по
 * месту без контрола (строка таблицы, подпись), и keydown карточки его не получает.
 */
function isLostFocus(target: EventTarget | null): boolean {
  if (target === document.body || target === document.documentElement) return true
  if (!(target instanceof Node) || !cardEl.value || cardEl.value.contains(target)) return false
  const dialogs = document.querySelectorAll('.q-dialog')
  return !!dialogs[dialogs.length - 1]?.contains(target)
}

/**
 * Диалог — верхний: последний из открытых диалогов Quasar на странице (каждый открытый диалог — свой узел в конце body,
 * по порядку открытия). Так поверх диалога может быть и не DialogFrame — например, просмотр фото: тогда клавиши его.
 */
function isTopDialog(): boolean {
  const dialogs = document.querySelectorAll('.q-dialog')
  const top = dialogs[dialogs.length - 1]
  return !!top && !!cardEl.value && top.contains(cardEl.value)
}

// захват: раньше обработчиков страницы (панель, «назад» фрейма)
window.addEventListener('keydown', onWindowKeydown, true)

/**
 * Фокус на первый контрол — по событию show диалога: оно приходит и для диалога, созданного уже показанным
 * (openDialog), и после того, как Quasar поставил фокус по-своему.
 */
function focusFirst() {
  focusables()[0]?.focus()
}

onBeforeUnmount(() => {
  dialogHistory.forget(historyEntry)
  window.removeEventListener('keydown', onWindowKeydown, true)
})

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
  border-radius: 0.3rem;
  background: #fff;
  box-shadow: 0 1.5rem 3.8rem rgba(0, 0, 0, 0.2);

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
  overflow-y: auto;
}

.dialog-frame__error {
  flex: none;
  max-height: 40vh;
  overflow-y: auto;
  border-radius: 0;
  font-size: 0.8rem;
}

.dialog-frame__warnings {
  flex: none;
  max-height: 30vh;
  overflow-y: auto;
  background: $zs-warning-bg;
  font-size: 0.8rem;
}

.dialog-frame__busy {
  flex: none;
}

.dialog-frame__state {
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
