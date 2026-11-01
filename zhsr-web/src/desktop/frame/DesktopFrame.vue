<template>
  <div ref="frameEl" class="desktop-frame">
    <div class="desktop-frame__content">
      <header class="desktop-frame__header">
        <button v-if="back" type="button" class="desktop-frame__back" :title="t('Назад')" @click="back">
          <SvgIcon name="arrow-back" size="1.1rem" />
        </button>
        <slot name="header">
          <h1 class="desktop-frame__title">{{ title }}</h1>
        </slot>
      </header>

      <div class="desktop-frame__body">
        <slot />
      </div>
    </div>

    <aside
      v-if="$slots.right && rightOpen"
      class="desktop-frame__right"
      :class="{ 'desktop-frame__right--floating': floating }"
      :style="{ width: rightWidth }"
    >
      <slot name="right" />
    </aside>
  </div>
</template>

<script setup lang="ts">
import { Screen } from 'quasar'
import { onBeforeUnmount, onMounted, ref, useSlots, watch, watchEffect } from 'vue'

import { t } from '@/boot/i18n'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { MENU_WIDTH, menuState } from '../menu/menuState'

/**
 * Фрейм страницы десктопной части с оформлением по макету.
 *
 * Слоты:
 * - header — строка заголовка; без слота показывается title. В слоте заголовок и кнопки страницы, например
 *   «Ввести вагон»: класс desktop-frame__title даёт заголовку оформление по макету, а кнопки прижимаются вправо
 *   классом desktop-frame__actions;
 * - default — тело панели: фильтры, таблица;
 * - right — правая панель, например карточка выбранного вагона; без слота панели нет.
 *
 * back — возврат со страницы (например, из редактора маршрута в список): фрейм показывает кнопку «назад» перед
 * заголовком, и Escape делает то же самое. Escape не возвращает, пока открыто выпадающее окно или диалог: тогда он
 * закрывает их (они сами останавливают Escape или ещё видны в DOM в момент нажатия).
 *
 * Правая панель показывается, пока rightOpen (когда — решает useSidePanel). Стоит ли она рядом со списком или
 * плавает поверх него (с тенью, как в макете), фрейм решает по свободному месту: панель стоит рядом, если после неё
 * списку остаётся не меньше minContentWidth. Ширина фрейма отслеживается, поэтому решение пересматривается при
 * любой перестройке: изменили окно, развернули или свернули левое меню. Решение уходит владельцу через
 * v-model:floating — от него зависит, показывать ли пустую панель (см. useSidePanel).
 *
 * Место для панели сначала освобождается за счёт левого меню: если при развёрнутом меню панели рядом со списком не
 * хватает места, фрейм просит меню свернуться (menuState.panelNeedsSpace), и панель плавает, только если места нет и
 * при свёрнутом меню. Нужно ли сворачивать меню, считается по ширине окна при развёрнутом меню, а не по текущей ширине
 * фрейма: иначе свернувшееся меню освобождало бы место, фрейм просил бы развернуть его обратно, и меню раскачивалось
 * бы. Если пользователь сам развернул меню, его выбор сильнее: панель плавает.
 */
const props = withDefaults(
  defineProps<{
    title?: string
    rightWidth?: string
    rightOpen?: boolean
    minContentWidth?: string
    back?: (() => void) | null
  }>(),
  {
    title: '',
    back: null,
    rightWidth: '22.4rem',
    rightOpen: true,
    minContentWidth: '40rem',
  },
)

const floating = defineModel<boolean>('floating', { default: false })

const frameEl = ref<HTMLElement | null>(null)
const frameWidth = ref(0)

/** Длина CSS (rem, em, px) в пикселях — для сравнения с измеренной шириной фрейма. */
function toPx(length: string): number {
  const value = parseFloat(length)
  if (length.endsWith('px')) return value
  const base = length.endsWith('rem') ? document.documentElement : frameEl.value
  return value * parseFloat(getComputedStyle(base ?? document.documentElement).fontSize)
}

function decide() {
  const next = frameWidth.value < toPx(props.rightWidth) + toPx(props.minContentWidth)
  if (next !== floating.value) floating.value = next
}

let observer: ResizeObserver | null = null

onMounted(() => {
  observer = new ResizeObserver((entries) => {
    frameWidth.value = entries[0]?.contentRect.width ?? 0
  })
  if (frameEl.value) observer.observe(frameEl.value)
})

onBeforeUnmount(() => observer?.disconnect())

watch([frameWidth, () => props.rightWidth, () => props.minContentWidth], decide)

// просьба свернуть меню: при развёрнутом меню панели рядом со списком не хватило бы места
const slots = useSlots()
watchEffect(() => {
  const need = toPx(props.rightWidth) + toPx(props.minContentWidth)
  menuState.panelNeedsSpace = !!slots.right && Screen.width - toPx(MENU_WIDTH) < need
})

onBeforeUnmount(() => (menuState.panelNeedsSpace = false))

// Escape — «назад», если он задан и Escape не нужен открытому меню, выпадающему окну или диалогу
function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || !props.back || e.defaultPrevented) return
  if (document.querySelector('.q-menu, .q-dialog')) return
  props.back()
}

window.addEventListener('keydown', onKeydown)
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped lang="scss">
.desktop-frame {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
}

.desktop-frame__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 0.5rem 1.125rem 1rem;
  overflow-y: auto;
}

.desktop-frame__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.875rem;

  :deep(.desktop-frame__actions) {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-left: auto;
  }
}

.desktop-frame__back {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  // до заголовка — 0.75rem, а не общий промежуток шапки
  margin-right: -0.25rem;
  padding: 0;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text;
  cursor: pointer;

  &:hover {
    background: $zs-bg-button-soft;
  }
}

.desktop-frame__header :deep(.desktop-frame__title),
.desktop-frame__title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.015em;
}

.desktop-frame__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.desktop-frame__right {
  display: flex;
  flex: none;
  flex-direction: column;
  overflow-y: auto;
  border-left: 0.0625rem solid $zs-border;
  background: $zs-bg-panel;

  // узкое окно: панель поверх списка, у правого края, во всю высоту фрейма
  &--floating {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    max-width: calc(100% - 2rem);
    box-shadow: -0.5rem 0 1.9rem rgba(0, 0, 0, 0.14);
  }
}
</style>
