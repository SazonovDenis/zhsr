<template>
  <div class="error-box" role="alert">
    <div class="error-box__body">
      <div>{{ error.text }}</div>
      <div v-if="error.request" class="error-box__request">{{ error.request }}</div>
    </div>
    <!-- повтор есть — справа кнопка «повторить»; повтор — только по ней, не по всей ошибке -->
    <button
      v-if="retry"
      type="button"
      tabindex="-1"
      class="error-box__reload"
      :title="t('Повторить')"
      @mousedown.prevent
      @click.stop="retry()"
    >
      <q-icon name="refresh" size="1.25rem" />
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
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 0.25rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.875rem;
}

.error-box__body {
  flex: 1;
  min-width: 0;
}

.error-box__reload {
  display: flex;
  flex: none;
  align-items: center;
  margin-left: 1rem;
  padding: 0.25rem;
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
  margin-top: 0.25rem;
  color: $zs-error-fg;
  font-family: $zs-font-mono;
  font-size: 0.75rem;
  opacity: 0.65;
  word-break: break-all;
}
</style>
