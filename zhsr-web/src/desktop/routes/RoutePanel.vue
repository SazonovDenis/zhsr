<template>
  <SidePanel
    :empty="route ? '' : 'Выберите маршрут в списке'"
    :title="route ? t('Маршрут № {train}', { train: route.trainNumber }) : ''"
    :subtitle="route?.name ?? ''"
    :scroll="false"
    @close="emit('close')"
  >
    <template v-if="route">
      <SidePanelFields>
        <span>{{ t('Вид') }}</span>
        <span
          >{{ dictManager.getValue('RouteKind', route.routeKind) }},
          {{ dictManager.getValue('TransportType', route.transportType).toLowerCase() }}</span
        >
        <span>{{ t('Действует') }}</span>
        <span>{{ period }}</span>
        <span>{{ t('Время в пути') }}</span>
        <span>{{ formatHm(route.duration) }}</span>
        <span>{{ t('Нормы часов') }}</span>
        <span>
          {{ t('проводник {c}, ЛНП {l}, ПЭМ {p}', { c: route.conductorHours, l: route.lnpHours, p: route.pemHours }) }}
        </span>
      </SidePanelFields>

      <!-- без прав кнопка видна, но неактивна, причина во всплывающей подсказке -->
      <div class="route-panel__actions">
        <button
          type="button"
          class="route-panel__edit q-py-none q-px-md"
          :disabled="!!editReason"
          :title="editReason || undefined"
          @click="emit('edit')"
        >
          {{ t('Редактировать') }}
        </button>
      </div>

      <!-- остановки — списком или на карте (переключатель); прокручиваются только они, атрибуты над ними на месте -->
      <section class="route-panel__stops q-pt-md q-px-lg q-pb-none">
        <div class="route-panel__tabs" role="tablist">
          <button
            v-for="tab in TABS"
            :key="tab.id"
            type="button"
            role="tab"
            class="route-panel__tab q-pt-none q-px-none q-pb-xs"
            :class="{ 'route-panel__tab--active': view === tab.id }"
            :aria-selected="view === tab.id"
            @click="view = tab.id"
          >
            {{ t(tab.label) }}
          </button>
        </div>
        <!-- станции маршрута — из карточки маршрута: грузятся при выборе строки -->
        <LoadSpinner v-if="loading" />
        <ErrorBox v-else-if="error" :error="error" :retry="load" />
        <template v-else-if="full">
          <div v-if="view === 'list'" class="route-panel__scroll q-py-none q-px-lg">
            <RouteStops v-model:collapsed="collapsed" :points="full.points" layout="vertical" />
          </div>
          <!-- карта — во всё место под переключателем; станции следования — как в списке: развёрнуты — видны всегда,
               свёрнуты — только при увеличении -->
          <RouteMap v-else :points="full.points" :show-passing="!collapsed" class="route-panel__map" />
        </template>
      </section>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import type { Route, RouteRow } from '@/api/routes'
import { fetchRoute } from '@/api/routes'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { dictManager } from '@/shared/dict/DictManager'
import RouteMap from '@/shared/components/map/RouteMap.vue'
import { RoleType } from '@/shared/constants/systemDicts'
import { roleReason } from '@/shared/roleReason'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import RouteStops from './RouteStops.vue'

/**
 * Правая панель списка маршрутов: атрибуты выбранного маршрута — из строки списка, переход в редактор и остановки.
 * Остановки — из карточки маршрута (все станции): она грузится при выборе строки, спиннер и ошибка — на месте
 * остановок.
 */
const props = defineProps<{ route: RouteRow | null }>()

const emit = defineEmits<{ close: []; edit: [] }>()

// маршрут правят администратор и планировщик вагонов (Ф-МАР-02)
const editReason = computed(() => roleReason([RoleType.WAGON_PLANNER], 'Правит планировщик вагонов'))

// остановки — списком или на карте; выбор сохраняется при переходе к другому маршруту
const TABS = [
  { id: 'list', label: 'Остановки' },
  { id: 'map', label: 'На карте' },
] as const
const view = ref<(typeof TABS)[number]['id']>('list')
// станции следования свёрнуты — в списке и на карте одинаково
const collapsed = ref(true)

const full = shallowRef<Route | null>(null)
const loading = ref(false)
const error = ref<ShownError | null>(null)
// номер загрузки: ответ по прежде выбранному маршруту приходит позже — он не показывается
let seq = 0

async function load() {
  const my = ++seq
  full.value = null
  error.value = null
  const route = props.route
  if (!route) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const loaded = await fetchRoute(route.id)
    if (my === seq) full.value = loaded
  } catch (e) {
    if (my === seq) error.value = shownError(e)
  } finally {
    if (my === seq) loading.value = false
  }
}

watch(() => props.route?.id, load, { immediate: true })

const date = (iso: string) => iso.split('-').reverse().join('.')

const period = computed(() => {
  const route = props.route
  if (!route) return ''
  if (!route.dend) return t('с {since}', { since: date(route.dbeg) })
  return t('с {since} по {until}', { since: date(route.dbeg), until: date(route.dend) })
})
</script>

<style scoped lang="scss">
.route-panel__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin: -0.3rem 0 -0.9rem;
}

.route-panel__edit {
  height: 2rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

// остановки занимают всё место панели под атрибутами
.route-panel__stops {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.6rem;
  min-height: 0;
  margin: 0 -1.5rem;
  border-top: 0.0625rem solid $zs-border;
}

// переключатель — заголовками раздела: выбранный — тёмным с чертой снизу
.route-panel__tabs {
  display: flex;
  flex: none;
  gap: 1.3rem;
  // отступ под подписью группы — на треть меньше промежутка между блоками (0.6rem)
  margin-bottom: calc(-0.6rem / 3);
}

.route-panel__tab {
  border: 0;
  border-bottom: 0.1rem solid transparent;
  background: none;
  color: $zs-text-muted;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  cursor: pointer;

  &--active {
    border-bottom-color: $primary;
    color: $zs-text;
  }
}

// прокрутка — только у списка остановок; полоса прокрутки — у края панели
.route-panel__scroll {
  flex: 1;
  min-height: 0;
  margin: 0 -1.5rem;
  overflow-y: auto;
}

.route-panel__map {
  flex: 1;
}
</style>
