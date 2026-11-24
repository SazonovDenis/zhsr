import { computed, ref, shallowRef, watch } from 'vue'

import type { CurrentUser } from '@/api/session'
import type { RequestWagon } from '@/api/wagons'
import { findWagons, WAGON_SEARCH_LIMIT } from '@/api/wagons'
import { CrewDuty } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { loadTripWagons } from '@/shared/requests/useWagonPick'

/** Режим ввода вагона: мой вагон, мой рейс, другой вагон. */
export type WagonMode = 'my' | 'trip' | 'other'

/** Пауза в вводе цифр перед поиском по номеру, мс. */
const SEARCH_DELAY = 300

/**
 * Вагон заявки на телефоне (Ф-ЗАЯ-01): три режима ввода.
 *
 * - «Мой вагон» — вагоны проводника в текущем рейсе (myWagons): карточками; вагон один — выбирает страница сама,
 *   вагонов несколько — выбирает пользователь.
 * - «Мой рейс» — состав текущего рейса открытым списком, без поиска. Без связи состав берётся сохранённый на телефоне.
 * - «Другой вагон» — поиск по цифрам номера среди всех вагонов; пока цифры не введены, список пуст; без связи —
 *   ошибка. Пришло ровно WAGON_SEARCH_LIMIT вагонов — limited: страница просит уточнить номер.
 *
 * Недоступные режимы (нет рейса, нет своих вагонов) в modes не попадают: на телефоне недоступное не показывается.
 * Смена режима очищает введённые цифры; выбранный вагон очищает страница. Режим исправляемой заявки — modeOf.
 */
export function useWizardWagon(user: () => CurrentUser | null) {
  const trip = computed(() => user()?.trip ?? null)
  /** номера вагонов проводника в текущем рейсе */
  const myNumbers = computed(() => (trip.value?.crewDutyType === CrewDuty.CONDUCTOR ? trip.value.wagons : []))

  const modes = computed(() => [
    ...(myNumbers.value.length > 0 ? [{ id: 'my', text: 'Мой вагон' }] : []),
    ...(trip.value ? [{ id: 'trip', text: 'Мой рейс' }] : []),
    { id: 'other', text: 'Другой вагон' },
  ])

  /** первый доступный режим */
  function defaultMode(): WagonMode {
    if (myNumbers.value.length > 0) return 'my'
    return trip.value ? 'trip' : 'other'
  }

  const mode = ref<WagonMode>(defaultMode())
  /** цифры номера вагона */
  const query = ref('')
  const tripWagons = shallowRef<RequestWagon[]>([])
  const found = shallowRef<RequestWagon[]>([])
  const loading = ref(false)
  /** ошибка загрузки; показывается на месте списка, клик — повтор (load) */
  const error = ref<ShownError | null>(null)

  /** вагоны проводника в составе рейса, в порядке закрепления */
  const myWagons = computed(() =>
    myNumbers.value.map((n) => tripWagons.value.find((w) => w.number === n)).filter((w): w is RequestWagon => !!w),
  )

  /** список режима: «Мой рейс» — состав; «Другой вагон» — найденные */
  const wagons = computed(() => {
    if (mode.value === 'trip') return tripWagons.value
    return mode.value === 'other' ? found.value : []
  })

  const limited = computed(() => mode.value === 'other' && found.value.length === WAGON_SEARCH_LIMIT)

  let seq = 0
  async function load() {
    const my = ++seq
    loading.value = true
    error.value = null
    try {
      // справочники строк списка (тип вагона; расположение — в «Другом вагоне») — параллельно с вагонами
      const [, list] = await Promise.all([
        dictManager.prepareDicts('WagonType', 'Station', 'Depot', 'Yard'),
        loadWagons(),
      ])
      if (my !== seq) return
      if (mode.value === 'other') found.value = list
      else tripWagons.value = list
    } catch (e) {
      if (my === seq) error.value = shownError(e)
    } finally {
      if (my === seq) loading.value = false
    }
  }

  function loadWagons(): Promise<RequestWagon[]> {
    if (mode.value === 'other') return query.value ? findWagons(query.value) : Promise.resolve([])
    const id = trip.value?.id
    if (!id) return Promise.resolve([])
    return tripWagons.value.length > 0 ? Promise.resolve(tripWagons.value) : loadTripWagons(id, true)
  }

  /** Сменить режим: введённые цифры и найденные вагоны очищаются, список режима загружается. */
  function setMode(value: WagonMode) {
    if (value === mode.value) return
    mode.value = value
    query.value = ''
    found.value = []
    void load()
  }

  // «Другой вагон»: поиск по цифрам после паузы в вводе; всё, кроме цифр, отбрасывается
  let timer: ReturnType<typeof setTimeout> | undefined
  watch(query, (text) => {
    const digits = text.replace(/[^\d/]/g, '')
    if (digits !== text) {
      query.value = digits
      return
    }
    if (mode.value !== 'other') return
    clearTimeout(timer)
    timer = setTimeout(() => void load(), SEARCH_DELAY)
  })

  /**
   * Режим для уже выбранного вагона (исправление заявки): вагон проводника (myWagons) — «Мой вагон», вагон состава
   * текущего рейса — «Мой рейс», иначе — «Другой вагон». Состав рейса не загрузился (нет связи и копии) — «Другой
   * вагон».
   */
  async function modeOf(wagonId: number): Promise<WagonMode> {
    const id = trip.value?.id
    if (!id) return 'other'
    try {
      if (tripWagons.value.length === 0) tripWagons.value = await loadTripWagons(id, true)
    } catch {
      // состава нет — вагон показывается в «Другом вагоне»; ошибку покажет список, если пользователь откроет «Мой рейс»
      return 'other'
    }
    if (myWagons.value.some((w) => w.id === wagonId)) return 'my'
    return tripWagons.value.some((w) => w.id === wagonId) ? 'trip' : 'other'
  }

  return { trip, modes, defaultMode, mode, setMode, modeOf, query, wagons, myWagons, limited, loading, error, load }
}
