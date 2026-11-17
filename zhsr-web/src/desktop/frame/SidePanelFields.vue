<template>
  <div class="side-panel-fields">
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * Поля карточки в правой панели: две колонки «подпись — значение»; колонка подписей — по ширине самой длинной
 * подписи. Внутри — пары элементов подряд:
 *
 *   <SidePanelFields>
 *     <span>{{ t('Депо приписки') }}</span><span>{{ depot }}</span>
 *   </SidePanelFields>
 */
</script>

<style scoped lang="scss">
.side-panel-fields {
  display: grid;
  // колонка подписей — по самой длинной подписи: подписи не переносятся, значениям — всё остальное место
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 0.5rem 1rem;
  font-size: 0.8rem;

  // подписи — нечётные элементы
  > :nth-child(odd) {
    color: $zs-text-muted;
  }
}
</style>
