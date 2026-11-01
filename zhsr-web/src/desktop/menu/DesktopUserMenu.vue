<template>
  <!-- клики здесь не сворачивают левое меню: до aside они не доходят -->
  <div ref="blockEl" class="user-block" :class="{ 'user-block--collapsed': collapsed }" @click.stop>
    <!-- блок внизу панели: подменю раскрывается вверх, над пользователем -->
    <div v-if="open" class="user-block__submenu">
      <button
        v-for="option in LOCALES"
        :key="option.value"
        type="button"
        class="user-block__sub-item"
        :class="{ 'user-block__sub-item--active': option.value === locale }"
        :title="option.label"
        @click="choose(option.value)"
      >
        {{ collapsed ? option.short : option.label }}
      </button>
      <button type="button" class="user-block__sub-item" :title="t('Выход')" @click="onLogout">
        <q-icon name="logout" size="1.1rem" />
        <span v-if="!collapsed">{{ t('Выход') }}</span>
      </button>
    </div>

    <!-- клик по пользователю разворачивает подменю прямо в панели: язык и выход -->
    <button
      type="button"
      class="user-block__user"
      :class="{ 'user-block__user--open': open }"
      :title="collapsed ? `${user.name}, ${t(user.role)}` : undefined"
      @click="open = !open"
    >
      <SvgIcon name="user" class="user-block__icon" />
      <span v-if="!collapsed" class="user-block__text">
        <span class="user-block__name">{{ user.name }}</span>
        <span class="user-block__role">{{ t(user.role) }}</span>
      </span>

      <SvgIcon v-if="!collapsed" name="chevron-down" size="0.8rem" class="user-block__chevron" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import type { CurrentUser } from '@/api/session'
import { logout } from '@/api/session'
import type { MessageLanguages } from '@/boot/i18n'
import { i18n, t } from '@/boot/i18n'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { useCloseOnOutside } from '@/shared/composables/useCloseOnOutside'
import { LOCALES, setLocale } from '@/shared/locale'

/**
 * Текущий пользователь внизу левого меню: иконка, фамилия и роль. Клик по пользователю разворачивает подменю прямо
 * в панели, как у «Администрирования»: языки и выход. Выход завершает сеанс на сервере и переводит на страницу входа.
 * В свёрнутом меню — иконка пользователя (подсказка — фамилия и роль); подменю разворачивается так же, с кодами
 * языков и иконкой выхода.
 */
defineProps<{ user: CurrentUser; collapsed: boolean }>()

const router = useRouter()

const locale = computed(() => i18n.global.locale.value)

// подменю пользователя развёрнуто; сворачивается, как только теряет фокус
const open = ref(false)
const blockEl = ref<HTMLElement | null>(null)
useCloseOnOutside(blockEl, () => (open.value = false))

function choose(value: MessageLanguages) {
  setLocale(value)
  open.value = false
}

async function onLogout() {
  await logout()
  await router.push({ name: 'login' })
}
</script>

<style scoped lang="scss">
.user-block {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.375rem 0.625rem;
  color: $zs-menu-fg;
  cursor: default;

  &--collapsed {
    align-items: center;
    padding: 0.375rem 0;
  }
}

.user-block__user {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  width: 100%;

  .user-block--collapsed & {
    width: auto;
  }

  padding: 0.25rem 0.375rem;
  border: 0;
  border-radius: 0.25rem;
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
  margin-left: auto;
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
  gap: 0.125rem;
}

.user-block__sub-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  height: 2rem;
  padding: 0 0.75rem 0 2.1rem;
  border: 0;
  border-radius: 0.25rem;
  background: transparent;
  color: $zs-menu-fg;
  font: inherit;
  font-size: 0.84375rem;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.08);
  }

  &--active {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
  }

  .user-block--collapsed & {
    justify-content: center;
    width: 2.8rem;
    padding: 0;
  }
}

.user-block__text {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
}

.user-block__name {
  color: #fff;
  font-size: 0.8125rem;
  font-weight: 600;
  white-space: nowrap;
}

.user-block__role {
  color: $zs-menu-fg-muted;
  font-size: 0.75rem;
  white-space: nowrap;
}
</style>
