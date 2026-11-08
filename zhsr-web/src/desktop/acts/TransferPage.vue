<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title">{{ t(title) }}</h1>
    </template>

    <LoadSpinner v-if="busy.active.value" />

    <div v-if="ready" class="transfer">
      <div class="transfer__body">
        <FormSection title="Акт" class="transfer__act">
          <FormField label="Станция">
            <CbList
              single
              placeholder="Выберите…"
              :model-value="station"
              :values="stations"
              @update:model-value="setStation($event as number | null)"
            />
          </FormField>
          <FormField label="Откуда">
            <CbList
              single
              placeholder="Выберите…"
              :model-value="fromCode"
              :values="fromChoices"
              :source="placesLoad"
              @update:model-value="setFrom($event as string | null)"
            >
              <!-- строка списка в колонки: название, вид расположения, сколько вагонов там стоит -->
              <template #option="{ item }">
                <div class="transfer__place">
                  <span>{{ item.data?.name }}</span>
                  <span class="zs-sub">{{ item.data?.kind }}</span>
                  <span class="transfer__place-count">{{ t('{n} вагонов', item.data?.count as number) }}</span>
                </div>
              </template>
            </CbList>
          </FormField>
          <FormField label="Куда">
            <CbList v-model="toCode" single placeholder="Выберите…" :values="toChoices" :source="placesLoad">
              <template #option="{ item }">
                <div class="transfer__place">
                  <span>{{ item.data?.name }}</span>
                  <span class="zs-sub">{{ item.data?.kind }}</span>
                  <span class="transfer__place-count">{{ t('{n} вагонов', item.data?.count as number) }}</span>
                </div>
              </template>
            </CbList>
          </FormField>
          <FormField label="Вид работ">
            <CbList
              v-model="work"
              single
              placeholder="Выберите…"
              :values="dictChoices('WorkType')"
              :source="dictSource('WorkType')"
            />
          </FormField>
          <FormField label="Время акта">
            <DatePicker v-model="time" with-time date-text="Время акта" />
          </FormField>
          <div v-if="toCode" class="transfer__mode q-py-sm q-px-md" :class="{ 'transfer__mode--none': !mode }">
            {{ modeText }}
          </div>
          <FormField v-if="mode === 'immediate'" label="Кто сдал вагоны">
            <CbList v-model="giver" single placeholder="Выберите…" :values="giverChoices" :source="giverLoad" />
          </FormField>
          <ErrorBox v-if="error" :error="error" />
        </FormSection>

        <div class="transfer__list">
          <div class="transfer__list-head">
            <span class="transfer__caption">{{ t('Вагоны') }}</span>
            <span class="zs-sub">{{ selText }}</span>
          </div>
          <DataTable :columns="columns" :rows="wagons" row-key="id" :loading="wagonsBusy" :empty="emptyText" />
        </div>
      </div>

      <div class="transfer__footer q-pt-md">
        <span class="zs-sub">{{ okHint }}</span>
        <button type="button" class="zs-btn-primary" :disabled="!canSave" @click="save">
          {{ t(okText) }}
        </button>
      </div>
    </div>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { date, Notify } from 'quasar'
import { computed, provide, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { TransferPlace, TransferWagon } from '@/api/transfers'
import { createTransfer, fetchTransferGivers, fetchTransferPlaces, fetchTransferWagons } from '@/api/transfers'
import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { ActStatus, RoleType } from '@/shared/constants/systemDicts'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { Column } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { useLoad } from '@/shared/loadable'
import {
  placeCode,
  placeKind,
  placeName,
  placeOfCode,
  placeStation,
  stationPlaceList,
  transferPlaceText,
} from '@/shared/transferPlace'
import { currentUser, hasRole, loadCurrentUser, userName } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { wagonsColumns } from '../wagons/wagonsColumns'
import CellTransferProperty from './cells/CellTransferProperty.vue'
import { TRANSFER_CHOICE } from './transferChoice'

/**
 * Оформление акта приёма-передачи (Ф-АКТ-02): откуда и куда — разные расположения одной станции (сама станция, её депо
 * и парки отстоя), со станции на станцию не передаётся. Акт принимающей стороны и администратора проводится сразу, с
 * указанием, кто сдал вагоны; акт передающей стороны ждёт приёма. Запрос страницы может заранее задать акт: from и to —
 * коды расположений (shared/transferPlace.ts), wagons — номера вагонов через запятую; так открывают акт кнопки панели
 * вагона, «Вернуть на станцию» из прицепки и «Оформить передачу» из формирования рейса. Проверки — на сервере.
 *
 * Вид — по макету (экран «Передача вагонов со станции»): слева поля акта, справа таблица вагонов с колонками списка
 * вагонов и колонкой «Передача» / «Приём» — переключатель «с имуществом / без имущества»; выбор значения отмечает
 * вагон. Внизу — кнопка оформления.
 */
const route = useRoute()
const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

const query = route.query
const str = (v: unknown) => (typeof v === 'string' && v ? v : null)

const station = ref<number | null>(null)
const fromCode = ref<string | null>(str(query.from))
const toCode = ref<string | null>(str(query.to))
const work = ref<number | null>(null)
const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const giver = ref<number | null>(null)
const wagons = shallowRef<TransferWagon[]>([])
// отмеченные вагоны: номер → с имуществом; вагоны из запроса страницы отмечены без имущества
const chosen = ref<Record<string, boolean>>(
  Object.fromEntries((query.wagons ? String(query.wagons).split(',') : []).map((n) => [n, false])),
)
const selected = computed(() => Object.keys(chosen.value))
provide(TRANSFER_CHOICE, {
  chosen,
  set(number, withProperty) {
    const next = { ...chosen.value }
    if (withProperty === null) delete next[number]
    else next[number] = withProperty
    chosen.value = next
  },
})
const ready = ref(false)
const running = ref(false)
const wagonsBusy = ref(false)
const error = ref<ShownError | null>(null)

const from = computed(() => placeOfCode(fromCode.value))
const to = computed(() => placeOfCode(toCode.value))

// станции, при которых есть депо или парк отстоя
const stations = computed(() => {
  if (!ready.value) return []
  const ids = new Set(
    [...dictManager.getList('Depot'), ...dictManager.getList('Yard')].map((r) => (r as { station?: number }).station),
  )
  return [...ids].filter((id): id is number => !!id).map((id) => ({ id, text: dictManager.getValue('Station', id) }))
})

// откуда — любое расположение станции; куда — другое расположение, со станции на станцию не передаётся
// сколько вагонов стоит в каждом расположении станции: код расположения → число; загрузка — выпадающего списка «Откуда»
const placeCounts = shallowRef<Record<string, number>>({})
const placesLoad = useLoad(async () => {
  const list = station.value ? await fetchTransferPlaces(station.value) : []
  placeCounts.value = Object.fromEntries(list.map((p) => [placeCode(p)!, p.wagonCount]))
})

// откуда — любое расположение станции, с числом вагонов в нём; куда — другое расположение, со станции на станцию не
// передаётся
const places = computed(() => (ready.value && station.value ? stationPlaceList(station.value) : []))
// вариант списка: в строке — название, вид расположения и сколько вагонов там стоит
function placeChoice(p: TransferPlace) {
  const id = placeCode(p)!
  const kind = dictManager.getValue('LocationKind', placeKind(p))
  const count = placeCounts.value[id] ?? 0
  return { id, text: transferPlaceText(p), data: { name: placeName(p), kind, count } }
}
const fromChoices = computed(() => places.value.map(placeChoice))
const toChoices = computed(() =>
  places.value
    .filter((p) => placeCode(p) !== fromCode.value && !(from.value.station !== null && p.station !== null))
    .map(placeChoice),
)

/**
 * Роль пользователя в акте: immediate — принимающая сторона или администратор, акт проводится сразу; pending —
 * передающая сторона, акт ждёт приёма; null — роль не участвует. На станцию и со станции работает приёмщик, в депо,
 * в парк отстоя и из них — мастер депо; обе стороны — на станции своего депо.
 */
const mode = computed<'immediate' | 'pending' | null>(() => {
  const u = currentUser.value
  if (hasRole(u, RoleType.ADMIN)) return 'immediate'
  if (!fromCode.value || !toCode.value || u?.depotStation !== station.value) return null
  const taker = to.value.station !== null ? RoleType.RECEIVER : RoleType.MASTER
  const giverRole = from.value.station !== null ? RoleType.RECEIVER : RoleType.MASTER
  if (hasRole(u, taker)) return 'immediate'
  return hasRole(u, giverRole) ? 'pending' : null
})
const immediate = computed(() => mode.value === 'immediate')

const takerName = computed(() => (to.value.station !== null ? 'приёмщик' : 'мастер депо'))
const modeText = computed(() => {
  if (mode.value === 'immediate')
    return t('Вы принимающая сторона: акт оформляется сразу принятым, вагоны переводятся одним действием.')
  if (mode.value === 'pending')
    return t(
      'Вы сдаёте вагоны. Акт получит статус «Ожидает приёма»; расположение изменится, когда {taker} примет вагоны.',
      {
        taker: t(takerName.value),
      },
    )
  return t(
    'Ваша роль не участвует в такой передаче. Сдаёт со станции и принимает на станцию приёмщик, в депо и парк отстоя и из них — мастер депо.',
  )
})

// заголовок — как в макете: со станции, на станцию или между депо и парком отстоя
const title = computed(() => {
  if (from.value.station !== null) return 'Передача вагонов со станции'
  if (to.value.station !== null) return 'Приём вагонов на станцию'
  return 'Передача вагонов'
})

// кнопка — как в макете: кто оформляет и куда
const okText = computed(() => {
  const take = immediate.value
  if (to.value.depot !== null) return take ? 'Принять вагоны в депо' : 'Передать вагоны в депо'
  if (to.value.yard !== null) return take ? 'Принять вагоны в парк отстоя' : 'Передать вагоны в парк отстоя'
  if (to.value.station !== null) return take ? 'Принять вагоны на станцию' : 'Передать вагоны на станцию'
  return take ? 'Принять вагоны' : 'Передать вагоны'
})

// колонки — как в списке вагонов, и «Передача» / «Приём»: с имуществом или без, выбор отмечает вагон
const columns = computed(() =>
  defineColumns<TransferWagon>([
    // колонки списка вагонов годятся: строка вагона акта — та же строка вагона с полями акта
    ...(wagonsColumns as unknown as Column<TransferWagon>[]).map((c) => ({ ...c, sortable: false })),
    {
      field: 'pendingAct',
      title: to.value.station !== null ? 'Приём' : 'Передача',
      width: 'max-content',
      cell: CellTransferProperty,
    },
  ]),
)

const selText = computed(() =>
  selected.value.length > 0
    ? t('выбрано {a} из {b}', { a: selected.value.length, b: wagons.value.length })
    : t('{n} вагонов', wagons.value.length),
)

const emptyText = computed(() => {
  if (!station.value) return t('Выберите станцию')
  if (!fromCode.value) return t('Выберите, откуда передаются вагоны')
  return t('Вагонов нет')
})

// почему оформить нельзя — подсказка на кнопке; пусто — можно
const okHint = computed(() => {
  if (!fromCode.value || !toCode.value) return t('Выберите, откуда и куда передаются вагоны')
  if (!mode.value) return t('Ваша роль не участвует в такой передаче')
  if (!work.value) return t('Выберите вид работ')
  if (!time.value) return t('Укажите время акта')
  if (immediate.value && !giver.value) return t('Выберите, кто сдал вагоны')
  if (selected.value.length === 0) return t('Отметьте вагоны')
  return ''
})
const canSave = computed(() => !running.value && !okHint.value)

const back = () => {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'acts' })
}

// кто сдал — свой выпадающий список, страница его не ждёт; ошибка — в самом списке
const givers = shallowRef<UserRef[]>([])
const giverChoices = computed(() => givers.value.map((g) => ({ id: g.id, text: userName(g) })))
const giverLoad = useLoad(async () => {
  givers.value = fromCode.value ? await fetchTransferGivers(from.value) : []
})

async function loadWagons() {
  if (!fromCode.value) {
    wagons.value = []
    return
  }
  wagonsBusy.value = true
  try {
    wagons.value = await fetchTransferWagons(from.value)
    // отметки остаются только у вагонов, которые можно передать
    const shown = new Set(wagons.value.filter((w) => !w.pendingAct).map((w) => w.number))
    chosen.value = Object.fromEntries(Object.entries(chosen.value).filter(([n]) => shown.has(n)))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    wagonsBusy.value = false
  }
}

function setStation(value: number | null) {
  station.value = value
  placeCounts.value = {}
  void placesLoad.run()
  setFrom(null)
}

// новое «откуда»: другие вагоны и другие сдающие; «куда», совпавшее с ним, сбрасывается
function setFrom(value: string | null) {
  fromCode.value = value
  if (!toChoices.value.some((p) => p.id === toCode.value)) toCode.value = null
  chosen.value = {}
  giver.value = null
  givers.value = []
  void loadWagons()
  if (immediate.value) void giverLoad.run()
}

watch(immediate, (now) => {
  if (now) void giverLoad.run()
})

void busy
  .run(async () => {
    const [user] = await Promise.all([
      loadCurrentUser(),
      dictManager.prepareDicts('Station', 'Depot', 'Yard', 'LocationKind', 'WagonType', 'WagonModel', 'WagonState'),
    ])
    // станция — по заданному «откуда», иначе станция депо сотрудника
    station.value = (fromCode.value ? placeStation(from.value) : null) ?? user.depotStation
    ready.value = true
    void placesLoad.run()
    await loadWagons()
    if (immediate.value) void giverLoad.run()
  })
  .catch((e: unknown) => (error.value = shownError(e)))

async function save() {
  error.value = null
  running.value = true
  try {
    const act = await createTransfer({
      fromStation: from.value.station,
      fromDepot: from.value.depot,
      fromYard: from.value.yard,
      toStation: to.value.station,
      toDepot: to.value.depot,
      toYard: to.value.yard,
      workType: work.value!,
      transferredByUsr: giver.value,
      transferredDt: time.value!,
      wagons: selected.value.map((number) => ({ number, withProperty: chosen.value[number]! })),
    })
    Notify.create({
      message:
        act.actStatusType === ActStatus.DONE ? t('Акт оформлен, вагоны приняты') : t('Акт оформлен и ожидает приёма'),
      type: 'positive',
      position: 'bottom',
    })
    bus.emit('state-refresh')
    back()
  } catch (e) {
    error.value = shownError(e)
    // пока акт оформлялся, вагон мог уйти в другой акт или в другое расположение: список — заново, причина видна в строке
    void loadWagons()
  } finally {
    running.value = false
  }
}
</script>
<style scoped lang="scss">
.transfer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.transfer__body {
  display: grid;
  flex: 1;
  grid-template-columns: 21rem minmax(0, 1fr);
  gap: 1.3rem;
  min-height: 0;
}

.transfer__act {
  overflow-y: auto;
}

.transfer__mode {
  border-radius: 0.3rem;
  background: $zs-selected-bg;
  color: $zs-selected-fg;
  font-size: 0.8rem;
  line-height: 1.45;

  &--none {
    background: $zs-error-bg;
    color: $zs-error-fg;
  }
}

.transfer__place {
  display: grid;
  grid-template-columns: minmax(12rem, 1fr) 7rem 6rem;
  align-items: baseline;
  gap: 0.8rem;
}

.transfer__place-count {
  color: $zs-text-muted;
  text-align: right;
}

.transfer__list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-width: 0;
  min-height: 0;
}

.transfer__list-head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.transfer__caption {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.transfer__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.8rem;
  border-top: 0.0625rem solid $zs-border;
  margin-top: 0.9rem;
}
</style>
