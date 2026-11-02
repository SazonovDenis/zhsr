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
/**
 * Лист снизу экрана поверх затемнения — ввод комментария к действию, пояснение, выбор вагона. Клик по затемнению
 * закрывает лист. v-model:show — показ.
 */
withDefaults(defineProps<{ title: string; subtitle?: string }>(), { subtitle: '' })

const show = defineModel<boolean>('show', { default: false })
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
  gap: 0.75rem;
  width: 100%;
  max-height: 85dvh;
  padding: 1.25rem 1rem calc(1.375rem + env(safe-area-inset-bottom));
  border-radius: 1.25rem 1.25rem 0 0;
  background: #fff;
  color: $zs-text;
  font-size: 0.9375rem;
}

.bottom-sheet__titles {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.bottom-sheet__title {
  font-size: 1.1875rem;
  font-weight: 700;
}

.bottom-sheet__subtitle {
  color: $zs-text-muted;
  font-size: 0.875rem;
}

.bottom-sheet__body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 0;
  overflow-y: auto;
}

.bottom-sheet__buttons {
  display: flex;
  gap: 0.625rem;
}
</style>
