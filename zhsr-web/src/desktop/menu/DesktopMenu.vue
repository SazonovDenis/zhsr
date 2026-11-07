<template>
  <!-- клик по свободному месту меню сворачивает и разворачивает его; клики по пунктам до сюда не доходят -->
  <aside class="desktop-menu" :class="{ 'desktop-menu--collapsed': collapsed }" @click="onToggleClick">
    <div class="desktop-menu__brand q-pa-md">
      <SvgIcon name="logo" size="1.6rem" />
      <span v-if="!collapsed" class="desktop-menu__brand-name">{{ t('Жолсерік') }}</span>
    </div>

    <nav class="desktop-menu__list q-py-none q-px-sm">
      <DesktopMenuItem
        v-for="item in menu.items"
        :key="item.id"
        :item="item"
        :active="isActive(item)"
        :collapsed="collapsed"
        :count="counters[item.id] ?? 0"
        @click.stop="select(item)"
      />

      <!-- «Администрирование» — последним в верхней группе; пункты раскрываются вниз, под заголовком группы. Пункт без
           route — раздел пока не сделан: виден, но не выбирается -->
      <div class="desktop-menu__admin">
        <button
          type="button"
          class="desktop-menu__group q-py-none q-px-md"
          :title="t(menu.admin.label)"
          @click.stop="onAdminClick"
        >
          <span class="desktop-menu__item-body">
            <SvgIcon v-if="menu.admin.icon" :name="menu.admin.icon" />
            <span v-if="!collapsed" class="desktop-menu__label">{{ t(menu.admin.label) }}</span>
          </span>
          <SvgIcon
            v-if="!collapsed"
            name="chevron-down"
            size="0.8rem"
            class="desktop-menu__chevron"
            :class="{ 'desktop-menu__chevron--open': adminOpen }"
          />
        </button>
        <template v-if="adminOpen && !collapsed">
          <DesktopMenuItem
            v-for="item in menu.admin.items"
            :key="item.id"
            :item="item"
            indent
            :active="isActive(item)"
            :collapsed="collapsed"
            :count="counters[item.id] ?? 0"
            :disabled="!item.route"
            @click.stop="bus.emit(menuEvent(item))"
          />
        </template>
      </div>
    </nav>

    <div class="desktop-menu__space" />

    <div v-if="user" class="desktop-menu__user q-pa-sm">
      <DesktopUserMenu :user="user" :collapsed="collapsed" />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { useRoute } from 'vue-router'

import type { CurrentUser } from '@/api/session'
import { fetchCurrentState } from '@/api/state'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { isMenuAutoCollapsed } from '@/shared/screen'
import { loadCurrentUser } from '@/shared/user'
import { MENU_COLLAPSED_WIDTH, MENU_WIDTH, menuState } from './menuState'
import DesktopMenuItem from './DesktopMenuItem.vue'
import DesktopUserMenu from './DesktopUserMenu.vue'
import type { MenuItem } from './menu'
import { menu, menuEvent } from './menu'

/**
 * Левое меню десктопной части.
 *
 * Пункты описаны в menu.json. Выбранный пункт отправляет в шину событие `menu-<id>`, а переход выполняет layout.
 * Меню само слушает события `menu-collapse` и `menu-expand`, и клик по свободному месту меню отправляет их же:
 * свернуть и развернуть меню можно из любого места приложения.
 * Пока пользователь не сворачивал меню сам, оно сворачивается само на узком окне (уже 1300px), как в макете.
 */

const route = useRoute()

// Свёрнуто ли меню. Пока пользователь сам не сворачивал и не разворачивал меню (menuState.userCollapsed = null), оно
// сворачивается само: на узком окне (уже 1300px) или когда фрейму с правой панелью не хватает места при развёрнутом
// меню (menuState.panelNeedsSpace, решает DesktopFrame). Выбор пользователя дальше сохраняется
const collapsed = computed(() => menuState.userCollapsed ?? (isMenuAutoCollapsed() || menuState.panelNeedsSpace))
const adminOpen = computed(() => menuState.openGroup === menu.admin.id)
const user = shallowRef<CurrentUser | null>(null)

// счётчики пунктов меню из текущего состояния (fetchCurrentState); загружаются при показе меню, после
// восстановления связи и по событию шины state-refresh — его отправляет тот, кто изменил данные, влияющие на счётчики
const counters = shallowRef<Record<string, number>>({})

// счётчики: ошибку их загрузки осознанно не показываем — пункты меню остаются без чисел
async function loadState() {
  try {
    counters.value = (await fetchCurrentState()).counters
  } catch {
    counters.value = {}
  }
}

const onStateRefresh = () => void loadState()
bus.on('state-refresh', onStateRefresh)
bus.on('connection-restored', onStateRefresh)

onMounted(async () => {
  user.value = await loadCurrentUser()
  await loadState()
})

const onCollapse = () => (menuState.userCollapsed = true)
const onExpand = () => (menuState.userCollapsed = false)
bus.on('menu-collapse', onCollapse)
bus.on('menu-expand', onExpand)
onBeforeUnmount(() => {
  bus.off('menu-collapse', onCollapse)
  bus.off('menu-expand', onExpand)
  bus.off('state-refresh', onStateRefresh)
  bus.off('connection-restored', onStateRefresh)
})

function onToggleClick() {
  bus.emit(collapsed.value ? 'menu-expand' : 'menu-collapse')
}

function select(item: MenuItem) {
  bus.emit(menuEvent(item))
}

// в свёрнутом меню пунктам группы показаться негде: меню разворачивается и сразу показывает их
function onAdminClick() {
  if (collapsed.value) {
    menuState.openGroup = menu.admin.id
    bus.emit('menu-expand')
    return
  }
  menuState.openGroup = adminOpen.value ? null : menu.admin.id
}

function isActive(item: MenuItem): boolean {
  return !!item.route && route.name === item.route
}
</script>

<style scoped lang="scss">
.desktop-menu {
  display: flex;
  flex: none;
  flex-direction: column;
  width: v-bind(MENU_WIDTH);
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--zs-header-bg);
  color: #fff;
  cursor: pointer;
  transition: width 0.15s;

  &--collapsed {
    width: v-bind(MENU_COLLAPSED_WIDTH);
  }
}

.desktop-menu__brand {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.desktop-menu__brand-name {
  font-size: 1.1rem;
  font-weight: 700;
  letter-spacing: 0;
  white-space: nowrap;
}

.desktop-menu__list {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.desktop-menu__admin {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.desktop-menu__space {
  flex: 1;
}

.desktop-menu__group {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  height: 2.4rem;
  border: 0;
  border-radius: 0.3rem;
  background: transparent;
  // как соседние пункты меню (DesktopMenuItem)
  color: $zs-menu-fg;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 500;
  text-align: left;
  cursor: pointer;

  .desktop-menu--collapsed & {
    justify-content: center;
  }
}

.desktop-menu__item-body {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

.desktop-menu__label {
  white-space: nowrap;
}

.desktop-menu__user {
  border-top: 0.0625rem solid rgba(255, 255, 255, 0.15);
}

// группа раскрывается вниз: свёрнутая — шеврон вниз, раскрытая — вверх
.desktop-menu__chevron {
  transition: transform 0.15s;

  &--open {
    transform: rotate(180deg);
  }
}
</style>
