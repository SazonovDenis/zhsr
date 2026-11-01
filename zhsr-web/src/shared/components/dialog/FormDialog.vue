<template>
  <DialogFrame
    v-model:show="show"
    :title="t(title)"
    :width="width"
    :actions="actions"
    :action-running="running ? 'ok' : null"
    @action="onAction"
  >
    <slot />
    <div v-if="error" class="form-dialog__error">{{ error }}</div>
    <div v-if="hint" class="form-dialog__hint">{{ t(hint) }}</div>
  </DialogFrame>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'
import type { DialogAction } from './types'
import DialogFrame from './DialogFrame.vue'

/**
 * Общий предок диалогов ввода («Ввести вагон», «Создать рейс», «Новый маршрут»): поля формы (слот default), текст
 * ошибки, подсказка и кнопки «Отмена» и основного действия.
 *
 * Основное действие отправляет событие ok; что проверить и какой запрос отправить, решает конкретный диалог. Пока
 * running = true, кнопка основного действия крутит спиннер, а повторно действие не запускается.
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
    width?: string
    running?: boolean
    /** ошибка проверки или ответа сервера; показывается над кнопками */
    error?: string
    /** пояснение под полями на русском, например «Рейс создаётся в статусе «План»» */
    hint?: string
  }>(),
  { okText: 'Сохранить', width: '35rem', running: false, error: '', hint: '' },
)

const emit = defineEmits<{ ok: [] }>()

const show = defineModel<boolean>('show', { default: false })

const actions = computed<DialogAction[]>(() => [
  { title: 'Отмена' },
  { name: 'ok', title: props.okText, default: true },
])

function onAction(name: string) {
  if (name === 'ok') emit('ok')
}
</script>

<style scoped lang="scss">
.form-dialog__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe5e1;
  color: #8f2416;
  font-size: 0.8125rem;
}

.form-dialog__hint {
  color: $zs-text-muted;
  font-size: 0.8125rem;
  line-height: 1.45;
}
</style>
