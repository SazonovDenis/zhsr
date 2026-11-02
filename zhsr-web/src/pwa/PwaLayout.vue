<template>
  <div class="pwa">
    <header class="pwa__header">
      <button type="button" class="pwa__burger" :title="t('Меню')">
        <q-icon name="menu" size="1.5rem" />
        <q-menu anchor="bottom left" self="top left" :offset="[0, 6]">
          <q-list class="pwa__menu">
            <q-item
              v-for="option in LOCALES"
              :key="option.value"
              v-close-popup
              clickable
              :active="option.value === locale"
              active-class="pwa__menu-active"
              @click="setLocale(option.value)"
            >
              <q-item-section side class="pwa__menu-code">{{ option.short }}</q-item-section>
              <q-item-section>{{ option.label }}</q-item-section>
            </q-item>
            <q-separator />
            <q-item v-close-popup clickable @click="onLogout">
              <q-item-section side><q-icon name="logout" /></q-item-section>
              <q-item-section>{{ t('Выход') }}</q-item-section>
            </q-item>
          </q-list>
        </q-menu>
      </button>
      <span class="pwa__brand">{{ t('Жолсерік') }}</span>
      <span class="pwa__net" :class="{ 'pwa__net--off': !isOnline }">
        <span class="pwa__net-dot" />
        {{ isOnline ? t('В сети') : t('Нет связи') }}
      </span>
    </header>

    <!-- заявки, поданные без связи: ждут на телефоне -->
    <div v-if="outbox.length > 0" class="pwa__outbox">
      <span class="pwa__outbox-text">
        {{
          isOnline
            ? t('Не отправлено: {n}', { n: outbox.length })
            : t('Нет связи • не отправлено: {n}', { n: outbox.length })
        }}
      </span>
      <button type="button" class="pwa__outbox-btn" :disabled="!isOnline || sending" @click="send">
        {{ t('Отправить') }}
      </button>
    </div>

    <main class="pwa__main">
      <router-view />
    </main>

    <PwaTabs v-if="route.meta.tabs" />
  </div>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { i18n, t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { dictManager } from '@/shared/dict/DictManager'
import { LOCALES, setLocale } from '@/shared/locale'
import { isOnline } from '@/shared/network'
import { flushOutbox, outbox, startOutbox } from '@/shared/offline/outbox'
import { REQUEST_DICTS } from '@/shared/requests/requestView'
import { loadCurrentUser, logoutUser } from '@/shared/user'
import PwaTabs from './components/PwaTabs.vue'

/**
 * Каркас мобильной части: шапка (меню слева — языки и выход, название, есть ли связь), полоса неотправленных заявок,
 * страница и нижняя панель вкладок — у страниц с meta.tabs. Страницы, в которые «проваливаются» (карточка заявки,
 * мастер подачи, формирование рейса), рисуют свою шапку с кнопкой «назад» и свою нижнюю панель (PwaPage).
 *
 * При старте загружаются текущий пользователь и справочники заявок, читается очередь неотправленных заявок; без
 * связи всё это берётся из копии на телефоне.
 */

const route = useRoute()
const router = useRouter()

const locale = computed(() => i18n.global.locale.value)

const sending = ref(false)

async function send() {
  sending.value = true
  try {
    const n = await flushOutbox()
    if (n > 0) Notify.create({ message: t('Отправлено заявок: {n}', { n }), position: 'bottom' })
  } finally {
    sending.value = false
  }
}

async function onLogout() {
  await logoutUser()
  await router.push({ name: 'login' })
}

// связь восстановилась — данные пользователя (текущий рейс) могли измениться
const onRestored = () => void loadCurrentUser(true).catch(() => undefined)
bus.on('connection-restored', onRestored)
onBeforeUnmount(() => bus.off('connection-restored', onRestored))

onMounted(async () => {
  await Promise.all([
    loadCurrentUser().catch(() => undefined),
    dictManager.prepareDicts(...REQUEST_DICTS, 'TripState', 'Role'),
    startOutbox(),
  ])
})
</script>

<style scoped lang="scss">
// во всю видимую высоту экрана телефона: 100dvh не учитывает адресную строку
.pwa {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: $zs-bg-header;
  color: $zs-text;
  font-size: 0.9375rem;
}

.pwa__header {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem 0.5rem 0.5rem;
  background: $zs-menu-bg;
  color: #fff;
}

.pwa__burger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.8rem;
  height: 2.8rem;
  padding: 0;
  border: 0;
  border-radius: 0.375rem;
  background: transparent;
  color: inherit;
  cursor: pointer;

  &:active {
    background: rgba(255, 255, 255, 0.12);
  }
}

.pwa__brand {
  flex: 1;
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.pwa__net {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: $zs-menu-fg;
  font-size: 0.75rem;

  &--off {
    color: #f7b733;
    font-weight: 700;
  }
}

.pwa__net-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: #3fbf6b;

  .pwa__net--off & {
    background: #f2a516;
  }
}

.pwa__menu {
  min-width: 12rem;
}

.pwa__menu-code {
  min-width: 2.2rem;
  color: $zs-text-muted;
  font-weight: 600;
}

.pwa__menu-active {
  background: $zs-selected-bg;
  color: $zs-selected-fg;
}

.pwa__outbox {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.625rem;
  padding: 0.5rem 1rem;
  background: #fff1cc;
  color: #5c3d00;
  font-size: 0.8125rem;
  font-weight: 600;
}

.pwa__outbox-text {
  flex: 1;
}

.pwa__outbox-btn {
  height: 2.3rem;
  padding: 0 0.875rem;
  border: 0.0625rem solid #c79a2e;
  border-radius: 0.375rem;
  background: #fff;
  color: #5c3d00;
  font: inherit;
  cursor: pointer;

  &:disabled {
    opacity: 0.5;
  }
}

.pwa__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
</style>
