<template>
  <Teleport to="body">
    <div
      v-if="open"
      ref="menuEl"
      class="row-menu q-py-xs"
      role="menu"
      :style="{ left: `${pos.x}px`, top: `${pos.y}px` }"
      @keydown.esc.prevent="close"
    >
      <template v-for="(item, i) in items" :key="i">
        <div v-if="item.separator && i > 0" class="row-menu__separator" role="separator" />
        <button
          type="button"
          class="row-menu__item q-py-xs q-px-md"
          role="menuitem"
          :disabled="!!item.reason"
          @click="pick(item)"
        >
          <span>{{ t(item.label) }}</span>
          <span v-if="item.reason" class="row-menu__reason q-pl-sm">{{ item.reason }}</span>
        </button>
      </template>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'

import { t } from '@/boot/i18n'

/** Пункт открытого меню: видимый, с причиной недоступности (пусто — доступен) и действием. */
export interface ShownMenuItem {
  label: string
  reason: string
  separator: boolean
  run: () => void
}

/**
 * Всплывающее меню строки таблицы (DataTable): открывается у точки экрана — под тремя точками строки или у курсора
 * по правой кнопке. Закрывается выбором пункта, щелчком мимо, Escape, прокруткой и сменой размера окна. Недоступный
 * пункт неактивен, причина — под ним.
 */
const open = ref(false)
const items = ref<ShownMenuItem[]>([])
const pos = ref({ x: 0, y: 0 })
const menuEl = ref<HTMLElement | null>(null)

/**
 * Открыть меню у точки экрана — координаты события мыши; alignRight — меню правым краем к точке. Меню не выходит за
 * край окна.
 */
async function show(list: ShownMenuItem[], x: number, y: number, alignRight = false) {
  items.value = list
  pos.value = { x, y }
  open.value = true
  listen(true)
  await nextTick()
  const el = menuEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  pos.value = {
    x: Math.max(0, Math.min(alignRight ? x - rect.width : x, window.innerWidth - rect.width)),
    y: y + rect.height > window.innerHeight ? Math.max(0, y - rect.height) : y,
  }
  el.querySelector<HTMLButtonElement>('.row-menu__item:not(:disabled)')?.focus()
}

function close() {
  open.value = false
  listen(false)
}

function pick(item: ShownMenuItem) {
  if (item.reason) return
  close()
  item.run()
}

function onOutside(e: Event) {
  if (menuEl.value && e.target instanceof Node && menuEl.value.contains(e.target)) return
  close()
}

function listen(on: boolean) {
  const method = on ? 'addEventListener' : 'removeEventListener'
  document[method]('mousedown', onOutside, true)
  window[method]('scroll', close, true)
  window[method]('resize', close)
  window[method]('blur', close)
}

onBeforeUnmount(() => listen(false))

defineExpose({ show, close })
</script>

<style scoped lang="scss">
.row-menu {
  position: fixed;
  z-index: 6000;
  min-width: 12rem;
  max-width: 22rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
  box-shadow: 0 0.5rem 1.5rem rgba(0, 0, 0, 0.14);
  font-size: 0.8rem;
}

.row-menu__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  border: 0;
  background: none;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  // подсветка под курсором — и у недоступных пунктов: видно, к какому пункту относится пояснение
  &:hover,
  &:focus-visible {
    background: $zs-row-hover-bg;
    outline: none;
  }

  // Quasar делает любой [disabled] полупрозрачным (0.6) — у меню без этого: недоступный пункт серый ($zs-text-muted),
  // пояснение под ним — цветом текста
  &:disabled {
    color: $zs-text-muted;
    cursor: default;
    opacity: 1 !important;
  }
}

.row-menu__reason {
  color: $zs-text;
  font-size: 0.7rem;
  font-style: italic;
}

.row-menu__separator {
  margin: 0.2rem 0;
  border-top: 0.0625rem solid $zs-border-row;
}
</style>
