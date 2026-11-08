<template>
  <SidePanel
    :empty="wagon ? '' : 'Выберите вагон в списке'"
    :title="wagon ? t('Вагон № {number}', { number: wagon.number }) : ''"
    :subtitle="
      wagon
        ? `${dictManager.getValue('WagonType', wagon.wagonType)} • ${dictManager.getValue('WagonModel', wagon.modelType)}`
        : ''
    "
    @close="emit('close')"
  >
    <SidePanelFields v-if="wagon">
      <span>{{ t('Депо приписки') }}</span>
      <span>{{ dictManager.getValue('Depot', wagon.depot) }}</span>
      <span>{{ t('Расположение') }}</span>
      <span>{{ wagonPlace(wagon) }}</span>
      <span>{{ t('Состояние') }}</span>
      <span><DictBadge :id="wagon.wagonStatusType" dict="WagonState" /></span>
    </SidePanelFields>
    <div v-if="transfers.length" class="wagon-panel__actions q-mt-md">
      <template v-for="b in transfers" :key="b.kind">
        <button
          type="button"
          class="wagon-panel__btn q-py-none q-px-md"
          :disabled="!b.query"
          @click="b.query && transfer(b.query)"
        >
          {{ t(b.label) }}
        </button>
        <span class="wagon-panel__reason">{{ b.reason }}</span>
      </template>
    </div>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { LocationType, RoleType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { placeCode, placeKind, placeStation, stationPlaceList, wagonTransferPlace } from '@/shared/transferPlace'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import { wagonPlace } from '@/shared/wagonPlace'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'

/**
 * Правая панель списка вагонов: карточка выбранного вагона и кнопки передачи (Ф-АКТ-02) — «Передать на станцию»,
 * «Передать в депо», «Передать в парк отстоя». Кнопка открывает оформление акта: откуда — где вагон стоит, вагон
 * отмечен; куда — выбрано, если на станции вагона одно такое расположение. Кнопки видны всегда; передать нельзя —
 * кнопка недоступна, рядом пояснение: вагон в рейсе, вагон уже там или на станции вагона нет такого расположения.
 * Справочники Station, Depot и Yard загружает страница списка.
 */
const props = defineProps<{ wagon: WagonRow | null }>()

const emit = defineEmits<{ close: [] }>()

const router = useRouter()

onMounted(() => void loadCurrentUser())

const TARGETS = [
  { kind: LocationType.STATION, label: 'Передать на станцию' },
  { kind: LocationType.DEPOT, label: 'Передать в депо' },
  { kind: LocationType.YARD, label: 'Передать в парк отстоя' },
]

// передают и принимают приёмщик и мастер депо; администратор может всё
const canTransfer = computed(() =>
  [RoleType.ADMIN, RoleType.RECEIVER, RoleType.MASTER].some((r) => hasRole(currentUser.value, r)),
)

// где вагон уже стоит — пояснение к кнопке того же вида расположения
const ALREADY: Record<number, string> = {
  [LocationType.STATION]: 'Вагон уже на станции',
  [LocationType.DEPOT]: 'Вагон уже в депо',
  [LocationType.YARD]: 'Вагон уже в парке отстоя',
}
// на станции нет расположения, куда ведёт кнопка
const MISSING: Record<number, string> = {
  [LocationType.DEPOT]: 'На станции {station} нет депо',
  [LocationType.YARD]: 'На станции {station} нет парка отстоя',
}

/** Кнопки передачи: query — запрос страницы акта; null — передать нельзя, reason — почему. */
const transfers = computed(() => {
  const w = props.wagon
  if (!w || !canTransfer.value) return []
  const from = wagonTransferPlace(w.locationType, w.place)
  const station = from && placeStation(from)
  const places = station ? stationPlaceList(station) : []
  return TARGETS.map((b) => {
    const blocked = (reason: string) => ({ ...b, query: null, reason })
    if (!from) return blocked(t('Вагон в рейсе'))
    if (!station) return blocked(t('У депо или парка отстоя вагона не задана станция'))
    // куда — другие расположения этого вида на станции вагона
    const targets = places.filter((p) => placeKind(p) === b.kind && placeCode(p) !== placeCode(from))
    if (!targets.length) {
      return blocked(
        b.kind === placeKind(from)
          ? t(ALREADY[b.kind]!)
          : t(MISSING[b.kind]!, { station: dictManager.getValue('Station', station) }),
      )
    }
    const to = targets.length === 1 ? { to: placeCode(targets[0]!)! } : {}
    return { ...b, query: { from: placeCode(from)!, ...to, wagons: w.number }, reason: '' }
  })
})

function transfer(query: Record<string, string>) {
  void router.push({ name: 'transfer', query })
}
</script>

<style scoped lang="scss">
.wagon-panel__actions {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.5rem;
}

.wagon-panel__reason {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.wagon-panel__btn {
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $primary;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: $zs-bg-hover;
  }

  &:disabled {
    color: $zs-text-muted;
    cursor: default;
  }
}
</style>
