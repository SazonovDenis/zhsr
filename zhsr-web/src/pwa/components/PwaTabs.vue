<template>
  <nav class="pwa-tabs">
    <router-link
      v-for="tab in tabs"
      :key="tab.name"
      :to="{ name: tab.name }"
      class="pwa-tabs__tab"
      :class="{ 'pwa-tabs__tab--active': route.name === tab.name }"
    >
      <span class="pwa-tabs__icon">
        <SvgIcon v-if="tab.svg" :name="tab.svg" size="1.3rem" />
        <q-icon v-else :name="tab.icon" size="1.4rem" />
        <span v-if="tab.badge" class="pwa-tabs__badge q-py-none q-px-xs">{{ tab.badge }}</span>
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
 * Нижняя панель вкладок мобильной части: «Рейс», «Заявки» (число — заявки, ожидающие обработки), «Профиль».
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

const tabs = computed<{ name: string; label: string; icon?: string; svg?: IconName; badge?: number }[]>(() => [
  { name: 'pwa-home', label: 'Рейс', icon: 'today' },
  { name: 'pwa-requests', label: 'Заявки', svg: 'requests', badge: requests.value },
  { name: 'pwa-profile', label: 'Профиль', svg: 'user' },
])

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
  grid-template-columns: repeat(3, 1fr);
  border-top: 0.0625rem solid $zs-border;
  background: #fff;
}

.pwa-tabs__tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.1rem;
  min-height: 3.1rem;
  padding: 0.3rem 0 calc(0.3rem + env(safe-area-inset-bottom));
  color: $zs-text-muted;
  font-size: 0.7rem;
  font-weight: 500;
  text-decoration: none;

  // выделенный пункт — прямоугольник цвета верхней строки на всю ячейку, без зазоров
  &--active {
    background: var(--zs-header-bg);
    color: #fff;
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
  border-radius: 0.4rem;
}

.pwa-tabs__badge {
  position: absolute;
  top: -0.4rem;
  right: -0.6rem;
  min-width: 1.1rem;
  height: 1.1rem;
  border-radius: 0.6rem;
  background: #c62828;
  color: #fff;
  font-size: 0.7rem;
  font-weight: 700;
  line-height: 1.1rem;
  text-align: center;
}
</style>
