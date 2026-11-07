<template>
  <button type="button" class="zs-badge queued-refusal" @click="open = true">{{ t('Не принято') }}</button>

  <BottomSheet v-model:show="open" :title="t('Не принято')">
    <div class="queued-refusal__error">{{ t(item.error) }}</div>
    <div>{{ t('Сервер не примет эту запись, повторять отправку бесполезно') }}</div>
    <template #buttons>
      <button type="button" class="queued-refusal__btn q-py-none q-px-md" @click="open = false">
        {{ t('Закрыть') }}
      </button>
      <button type="button" class="queued-refusal__btn q-py-none q-px-md" @click="cancel">
        {{ t(CANCEL[item.kind]) }}
      </button>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { t } from '@/boot/i18n'
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
}

const open = ref(false)

async function cancel() {
  open.value = false
  await removeQueued(props.item)
}
</script>

<style scoped lang="scss">
.queued-refusal {
  border: 0;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font: inherit;
  cursor: pointer;
}

.queued-refusal__error {
  color: $zs-error-fg;
  font-weight: 600;
}

.queued-refusal__btn {
  flex: 1;
  height: 2.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  cursor: pointer;
}
</style>
