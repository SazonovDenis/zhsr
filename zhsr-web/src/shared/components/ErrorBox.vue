<template>
  <div class="error-box q-pa-md" role="alert">
    <div class="error-box__body">
      <div>{{ error.text }}</div>
      <div v-if="error.request" class="error-box__request q-mt-xs">{{ error.request }}</div>
    </div>
    <!-- повтор есть — справа кнопка «повторить»; повтор — только по ней, не по всей ошибке -->
    <button
      v-if="retry"
      type="button"
      tabindex="-1"
      class="error-box__reload q-ml-md q-pa-xs"
      :title="t('Повторить')"
      @mousedown.prevent
      @click.stop="retry()"
    >
      <q-icon name="refresh" size="1.3rem" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'

/**
 * Ошибка загрузки на месте списка или карточки: текст и бледной второй строкой — запрос, который упал. retry — повтор
 * загрузки: его запускает кнопка справа.
 */
withDefaults(defineProps<{ error: ShownError; retry?: (() => void) | null }>(), { retry: null })
</script>

<style scoped lang="scss">
.error-box {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.9rem;
}

.error-box__body {
  flex: 1;
  min-width: 0;
}

.error-box__reload {
  display: flex;
  flex: none;
  align-items: center;
  border: 0;
  border-radius: 50%;
  background: none;
  color: $zs-text-muted;
  cursor: pointer;

  &:hover {
    background: rgba(0, 0, 0, 0.06);
  }
}

.error-box__request {
  color: $zs-error-fg;
  font-family: $zs-font-mono;
  font-size: 0.8rem;
  opacity: 0.65;
  word-break: break-all;
}
</style>
