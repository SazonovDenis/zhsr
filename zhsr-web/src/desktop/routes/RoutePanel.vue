<template>
  <SidePanel
    :empty="route ? '' : 'Выберите маршрут в списке'"
    :title="route ? t('Маршрут № {train}', { train: route.train }) : ''"
    :subtitle="route?.name ?? ''"
    @close="emit('close')"
  >
    <template v-if="route">
      <SidePanelFields>
        <span>{{ t('Вид') }}</span>
        <span>{{ dictManager.getValue('RouteKind', route.kind) }}, {{ t(route.transport).toLowerCase() }}</span>
        <span>{{ t('Действует') }}</span>
        <span>{{ period }}</span>
        <span>{{ t('Время в пути') }}</span>
        <span>{{ t('{n} часов', route.durationHours) }}</span>
        <span>{{ t('Нормы часов') }}</span>
        <span>
          {{ t('проводник {c}, ЛНП {l}, ПЭМ {p}', { c: route.hoursConductor, l: route.hoursLnp, p: route.hoursPem }) }}
        </span>
      </SidePanelFields>

      <div class="route-panel__actions">
        <button type="button" class="route-panel__edit" @click="emit('edit')">{{ t('Редактировать') }}</button>
      </div>

      <section class="route-panel__stops">
        <div class="route-panel__section-title">{{ t('Остановки') }}</div>
        <RouteStops :points="route.points" layout="vertical" />
      </section>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { Route } from '@/api/routes'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import RouteStops from './RouteStops.vue'

/** Правая панель списка маршрутов: атрибуты выбранного маршрута, переход в редактор и остановки. */
const props = defineProps<{ route: Route | null }>()

const emit = defineEmits<{ close: []; edit: [] }>()

const date = (iso: string) => iso.split('-').reverse().join('.')

const period = computed(() => {
  const route = props.route
  if (!route) return ''
  if (!route.until) return t('с {since}', { since: date(route.since) })
  return t('с {since} по {until}', { since: date(route.since), until: date(route.until) })
})
</script>

<style scoped lang="scss">
.route-panel__actions {
  display: flex;
  justify-content: flex-end;
  margin: -0.25rem 0 -0.875rem;
}

.route-panel__edit {
  height: 2rem;
  padding: 0 0.75rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.84375rem;
  font-weight: 600;
  cursor: pointer;
}

.route-panel__section-title {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.route-panel__stops {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  margin: 0 -1.5rem;
  padding: 1.125rem 1.5rem 0;
  border-top: 0.0625rem solid $zs-border;
}
</style>
