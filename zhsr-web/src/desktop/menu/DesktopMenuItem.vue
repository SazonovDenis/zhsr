<template>
  <button
    type="button"
    class="menu-item"
    :class="{
      'menu-item--active': active,
      'menu-item--indent': item.sub || indent,
      'menu-item--collapsed': collapsed,
    }"
    :title="t(item.label)"
  >
    <span class="menu-item__body">
      <!-- вложенный пункт в развёрнутом меню — без иконки, с отступом -->
      <SvgIcon v-if="item.icon && !indent && (!item.sub || collapsed)" :name="item.icon" />
      <!-- в свёрнутом меню у пункта без иконки — первые две буквы названия -->
      <span v-else-if="collapsed && !item.icon" class="menu-item__short">{{ t(item.label).slice(0, 2) }}</span>
      <span v-if="!collapsed" class="menu-item__label">{{ t(item.label) }}</span>
    </span>
    <!-- счётчик: в развёрнутом меню — справа от названия, в свёрнутом — в углу иконки -->
    <span v-if="count" class="menu-item__count">{{ count }}</span>
  </button>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import type { MenuItem } from './menu'

/**
 * indent — пункт группы: с отступом, как вложенный.
 * count — счётчик пункта (например, сколько актов ждут приёма); 0 — счётчика нет.
 */
defineProps<{ item: MenuItem; active: boolean; collapsed: boolean; indent?: boolean; count?: number }>()
</script>

<style scoped lang="scss">
.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  height: 2.4rem;
  padding: 0 0.75rem;
  border: 0;
  border-radius: 0.25rem;
  background: transparent;
  color: $zs-menu-fg;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 500;
  text-align: left;
  cursor: pointer;

  &--active {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
  }

  &--indent {
    height: 2rem;
    padding-left: 2.4375rem;
    font-size: 0.84375rem;
    font-weight: 400;
  }

  &--collapsed {
    justify-content: center;
    padding-left: 0.75rem;
  }
}

.menu-item__body {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  min-width: 0;
}

.menu-item__count {
  flex: none;
  color: #9fb4c8;
  font-size: 0.75rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;

  // свёрнутое меню: в правом верхнем углу пункта, правее иконки, а не поверх неё
  .menu-item--collapsed & {
    position: absolute;
    top: 0.1rem;
    right: 0.2rem;
  }
}

.menu-item__short {
  font-size: 0.8125rem;
  font-weight: 400;
}

.menu-item__label {
  white-space: nowrap;
}
</style>
