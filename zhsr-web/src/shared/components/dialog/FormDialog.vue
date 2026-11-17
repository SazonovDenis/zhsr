<template>
  <DialogFrame
    v-model:show="show"
    :title="t(title)"
    :width="width"
    :actions="actions"
    :action-running="running ? 'ok' : null"
    :busy="busy.active.value"
    @action="onAction"
  >
    <template #actions-left><slot name="actions-left" /></template>
    <!-- двойной клик по варианту с data-dialog-pick — выбор и основное действие; любое действие в полях (нажатие,
         ввод) убирает ошибку -->
    <div class="form-dialog__fields" @dblclick="onDblclick" @pointerdown="dismiss" @keydown="dismiss" @input="dismiss">
      <slot />
    </div>
    <!-- ошибка с крестиком: крестик (на телефоне и нажатие на неё) — убрать -->
    <ErrorBox v-if="shown" :error="shown" closable class="form-dialog__error" @close="dismiss" />
    <div v-if="hint" class="form-dialog__hint">{{ t(hint) }}</div>
  </DialogFrame>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { t } from '@/boot/i18n'
import { useBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import type { DialogAction } from './types'
import DialogFrame from './DialogFrame.vue'

/**
 * Общий предок диалогов ввода («Ввести вагон», «Создать рейс», «Новый маршрут»): поля формы (слот default), текст
 * ошибки, подсказка и кнопки: «Отмена» слева, справа — дополнительные (extraActions) и основного действия.
 *
 * Слот actions-left — кнопки слева на панели диалога, например «Удалить запись».
 *
 * Ошибка (error) — с крестиком в правом верхнем углу; крестик (на телефоне и нажатие на неё) или любое действие в
 * полях формы её убирают, новая ошибка показывается снова.
 *
 * Основное действие отправляет событие ok; что проверить и какой запрос отправить, решает конкретный диалог. Пока
 * running = true, кнопка основного действия крутит спиннер, а повторно действие не запускается.
 *
 * Двойной клик по элементу с атрибутом data-dialog-pick — то же, что нажать кнопку основного действия: первый клик
 * уже выбрал вариант. Атрибут ставится у вариантов списка, если список — единственное поле формы (выбор исполнителя).
 *
 * Диалог-наследник открывается через openDialog: у него v-model:show, а результат он отдаёт событием done.
 *
 *   <FormDialog v-model:show="show" title="Ввод вагона" ok-text="Ввести вагон" :running="running"
 *               :error="error" @ok="save">...поля...</FormDialog>
 */
const props = withDefaults(
  defineProps<{
    /** заголовок на русском; переводится при показе */
    title: string
    /** текст кнопки основного действия на русском */
    okText?: string
    /** текст кнопки «Отмена» на русском, например «Не отменять» у отмены заявки */
    cancelText?: string
    width?: string
    running?: boolean
    /** ошибка над кнопками: проверки формы (formError) или запроса (shownError, запрос второй строкой); null — нет */
    error?: ShownError | null
    /** пояснение под полями на русском, например «Рейс создаётся в статусе «План»» */
    hint?: string
    /** дополнительные кнопки справа — перед кнопкой основного действия, например «Назначить себя» */
    extraActions?: DialogAction[]
  }>(),
  {
    okText: 'Сохранить',
    cancelText: 'Отмена',
    width: '35rem',
    running: false,
    error: null,
    hint: '',
    extraActions: () => [],
  },
)

const emit = defineEmits<{ ok: [] }>()

const show = defineModel<boolean>('show', { default: false })

// ошибку убрал пользователь (крестик, нажатие на неё, действие в полях); новая ошибка показывается снова
const dismissed = ref(false)
watch(
  () => props.error,
  () => (dismissed.value = false),
)
const shown = computed(() => (dismissed.value ? null : props.error))

function dismiss() {
  if (props.error) dismissed.value = true
}

// ожидание диалога (provideBusy в диалоге-наследнике): спиннер в подвале — на всю цепочку загрузок формы
const busy = useBusy()

const actions = computed<DialogAction[]>(() => [
  { title: props.cancelText, left: true },
  ...props.extraActions,
  { name: 'ok', title: props.okText, default: true },
])

function onAction(name: string) {
  if (name === 'ok') emit('ok')
}

function onDblclick(e: MouseEvent) {
  if (props.running || !(e.target as HTMLElement).closest('[data-dialog-pick]')) return
  emit('ok')
}
</script>

<style scoped lang="scss">
.form-dialog__fields {
  // обёртка только ловит двойной клик: поля раскладываются, как будто её нет
  display: contents;
}

.form-dialog__error {
  font-size: 0.8rem;
}

.form-dialog__hint {
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: 1.45;
}
</style>
