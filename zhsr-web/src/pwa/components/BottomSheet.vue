<template>
  <Teleport to="body">
    <div v-if="show" class="bottom-sheet" @click.self="show = false">
      <div class="bottom-sheet__panel">
        <div class="bottom-sheet__titles">
          <div class="bottom-sheet__title">{{ title }}</div>
          <div v-if="subtitle" class="bottom-sheet__subtitle">{{ subtitle }}</div>
        </div>
        <div class="bottom-sheet__body">
          <slot />
        </div>
        <div v-if="$slots.buttons" class="bottom-sheet__buttons">
          <slot name="buttons" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

import type { HistoryDialog } from '@/shared/components/dialog/dialogHistory'
import { dialogHistory } from '@/shared/components/dialog/dialogHistory'

/**
 * Лист снизу экрана поверх затемнения — ввод комментария к действию, примечание, выбор вагона. Клик по затемнению
 * закрывает лист. v-model:show — показ. Кнопка «назад» на телефоне закрывает лист, а не уводит со страницы
 * (dialogHistory).
 */
withDefaults(defineProps<{ title: string; subtitle?: string }>(), { subtitle: '' })

const show = defineModel<boolean>('show', { default: false })

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
.bottom-sheet {
  position: fixed;
  inset: 0;
  z-index: 6000;
  display: flex;
  align-items: flex-end;
  background: rgba(15, 18, 22, 0.45);
}

.bottom-sheet__panel {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  width: 100%;
  // браузер без dvh пропустил бы ограничение, и высокий лист ушёл бы верхом за экран вместе с заголовком
  max-height: 85vh;
  max-height: 85dvh;
  padding: 1.3rem 1rem calc(1.4rem + env(safe-area-inset-bottom));
  border-radius: 1.3rem 1.3rem 0 0;
  background: #fff;
  color: $zs-text;
  font-size: 0.9rem;
}

.bottom-sheet__titles {
  display: flex;
  // заголовок и кнопки не сжимаются: прокручивается только тело
  flex: none;
  flex-direction: column;
  gap: 0.1rem;
}

.bottom-sheet__title {
  font-size: 1.2rem;
  font-weight: 700;
}

.bottom-sheet__subtitle {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.bottom-sheet__body {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-height: 0;
  overflow-y: auto;
}

.bottom-sheet__buttons {
  display: flex;
  flex: none;
  gap: 0.6rem;
}
</style>
