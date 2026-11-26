<template>
  <PwaPage :back="goBack" :title="t('Состав')" :subtitle="subtitle" fill>
    <ErrorBox v-if="error" :error="error" :retry="load" />
    <LoadSpinner v-if="busy.active.value && !crew" />

    <template v-if="crew">
      <div class="trip-wagons__head">
        {{ t('Вагоны') }}
        <span class="trip-wagons__muted">{{ t('{n} вагонов', items.length) }}</span>
      </div>
      <!-- строки вагона (WagonOption): номер, тип, справа — ответственные; под ними — готовность по норме проводников -->
      <div class="trip-wagons__list">
        <div v-for="w in items" :key="w.number" class="trip-wagons__row q-py-sm q-px-md">
          <WagonOption :number="w.number" :type="w.type" :place="w.place" />
          <div v-if="w.issues.length" class="trip-wagons__warn q-mt-xs">
            <div class="trip-wagons__warn-title">{{ t('Проводники не по норме') }}</div>
            <div v-for="issue in w.issues" :key="issue">{{ issue }}</div>
          </div>
        </div>
        <div v-if="items.length === 0" class="trip-wagons__muted q-py-lg q-px-md">{{ t('Вагонов нет') }}</div>
      </div>
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { TripCrew } from '@/api/trips'
import { fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import WagonOption from '@/shared/components/WagonOption.vue'
import { loadCached } from '@/shared/offline/cache'
import { crewNorm } from '@/shared/trips/crewNorm'
import { userName } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'

/**
 * Состав поезда рейса на телефоне — по ссылке «Состав» в карточке бригады: вагоны, которые едут в поезде, по позиции,
 * строками WagonOption: номер, тип, справа — ответственные из бригады; под вагоном с невыполненной нормой проводников
 * — «Проводники не по норме» и причины. Без связи — сохранённая
 * на телефоне копия.
 *
 * @prop id рейс
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const busy = provideBusy()
const crew = shallowRef<TripCrew | null>(null)
const error = ref<ShownError | null>(null)

const subtitle = computed(() => (crew.value ? `${crew.value.trip.trainNumber} • ${crew.value.trip.name}` : ''))

const items = computed(() => {
  const c = crew.value
  if (!c) return []
  const norm = crewNorm(c).wagons
  return c.wagons
    .filter((w) => w.riding)
    .sort((a, b) => a.seq - b.seq)
    .map((w) => {
      const usrs = c.crew.filter((m) => m.wagons.includes(w.number)).map((m) => userName(m.usr))
      return {
        issues: norm.find((n) => n.number === w.number)?.issues ?? [],
        number: w.number,
        type: `${w.seq}. ${dictManager.getValue('WagonType', w.wagonType)}`,
        place: usrs.length > 0 ? usrs.join(', ') : t('не назначен'),
      }
    })
})

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      const [cached] = await Promise.all([
        loadCached(`crew:${props.id}`, () => fetchTripCrew(Number(props.id))),
        dictManager.prepareDict('WagonType'),
      ])
      crew.value = cached.data
    })
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}
</script>

<style scoped lang="scss">
.trip-wagons__head {
  display: flex;
  flex: none;
  align-items: baseline;
  justify-content: space-between;
  font-size: 0.9rem;
  font-weight: 600;
}

.trip-wagons__list {
  flex: 0 1 auto;
  min-height: 0;
  overflow-y: auto;
  border: 0.1rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.trip-wagons__row + .trip-wagons__row {
  border-top: 0.1rem solid $zs-border-row;
}

.trip-wagons__warn {
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.trip-wagons__warn-title {
  font-weight: 600;
}

.trip-wagons__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;
}
</style>
