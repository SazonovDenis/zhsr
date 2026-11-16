<template>
  <button type="button" class="queued-refusal q-pa-none" @click="open = true">
    <StatusBadge tone="bad">{{ t('Не принято') }}</StatusBadge>
  </button>

  <BottomSheet v-model:show="open" :title="t('Не принято')">
    <div class="queued-refusal__error">{{ t(item.error) }}</div>
    <div>{{ t('Сервер не примет эту запись, повторять отправку бесполезно') }}</div>
    <template #buttons>
      <q-btn
        unelevated
        outline
        no-caps
        color="ink"
        class="queued-refusal__btn zs-pwa-btn zs-soft-border"
        :label="t('Закрыть')"
        @click="open = false"
      />
      <q-btn
        unelevated
        outline
        no-caps
        color="ink"
        class="queued-refusal__btn zs-pwa-btn zs-soft-border"
        :label="t(CANCEL[item.kind])"
        @click="cancel"
      />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { t } from '@/boot/i18n'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import type { OutboxKind } from '@/shared/db/db'
import type { OutboxItem } from '@/shared/offline/outbox'
import { removeQueued } from '@/shared/offline/outbox'
import BottomSheet from './BottomSheet.vue'

/** Надпись «Не принято» у записи очереди, которой сервер отказал: по нажатию лист с текстом отказа и кнопкой отмены. */
const props = defineProps<{ item: OutboxItem }>()

const CANCEL: Record<OutboxKind, string> = {
  request: 'Отменить заявку',
  edit: 'Отменить правку',
  note: 'Отменить примечание',
  photo: 'Отменить фото',
  action: 'Убрать действие',
  assign: 'Убрать назначение',
}

const open = ref(false)

async function cancel() {
  open.value = false
  await removeQueued(props.item)
}
</script>

<style scoped lang="scss">
// кнопка без своего вида: видна плашка
.queued-refusal {
  border: 0;
  background: none;
  font: inherit;
  cursor: pointer;
}

.queued-refusal__error {
  color: $zs-error-fg;
  font-weight: 600;
}

// кнопки — q-btn размера zs-pwa-btn; здесь только раскладка в ряду
.queued-refusal__btn {
  flex: 1;
}
</style>
