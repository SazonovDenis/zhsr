<template>
  <div class="bottom-bar">
    <!-- над кнопками — пояснение или ошибка, например «Нарушений: 2» -->
    <slot />
    <div class="bottom-bar__row">
      <q-btn
        v-for="a in left"
        :key="a.key"
        unelevated
        no-caps
        :outline="(a.kind ?? 'secondary') === 'secondary'"
        :color="a.kind === 'primary' ? 'primary' : a.kind === 'danger' ? 'danger' : 'ink'"
        class="zs-pwa-btn zs-pwa-btn--lg"
        :class="{ 'zs-soft-border': (a.kind ?? 'secondary') === 'secondary', 'bottom-bar__btn--stretch': stretched(a) }"
        :disable="a.disabled || a.loading"
        :loading="a.loading"
        :label="t(a.title)"
        @click="emit('action', a.key)"
      />
      <span class="bottom-bar__gap" />
      <q-btn
        v-for="a in right"
        :key="a.key"
        unelevated
        no-caps
        :outline="(a.kind ?? 'secondary') === 'secondary'"
        :color="a.kind === 'primary' ? 'primary' : a.kind === 'danger' ? 'danger' : 'ink'"
        class="zs-pwa-btn zs-pwa-btn--lg"
        :class="{ 'zs-soft-border': (a.kind ?? 'secondary') === 'secondary', 'bottom-bar__btn--stretch': stretched(a) }"
        :disable="a.disabled || a.loading"
        :loading="a.loading"
        :label="t(a.title)"
        @click="emit('action', a.key)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'

/** Кнопка нижней панели. */
export interface BottomBarAction {
  key: string
  /** текст на русском; переводится при показе */
  title: string
  /** основная — синяя, растягивается на свободное место; второстепенная — белая; опасная — красная, тоже растягивается */
  kind?: 'primary' | 'secondary' | 'danger'
  /** слева или справа; по умолчанию справа */
  align?: 'left' | 'right'
  disabled?: boolean
  /** идёт действие: спиннер вместо текста, кнопка недоступна */
  loading?: boolean
  /** растянуть на свободное место; по умолчанию — основная и опасная */
  stretch?: boolean
}

/**
 * Нижняя панель кнопок мобильной части — одна для мастеров (подача заявки, начало чек-листа и описи, заполнение) и
 * листов снизу (BottomSheet). Вызывающий передаёт только состав кнопок, их вид и сторону; вид кнопок — здесь.
 * Основная и опасная кнопки растягиваются на свободное место, если не задано stretch: false. Нажатие — событие action с
 * ключом кнопки. Слот default — строка над кнопками.
 */
const props = defineProps<{ actions: BottomBarAction[] }>()

const emit = defineEmits<{ action: [key: string] }>()

function stretched(a: BottomBarAction): boolean {
  return a.stretch ?? (a.kind === 'primary' || a.kind === 'danger')
}

const left = computed(() => props.actions.filter((a) => a.align === 'left'))
const right = computed(() => props.actions.filter((a) => a.align !== 'left'))
</script>

<style scoped lang="scss">
.bottom-bar {
  display: flex;
  // во всю ширину и там, где родитель — строка (кнопки листа снизу): иначе панель сжималась по кнопкам, и правая
  // кнопка не доходила до края
  flex: 1;
  flex-direction: column;
  width: 100%;
  gap: 0.5rem;
}

.bottom-bar__row {
  display: flex;
  gap: 0.6rem;
}

// свободное место между левыми и правыми кнопками; основная кнопка забирает его сама
.bottom-bar__gap {
  flex: 1;

  .bottom-bar__row:has(.bottom-bar__btn--stretch) & {
    display: none;
  }
}

// кнопки — q-btn размера zs-pwa-btn--lg; здесь только растяжка в ряду
.bottom-bar__btn--stretch {
  flex: 1;
}
</style>
