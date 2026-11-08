<template>
  <PwaPage :title="pageTitle" :back="cancel">
    <template #subtitle>
      <div class="wizard__bars">
        <span v-for="i in 3" :key="i" class="wizard__bar" :class="{ 'wizard__bar--on': i - 1 <= step }" />
      </div>
    </template>
    <!-- главное показывающее страницы: общий спиннер на всю цепочку загрузок -->
    <LoadSpinner v-if="busy.active.value" />

    <!-- шаг 1: неисправность -->
    <template v-if="step === 0">
      <div class="wizard__label">{{ t('Неисправность') }}</div>
      <input v-model="faultQuery" class="wizard__input" :placeholder="t('Поиск словами: «окно дует»')" />
      <div v-if="!searching" class="wizard__chips">
        <button
          v-for="f in frequent"
          :key="f.id"
          type="button"
          class="wizard__chip q-py-none q-px-md"
          :class="{ 'wizard__chip--on': draft.faultType === f.id }"
          @click="pickFault(f.id)"
        >
          {{ f.name }}
        </button>
      </div>
      <div class="wizard__tree">
        <template v-for="c in tree" :key="c.id">
          <button type="button" class="wizard__cat q-py-none q-px-md" @click="toggleService(c.id)">
            <span class="wizard__cat-mark" :style="{ background: c.color }" />
            <span class="wizard__grow">{{ c.name }}</span>
            <span class="wizard__count">{{ c.count }}</span>
            <SvgIcon name="chevron-down" size="0.8rem" :class="{ 'wizard__chevron--closed': !serviceOpen(c.id) }" />
          </button>
          <template v-if="serviceOpen(c.id)">
            <template v-for="g in c.groups" :key="g.id">
              <button type="button" class="wizard__group q-py-none q-pr-md q-pl-lg" @click="toggleGroup(g.id)">
                <span class="wizard__grow">{{ g.name }}</span>
                <span class="wizard__count">{{ g.items.length }}</span>
                <SvgIcon name="chevron-down" size="0.8rem" :class="{ 'wizard__chevron--closed': !groupOpen(g.id) }" />
              </button>
              <template v-if="groupOpen(g.id)">
                <button
                  v-for="f in g.items"
                  :key="f.id"
                  type="button"
                  class="wizard__fault q-py-sm q-pr-md q-pl-xl"
                  :class="{ 'wizard__fault--on': draft.faultType === f.id }"
                  @click="pickFault(f.id)"
                >
                  <span class="wizard__grow">{{ f.name }}</span>
                  <span v-if="draft.faultType === f.id" class="wizard__check">✓</span>
                </button>
              </template>
            </template>
          </template>
        </template>
        <div v-if="tree.length === 0" class="wizard__empty q-py-lg q-px-md">{{ t('Ничего не найдено') }}</div>
      </div>
    </template>

    <!-- шаг 2: детали -->
    <template v-if="step === 1">
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Критичность') }}</span>
        <div class="wizard__chips">
          <button
            v-for="s in severities"
            :key="s.id"
            type="button"
            class="wizard__chip q-py-none q-px-md"
            :class="{ 'wizard__chip--on': draft.severityType === s.id }"
            @click="draft.severityType = Number(s.id)"
          >
            {{ s.text }}
          </button>
        </div>
      </div>
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Служба') }}</span>
        <span>{{ goesTo }}</span>
      </div>
      <div v-if="canChooseExecutor" class="wizard__field">
        <span class="wizard__label">{{ t('Исполнитель') }}</span>
        <select v-model="draft.executorUsr" class="wizard__select q-py-none q-px-md">
          <option :value="null">{{ t('Не назначать — возьмёт любой сотрудник службы') }}</option>
          <option v-for="u in staff" :key="u.id" :value="u.id">{{ u.name }}</option>
        </select>
      </div>
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Описание') }}</span>
        <textarea
          v-model="draft.description"
          class="wizard__textarea q-pa-md"
          rows="3"
          :placeholder="t('Например: в 3-м купе не закрывается окно')"
        />
      </div>
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Фото • {n} из 5', { n: draft.photos.length }) }}</span>
        <PhotoPicker v-model="draft.photos" :max="5" large compress />
      </div>
    </template>

    <!-- шаг 3: вагон -->
    <template v-if="step === 2">
      <div class="wizard__label">{{ t('Вагон') }}</div>
      <button
        v-if="pick.myWagon.value"
        type="button"
        class="wizard__mine q-pa-md"
        :class="{ 'wizard__mine--on': draft.wagon === pick.myWagon.value.id }"
        @click="chooseWagon(pick.myWagon.value)"
      >
        <span class="wizard__mine-text">
          <span class="wizard__caption">{{ t('Мой вагон') }}</span>
          <span class="wizard__mine-number zs-mono">{{ pick.myWagon.value.number }}</span>
          <span class="wizard__muted">{{ dictManager.getValue('WagonType', pick.myWagon.value.wagonType) }}</span>
        </span>
        <span v-if="draft.wagon === pick.myWagon.value.id" class="wizard__check">✓</span>
      </button>

      <div v-if="pick.myWagon.value" class="wizard__caption">{{ t('Другой вагон') }}</div>
      <div v-if="pick.hasTrip.value" class="wizard__chips">
        <button
          type="button"
          class="wizard__chip wizard__chip--small q-py-none q-px-md"
          :class="{
            'wizard__chip--on': pick.tripOn.value,
            'wizard__chip--faded': pick.tripOn.value && !!pick.query.value,
          }"
          @click="pick.tripOn.value = !pick.tripOn.value"
        >
          {{ t('Рейс {trip}', { trip: pick.tripLabel.value }) }}
          <span v-if="pick.tripOn.value">✕</span>
        </button>
      </div>
      <input
        :value="pick.query.value"
        class="wizard__input wizard__input--number"
        inputmode="numeric"
        :placeholder="t('Цифры номера вагона')"
        @input="onDigits"
      />
      <div class="wizard__list">
        <button
          v-for="w in pick.wagons.value"
          :key="w.number"
          type="button"
          class="wizard__wagon q-py-none q-px-md"
          :class="{ 'wizard__wagon--on': draft.wagon === w.id }"
          @click="chooseWagon(w)"
        >
          <span class="wizard__wagon-number zs-mono">{{ w.number }}</span>
          <span class="wizard__grow wizard__muted">{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
          <span v-if="draft.wagon === w.id" class="wizard__check">✓</span>
        </button>
        <LoadSpinner v-if="pick.loading.value && pick.wagons.value.length === 0" size="1.5rem" />
        <ErrorBox v-else-if="pick.error.value" :error="pick.error.value" :retry="pick.load" />
        <div v-else-if="!pick.loading.value && pick.wagons.value.length === 0" class="wizard__empty q-py-lg q-px-md">
          {{ t('Вагоны не найдены') }}
        </div>
      </div>
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Рейс') }}</span>
        <select v-model="draft.trip" class="wizard__select q-py-none q-px-md">
          <option :value="null">{{ t('Без рейса') }}</option>
          <option v-for="tr in tripOptions" :key="tr.id" :value="tr.id">{{ tr.text }}</option>
        </select>
      </div>
      <div v-if="draft.faultType" class="wizard__goes q-pa-md">
        <span class="wizard__caption">{{ t('Уйдёт') }}</span>
        <span class="wizard__goes-to">{{ goesTo }}</span>
      </div>
    </template>

    <div v-if="error" class="wizard__error q-py-sm q-px-md">{{ error }}</div>

    <template #bar>
      <div class="wizard__nav">
        <button type="button" class="wizard__btn q-py-none q-px-md" @click="back">
          {{ step === 0 ? t('Отмена') : t('Назад') }}
        </button>
        <button
          type="button"
          class="wizard__btn wizard__btn--primary q-py-none q-px-md"
          :disabled="!canNext || running"
          @click="next"
        >
          {{ step < 2 ? t('Далее') : id ? t('Сохранить') : t('Отправить') }}
        </button>
      </div>
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, reactive, ref, shallowRef, toRaw, watch } from 'vue'
import { useRouter } from 'vue-router'

import { isNetworkError } from '@/api/http'
import type { RequestDraft, UserRef } from '@/api/requests'
import { createRequest, fetchServiceStaff, updateRequest } from '@/api/requests'
import type { TripRef, RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { randomGuid } from '@/shared/db/db'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { notifyError } from '@/shared/errors'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { isOnline } from '@/shared/network'
import type { DraftView } from '@/shared/offline/outbox'
import {
  flushOutbox,
  outboxItem,
  queuedEdit,
  queueEdit,
  queuePhotos,
  queueRequest,
  updateQueuedRequest,
  withQueuedEdit,
} from '@/shared/offline/outbox'
import { isoDateTime } from '@/shared/format'
import { faultDefaults, faultTree, frequentFaults, frequentGroupId, isFaultSearch } from '@/shared/requests/faults'
import { ServiceType, Severity } from '@/shared/constants/systemDicts'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import { faultName } from '@/shared/requests/requestView'
import { canAssignOnCreate } from '@/shared/requests/rights'
import { loadOffline } from '@/shared/requests/useRequestCard'
import { useWagonPick } from '@/shared/requests/useWagonPick'
import { currentUser, loadCurrentUser } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'
import { tripRefText } from '@/shared/trips/tripRefText'

/**
 * Подача заявки на телефоне в 3 шага (Ф-ЗАЯ-01): неисправность, детали (критичность, описание, фото), вагон
 * (мой вагон, состав рейса или поиск по номеру) и рейс. Критичность подставляется из неисправности, служба определяется ею, рейс —
 * из вагона; всё можно изменить. Без связи заявка встаёт в очередь на телефоне и отправится сама.
 *
 * Задан id — исправление заявки (Ф-ЗАЯ-07): поля заполнены из заявки. Без связи исправление встаёт в очередь на
 * телефоне и отправится само. Id «local-…» — заявка из очереди: она ещё не на сервере, исправляется прямо в очереди.
 */
const props = withDefaults(defineProps<{ id?: string }>(), { id: '' })

const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

const step = ref(0)

// вагон 0 и неисправность 0 — ещё не выбраны
const draft = reactive<RequestDraft>({
  wagon: 0,
  trip: null,
  faultType: 0,
  severityType: Severity.MEDIUM,
  description: '',
  photos: [],
  executorUsr: null,
})
// номер и тип выбранного вагона — для показа, пока черновик в очереди на телефоне
const wagonNumber = ref('')
const wagonType = ref(0)

// на шаге деталей в заголовке — выбранная неисправность
const pageTitle = computed(() => {
  if (step.value === 1 && draft.faultType) return faultName(draft.faultType)
  return step.value === 0 ? t('Вид неисправности') : t('Вагон и рейс')
})

const running = ref(false)
const error = ref('')

// исправляется заявка из очереди на телефоне: на сервере её ещё нет
const isQueued = computed(() => props.id.startsWith('local-'))

// --- шаг 1: неисправность ---

const faultQuery = ref('')
const searching = computed(() => isFaultSearch(faultQuery.value))
const tree = computed(() => faultTree(faultQuery.value))
const frequent = computed(() => frequentFaults())
// раскрыты по умолчанию технические неисправности и частые; при поиске раскрыто всё найденное
const openServices = ref<number[]>([ServiceType.TECHNICAL])
// группа частых неисправностей раскрывается, когда справочники загружены (onMounted)
const openGroups = ref<number[]>([])
const serviceOpen = (id: number) => searching.value || openServices.value.includes(id)
const groupOpen = (id: number) => searching.value || openGroups.value.includes(id)

const flip = (list: number[], id: number) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id])
const toggleService = (id: number) => (openServices.value = flip(openServices.value, id))
const toggleGroup = (id: number) => (openGroups.value = flip(openGroups.value, id))

// неисправность выбрана — критичность по умолчанию из неё
function pickFault(id: number) {
  draft.faultType = id
  draft.severityType = faultDefaults(id).severity
}

// --- шаг 2: детали ---

const severities = computed(() => [...dictChoices('Severity')].reverse().map((c) => ({ ...c, text: c.text ?? '' })))
// служба определяется неисправностью
const service = computed(() => (draft.faultType ? faultDefaults(draft.faultType).service : null))

const canChooseExecutor = computed(() => !props.id && !!currentUser.value && canAssignOnCreate(currentUser.value))
const staff = shallowRef<UserRef[]>([])
watch(
  () => (canChooseExecutor.value ? service.value : null),
  async (id) => {
    draft.executorUsr = null
    staff.value = id && isOnline.value ? await fetchServiceStaff(id) : []
  },
)

// --- шаг 3: вагон и рейс ---

const pick = useWagonPick(() => currentUser.value, true)
const pickedWagon = shallowRef<RequestWagon | null>(null)
const requestTrip = shallowRef<TripRef | null>(null)

function onDigits(e: Event) {
  const input = e.target as HTMLInputElement
  input.value = input.value.replace(/\D/g, '')
  pick.query.value = input.value
}

function chooseWagon(w: RequestWagon) {
  draft.wagon = w.id
  wagonNumber.value = w.number
  wagonType.value = w.wagonType
  pickedWagon.value = w
  draft.trip = w.trip?.id ?? null
}

// рейс: рейс вагона, текущий рейс пользователя, рейс исправляемой заявки
const tripOptions = computed(() => {
  const list: { id: number; text: string }[] = []
  const add = (id: number, text: string) => {
    if (!list.some((x) => x.id === id)) list.push({ id, text })
  }
  const wagonTrip = pickedWagon.value?.trip
  if (wagonTrip) add(wagonTrip.id, tripRefText(wagonTrip))
  const u = currentUser.value?.trip
  if (u) add(u.id, `${u.trainNumber} ${u.name}`)
  if (requestTrip.value) add(requestTrip.value.id, tripRefText(requestTrip.value))
  return list
})

// выбранный рейс для показа, пока черновик в очереди; у рейса пользователя плановое отправление — его отправление
function chosenTrip(): TripRef | null {
  const u = currentUser.value?.trip
  const trips = [pickedWagon.value?.trip, requestTrip.value, u && { ...u, plannedDt: u.departure }]
  const trip = trips.find((x) => x?.id === draft.trip)
  return trip ? { id: trip.id, trainNumber: trip.trainNumber, name: trip.name, plannedDt: trip.plannedDt } : null
}

const goesTo = computed(() => (service.value ? dictManager.getValue('Service', service.value) : ''))

// --- шаги ---

const canNext = computed(() => [!!draft.faultType, true, !!draft.wagon][step.value])

function back() {
  if (step.value === 0) cancel()
  else step.value--
}

function cancel() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}

async function next() {
  if (!canNext.value) return
  if (step.value < 2) {
    step.value++
    if (step.value === 2) void pick.load()
    return
  }
  await submit()
}

function toCard() {
  void router.replace({ name: 'pwa-request', params: { id: props.id } })
}

async function submit() {
  error.value = ''
  running.value = true
  // guid и время — с первой попытки: сервер мог принять запись, а ответ — потеряться; у заявки и её фото одно время
  const at = isoDateTime(new Date())
  const data: RequestDraft = { ...draft, photos: [...draft.photos], clientGuid: randomGuid(), clientDt: at }
  const view: DraftView = { wagonNumber: wagonNumber.value, wagonType: wagonType.value, trip: chosenTrip() }
  const author = currentUser.value?.id ?? 0
  try {
    if (isQueued.value) {
      if (await updateQueuedRequest(props.id, data, view)) toCard()
      else error.value = t('Заявка уже ушла на сервер — откройте её в списке заявок')
      return
    }
    if (props.id) {
      const id = Number(props.id)
      // исправление уже ждёт в очереди — новое должно его заменить, иначе из очереди ушло бы старое
      if (queuedEdit(id)) {
        await queueEdit(id, data, author, view)
        void flushOutbox()
      } else {
        // фото уходят по одному, за исправлением
        await updateRequest(id, { ...data, photos: [] })
        await queuePhotos({ request: id }, at, data.photos, author)
      }
      toCard()
      return
    }
    // фото уходят по одному, за заявкой
    const created = await createRequest({ ...data, photos: [] })
    await queuePhotos({ request: created.id }, at, data.photos, author)
    Notify.create({ message: t('Заявка отправлена: {n}', { n: created.number }), position: 'bottom' })
    void router.replace({ name: 'pwa-request', params: { id: created.id } })
  } catch (e) {
    if (props.id && isNetworkError(e)) {
      // связи нет — исправление ждёт на телефоне и уйдёт само
      await queueEdit(Number(props.id), data, author, view)
      Notify.create({
        message: t('Связи нет. Исправление сохранено на телефоне и отправится само'),
        position: 'bottom',
      })
      toCard()
      return
    }
    if (isNetworkError(e)) {
      // связи нет — заявка ждёт на телефоне и уйдёт сама
      await queueRequest(data, author, view)
      Notify.create({ message: t('Связи нет. Заявка сохранена на телефоне и отправится сама'), position: 'bottom' })
      void router.replace({ name: 'pwa-requests' })
      return
    }
    error.value = e instanceof Error ? t(e.message) : String(e)
  } finally {
    running.value = false
  }
}

// черновик исправления: из заявки в очереди или из заявки с сервера (без связи — из копии на телефоне) с учётом
// исправления, которое уже ждёт отправки; фото в черновике — только новые, остальные живут сами по себе
async function fillDraft() {
  const queued = isQueued.value ? outboxItem(props.id) : queuedEdit(Number(props.id))
  if (isQueued.value && !queued) {
    error.value = t('Заявка уже ушла на сервер — откройте её в списке заявок')
    return
  }
  const r = isQueued.value ? null : withQueuedEdit((await loadOffline(Number(props.id))).data)
  Object.assign(draft, {
    wagon: queued?.draft.wagon ?? r!.wagon.id,
    trip: queued ? queued.draft.trip : (r!.trip?.id ?? null),
    faultType: queued?.draft.faultType ?? r!.faultType,
    severityType: queued?.draft.severityType ?? r!.severityType,
    description: queued?.draft.description ?? r!.description,
    executorUsr: queued?.draft.executorUsr ?? null,
  })
  wagonNumber.value = queued?.wagonNumber ?? r!.wagon.number
  wagonType.value = queued?.wagonType ?? r!.wagon.wagonType
  requestTrip.value = queued ? toRaw(queued.trip) : r!.trip
}

// загрузка — сразу в setup и в счёт ожидания страницы: спиннер виден с первой отрисовки
void busy
  .run(async () => {
    // дерево неисправностей — главное показывающее шага 1: без групп и видов неисправностей его не показать
    await Promise.all([dictManager.prepareDicts('FaultGroup', 'FaultType'), loadCurrentUser().catch(notifyError)])
    const frequentGroup = frequentGroupId()
    if (frequentGroup !== null) openGroups.value = [frequentGroup]
    if (props.id) {
      try {
        await fillDraft()
      } catch (e) {
        error.value = e instanceof Error ? t(e.message) : String(e)
      }
    }
  })
  .catch(notifyError)

// список вагонов — свой контрол, страница его не ждёт; новая заявка — по умолчанию вагон проводника
if (!props.id) {
  void pick.load().then(() => {
    if (pick.myWagon.value && !draft.wagon) chooseWagon(pick.myWagon.value)
  })
}
</script>

<style scoped lang="scss">
.wizard__bars {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.3rem;
}

.wizard__bar {
  height: 0.3rem;
  border-radius: 0.1rem;
  background: $zs-border;

  &--on {
    background: $primary;
  }
}

.wizard__input,
.wizard__select,
.wizard__textarea {
  width: 100%;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 1rem;

  &:focus {
    border-color: $primary;
    outline: none;
  }
}

.wizard__input,
.wizard__select {
  height: 3.3rem;
}

.wizard__input--number {
  font-size: 1.1rem;
}

.wizard__textarea {
  resize: none;
}

.wizard__caption {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

// подпись группы на странице (не внутри кнопки или строки)
div.wizard__caption {
  // отступ под подписью группы — на треть меньше промежутка между блоками (1rem)
  margin-bottom: calc(-1rem / 3);
}

.wizard__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.wizard__chip {
  min-height: 2.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 62.5rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  text-align: left;
  cursor: pointer;

  &--small {
    min-height: 2.1rem;
    font-size: 0.8rem;
  }

  &--on {
    border-color: $primary;
    background: $primary;
    color: #fff;
  }

  &--faded {
    opacity: 0.45;
  }
}

.wizard__tree,
.wizard__list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.wizard__cat,
.wizard__group,
.wizard__fault,
.wizard__wagon {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border: 0;
  border-top: 0.0625rem solid $zs-border-row;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.wizard__cat {
  min-height: 2.8rem;
  background: $zs-bg-header;
  font-size: 0.9rem;
  font-weight: 700;

  &:first-child {
    border-top: 0;
  }
}

.wizard__cat-mark {
  flex: none;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 0.1rem;
}

.wizard__group {
  min-height: 2.5rem;
  font-size: 0.8rem;
  font-weight: 600;
}

.wizard__fault {
  min-height: 2.4rem;
  font-size: 0.8rem;
  line-height: 1.3;

  &--on {
    background: $zs-selected-bg;
    font-weight: 600;
  }
}

.wizard__chevron--closed {
  transform: rotate(-90deg);
}

.wizard__grow {
  flex: 1;
  min-width: 0;
}

.wizard__count {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;
}

.wizard__check {
  color: $primary;
  font-size: 1rem;
  font-weight: 700;
}

.wizard__empty {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.wizard__field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.wizard__label {
  font-size: 0.9rem;
  font-weight: 600;
}

// надпись шага на странице: под ней отступ как у подписи поля (0.5rem вместо промежутка между блоками 1rem)
div.wizard__label {
  margin-bottom: -0.5rem;
}

.wizard__mine {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  border: 0.1rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &--on {
    border-color: $primary;
    background: $zs-row-selected-bg;
  }
}

.wizard__mine-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.1rem;
}

.wizard__mine-number {
  font-size: 1.3rem;
  font-weight: 700;
}

.wizard__muted {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.wizard__wagon {
  min-height: 3.3rem;

  &:first-child {
    border-top: 0;
  }

  &--on {
    background: $zs-row-selected-bg;
  }
}

.wizard__wagon-number {
  font-size: 1rem;
  font-weight: 700;
}

.wizard__goes {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  border-radius: 0.4rem;
  background: $zs-row-selected-bg;
}

.wizard__goes-to {
  font-size: 1.1rem;
  font-weight: 700;
}

.wizard__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.wizard__nav {
  display: flex;
  gap: 0.6rem;
}

.wizard__btn {
  min-height: 3.5rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;

  &--primary {
    flex: 1;
    border: 0;
    background: $primary;
    color: #fff;
    font-size: 1.1rem;
    font-weight: 700;
  }

  &:disabled {
    opacity: 0.4;
  }
}
</style>
