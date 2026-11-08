<template>
  <!-- клики здесь не сворачивают левое меню: до aside они не доходят -->
  <div class="user-block q-pa-sm" :class="{ 'user-block--collapsed': collapsed }" @click.stop>
    <!-- блок внизу панели: подменю раскрывается вверх, над пользователем -->
    <div v-if="open" class="user-block__submenu">
      <button
        v-for="option in LOCALES"
        :key="option.value"
        type="button"
        class="user-block__sub-item q-py-none q-pr-md q-pl-lg"
        :class="{ 'user-block__sub-item--active': option.value === locale }"
        :title="option.label"
        @click="choose(option.value)"
      >
        {{ collapsed ? option.short : option.label }}
      </button>
      <div class="user-block__separator q-my-xs q-mr-md q-ml-lg" />
      <button
        type="button"
        class="user-block__sub-item q-py-none q-pr-md q-pl-lg"
        :title="t('Сменить пароль')"
        @click="onChangePassword"
      >
        <q-icon name="lock" size="1.1rem" />
        <span v-if="!collapsed">{{ t('Сменить пароль') }}</span>
      </button>
      <button
        type="button"
        class="user-block__sub-item q-py-none q-pr-md q-pl-lg"
        :title="isOnline ? t('Выход') : t('Для выхода нужна связь')"
        :disabled="!isOnline"
        @click="onLogout"
      >
        <q-icon name="logout" size="1.1rem" />
        <span v-if="!collapsed">{{ t('Выход') }}</span>
      </button>
      <div class="user-block__separator user-block__separator--wide q-my-xs q-mr-md q-ml-lg" />
    </div>

    <!-- клик по пользователю разворачивает подменю прямо в панели: язык, смена пароля и выход -->
    <button
      type="button"
      class="user-block__user q-py-xs q-px-sm"
      :class="{ 'user-block__user--open': open }"
      :title="collapsed ? `${user.name}, ${roles}` : undefined"
      @click="toggle"
    >
      <SvgIcon name="user" class="user-block__icon" />
      <span v-if="!collapsed" class="user-block__text">
        <span class="user-block__name">{{ user.name }}</span>
        <span class="user-block__role">{{ roles }}</span>
      </span>

      <SvgIcon v-if="!collapsed" name="chevron-down" size="0.8rem" class="user-block__chevron q-ml-auto" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import type { CurrentUser } from '@/api/session'
import ChangePasswordDialog from '@/auth/ChangePasswordDialog.vue'
import type { MessageLanguages } from '@/boot/i18n'
import { i18n, t } from '@/boot/i18n'
import { openDialog } from '@/shared/components/dialog/openDialog'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { LOCALES, setLocale } from '@/shared/locale'
import { isOnline } from '@/shared/network'
import { logoutUser } from '@/shared/user'
import { menuState, USER_MENU_GROUP } from './menuState'

/**
 * Текущий пользователь внизу левого меню: иконка, фамилия и роль. Клик по пользователю разворачивает подменю прямо
 * в панели, как у «Администрирования»: языки, смена пароля и выход. Выход завершает сеанс на сервере и переводит на
 * страницу входа. В свёрнутом меню — иконка пользователя (подсказка — фамилия и роль); подменю разворачивается так же,
 * с кодами языков и иконками смены пароля и выхода.
 */
const props = defineProps<{ user: CurrentUser; collapsed: boolean }>()

// роли совмещаются: все роли пользователя через запятую
const roles = computed(() => props.user.roles.map((r) => dictManager.getValue('Role', r)).join(', '))

const router = useRouter()

const locale = computed(() => i18n.global.locale.value)

// подменю пользователя развёрнуто: оно одна из групп меню (menuState.openGroup)
const open = computed(() => menuState.openGroup === USER_MENU_GROUP)

function toggle() {
  menuState.openGroup = open.value ? null : USER_MENU_GROUP
}

function choose(value: MessageLanguages) {
  setLocale(value)
  menuState.openGroup = null
}

async function onLogout() {
  if (await logoutUser()) await router.push({ name: 'login' })
}

function onChangePassword() {
  menuState.openGroup = null
  void openDialog(ChangePasswordDialog)
}
</script>

<style scoped lang="scss">
.user-block {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  color: $zs-menu-fg;
  cursor: default;

  &--collapsed {
    align-items: center;
    padding: 0.4rem 0;
  }
}

.user-block__user {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;

  .user-block--collapsed & {
    width: auto;
  }

  border: 0;
  border-radius: 0.3rem;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: 1.3;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.08);
  }
}

// подменю раскрывается вверх: свёрнутое — шеврон вверх, раскрытое — вниз
.user-block__chevron {
  transform: rotate(180deg);
  transition: transform 0.15s;

  .user-block__user--open & {
    transform: none;
  }
}

// подменю — пункты с отступом под ФИО, как пункты группы «Администрирование»
.user-block__submenu {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;

  .user-block--collapsed & {
    align-self: stretch;
    align-items: center;
  }
}

.user-block__sub-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  height: 2rem;
  border: 0;
  border-radius: 0.3rem;
  background: transparent;
  color: $zs-menu-fg;
  font: inherit;
  font-size: 0.8rem;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.08);
  }

  &--active {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
  }

  &:disabled {
    background: transparent;
    opacity: 0.5;
    cursor: default;
  }

  .user-block--collapsed & {
    justify-content: center;
    width: 2.8rem;
    padding: 0;
  }
}

.user-block__separator {
  border-top: 0.0625rem solid rgba(255, 255, 255, 0.14);

  .user-block--collapsed & {
    width: 2.8rem;
    margin: 0.3rem 0;
  }

  // во всю ширину меню: за отступы блока пользователя
  &--wide {
    margin: 0.3rem -0.6rem;

    .user-block--collapsed & {
      align-self: stretch;
      width: auto;
      margin: 0.3rem 0;
    }
  }
}

.user-block__text {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.user-block__name {
  color: #fff;
  font-size: 0.8rem;
  font-weight: 600;
  white-space: nowrap;
}

// роли не обрезаются: не помещаются в строку — переносятся
.user-block__role {
  color: $zs-menu-fg-muted;
  font-size: 0.8rem;
  overflow-wrap: anywhere;
}
</style>
