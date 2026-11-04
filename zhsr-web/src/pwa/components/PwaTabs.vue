<template>
  <nav class="pwa-tabs">
    <router-link
      v-for="tab in tabs"
      :key="tab.name"
      :to="{ name: tab.name }"
      class="pwa-tabs__tab"
      :class="{ 'pwa-tabs__tab--active': route.name === tab.name, 'pwa-tabs__tab--new': tab.add }"
    >
      <span class="pwa-tabs__icon">
        <q-icon v-if="tab.add" name="add" size="1.6rem" />
        <SvgIcon v-else-if="tab.svg" :name="tab.svg" size="1.25rem" />
        <q-icon v-else :name="tab.icon" size="1.35rem" />
        <span v-if="tab.badge" class="pwa-tabs__badge">{{ tab.badge }}</span>
      </span>
      <span class="pwa-tabs__label">{{ t(tab.label) }}</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { fetchCurrentState } from '@/api/state'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import type { IconName } from '@/shared/icons/icons'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { loadCached } from '@/shared/offline/cache'

/**
 * Нижняя панель вкладок мобильной части: «Сегодня», «Заявки» (число — заявки, ожидающие обработки), «+ Заявка» —
 * подача новой заявки, «Профиль».
 */
const route = useRoute()

const requests = ref(0)

async function loadState() {
  try {
    requests.value = (await loadCached('state', fetchCurrentState)).data.counters.requests ?? 0
  } catch {
    // счётчик: ошибку осознанно не показываем — без связи и без копии счётчик просто не виден
  }
}

const tabs = computed<{ name: string; label: string; icon?: string; svg?: IconName; add?: boolean; badge?: number }[]>(
  () => [
    { name: 'pwa-home', label: 'Сегодня', icon: 'today' },
    { name: 'pwa-requests', label: 'Заявки', svg: 'requests', badge: requests.value },
    { name: 'pwa-request-new', label: 'Заявка', add: true },
    { name: 'pwa-profile', label: 'Профиль', svg: 'user' },
  ],
)

const onRefresh = () => void loadState()
bus.on('state-refresh', onRefresh)
bus.on('connection-restored', onRefresh)
onBeforeUnmount(() => {
  bus.off('state-refresh', onRefresh)
  bus.off('connection-restored', onRefresh)
})

onMounted(loadState)
</script>

<style scoped lang="scss">
.pwa-tabs {
  display: grid;
  flex: none;
  grid-template-columns: repeat(4, 1fr);
  padding: 0.375rem 0.5rem calc(0.5rem + env(safe-area-inset-bottom));
  border-top: 0.0625rem solid $zs-border;
  background: #fff;
}

.pwa-tabs__tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  min-height: 3.3rem;
  color: $zs-text-muted;
  font-size: 0.6875rem;
  font-weight: 500;
  text-decoration: none;

  &--active {
    color: $primary;
    font-weight: 700;
  }
}

.pwa-tabs__icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 0.375rem;

  .pwa-tabs__tab--active & {
    background: $zs-selected-bg;
  }

  // «+ Заявка» — крупная кнопка цвета акцента
  .pwa-tabs__tab--new & {
    width: 2.5rem;
    height: 2.5rem;
    border-radius: 0.5rem;
    background: $primary;
    color: #fff;
  }
}

.pwa-tabs__badge {
  position: absolute;
  top: -0.375rem;
  right: -0.625rem;
  min-width: 1.1rem;
  height: 1.1rem;
  padding: 0 0.3125rem;
  border-radius: 0.5625rem;
  background: #c62828;
  color: #fff;
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1.1rem;
  text-align: center;
}
</style>
