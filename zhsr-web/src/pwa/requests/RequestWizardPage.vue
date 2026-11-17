<template>
  <!-- шаг вагона: страница не прокручивается, прокручивается только список вагонов -->
  <PwaPage :title="pageTitle" :back="cancel" :fill="stepName === 'wagon'">
    <!-- прогресс шагов — у нижнего края шапки -->
    <template #bottom>
      <div class="wizard__bars q-px-sm">
        <span v-for="i in 3" :key="i" class="wizard__bar" :class="{ 'wizard__bar--on': i - 1 <= step }" />
      </div>
    </template>
    <!-- главное показывающее страницы: общий спиннер на всю цепочку загрузок -->
    <LoadSpinner v-if="busy.active.value" />

    <!-- шаг неисправности -->
    <template v-if="stepName === 'fault'">
      <!-- неисправность — тот же комбобокс, что на десктопе: дерево «служба → группа → неисправность» в списке -->
      <div class="wizard__fault-step">
        <FaultPicker :model-value="draft.faultType || null" @update:model-value="onFaultList" />
        <!-- частые: выбор в списке их скрывает; нажатая кнопка повторным нажатием не отжимается -->
        <CbButton
          v-if="!faultFromList"
          :model-value="draft.faultType || null"
          single
          required
          wrap
          :columns="2"
          :values="frequent.map((f) => ({ id: f.id, text: f.name }))"
          @update:model-value="onFaultPick(typeof $event === 'number' ? $event : null)"
        />
      </div>
    </template>

    <!-- шаг деталей -->
    <template v-if="stepName === 'details'">
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Критичность') }}</span>
        <CbSegment
          :model-value="draft.severityType"
          single
          required
          :values="severities"
          :max-button-count="4"
          @update:model-value="draft.severityType = Number($event)"
        />
      </div>
      <div v-if="canChooseExecutor" class="wizard__field">
        <span class="wizard__label">{{ t('Исполнитель') }}</span>
        <CbList
          :model-value="draft.executorUsr"
          single
          placeholder="Не назначать — возьмёт любой сотрудник службы"
          :values="staffChoices"
          @update:model-value="draft.executorUsr = typeof $event === 'number' ? $event : null"
        >
          <template #option="{ item }">
            <UserOption
              :name="(item.data?.candidate as StaffCandidate | undefined)?.usr.name ?? item.text ?? ''"
              :me="isMe({ id: Number(item.id) })"
              v-bind="staffRow((item.data?.candidate as StaffCandidate | undefined) ?? null, wagonNumber || null)"
            />
          </template>
        </CbList>
      </div>
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Описание') }}</span>
        <NoteInput v-model="draft.description" placeholder="Например: в 3-м купе не закрывается окно" />
      </div>
      <div class="wizard__field">
        <span class="wizard__label">{{ t('Фото • {n} из 5', { n: draft.photos.length }) }}</span>
        <PhotoPicker v-model="draft.photos" :max="5" large compress />
      </div>
    </template>

    <!-- шаг вагона — три режима ввода; смена режима очищает выбранный вагон и цифры -->
    <template v-if="stepName === 'wagon'">
      <WagonModePicker :user="currentUser" :selected="selectedWagon" @pick="onWagonPick" />
    </template>

    <div v-if="error" class="wizard__error q-py-sm q-px-md">{{ error }}</div>

    <template #bar>
      <BottomBar :actions="navActions" @action="(key) => (key === 'back' ? back() : next())" />
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, reactive, ref, shallowRef, toRaw, watch } from 'vue'
import { useRouter } from 'vue-router'

import { isNetworkError } from '@/api/http'
import type { RequestDraft, StaffCandidate } from '@/api/requests'
import { createRequest, fetchServiceStaff, updateRequest } from '@/api/requests'
import type { TripRef, RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import { randomGuid } from '@/shared/db/db'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import { provideBusy } from '@/shared/busy'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { notifyError } from '@/shared/errors'
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
import FaultPicker from '@/shared/requests/FaultPicker.vue'
import { faultDefaults, frequentFaults } from '@/shared/requests/faults'
import { Severity } from '@/shared/constants/systemDicts'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import { faultName } from '@/shared/requests/requestView'
import { canAssignOnCreate, staffTrip } from '@/shared/requests/rights'
import { loadOffline } from '@/shared/requests/useRequestCard'
import { currentUser, isMe, loadCurrentUser, userName } from '@/shared/user'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'
import PwaPage from '../components/PwaPage.vue'
import WagonModePicker from '../components/WagonModePicker.vue'
import { staffRow } from '@/shared/requests/staffRow'
import UserOption from '@/shared/components/UserOption.vue'
import { sortStaff } from '@/shared/requests/staff'

/**
 * Подача заявки на телефоне в 3 шага (Ф-ЗАЯ-01): вагон (мой вагон, состав рейса или поиск по номеру), неисправность,
 * детали (критичность, исполнитель, описание, фото); при исправлении вагон — последним шагом. Критичность подставляется из неисправности и её можно изменить;
 * служба определяется неисправностью, рейс — вагоном: в форме их нет. Без связи заявка встаёт в очередь на телефоне и отправится сама.
 *
 * Задан id — исправление заявки (Ф-ЗАЯ-07): поля заполнены из заявки. Без связи исправление встаёт в очередь на
 * телефоне и отправится само. Id «local-…» — заявка из очереди: она ещё не на сервере, исправляется прямо в очереди.
 */
const props = withDefaults(defineProps<{ id?: string }>(), { id: '' })

const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

const step = ref(0)
// порядок шагов: подача — вагон, неисправность, детали; исправление — вагон последним: чаще исправляют неисправность и
// описание
const stepName = computed(() =>
  props.id
    ? (['fault', 'details', 'wagon'] as const)[step.value]
    : (['wagon', 'fault', 'details'] as const)[step.value],
)

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
  if (stepName.value === 'details' && draft.faultType) return faultName(draft.faultType)
  return stepName.value === 'wagon' ? t('Место неисправности') : t('Вид неисправности')
})

const running = ref(false)
const error = ref('')

// исправляется заявка из очереди на телефоне: на сервере её ещё нет
const isQueued = computed(() => props.id.startsWith('local-'))

// --- шаг неисправности ---

// частые — неисправности в избранном; дерево «служба → группа → неисправность» — в списке FaultPicker
const frequent = computed(() => frequentFaults())

// неисправность выбрана в списке (комбобоксе): частые скрыты, пока её не стёрли
const faultFromList = ref(false)

function onFaultList(id: number | null) {
  faultFromList.value = id !== null
  onFaultPick(id)
}

// неисправность в поле выбора: выбрана — как кнопкой частых, стёрта — не выбрана ни одна частая
function onFaultPick(id: number | null) {
  // 0 — неисправность не выбрана
  if (id === null) draft.faultType = 0
  else pickFault(id)
}

// неисправность выбрана — критичность по умолчанию из неё
function pickFault(id: number) {
  draft.faultType = id
  draft.severityType = faultDefaults(id).severity
}

// --- шаг деталей ---

const severities = computed(() => [...dictChoices('Severity')].reverse().map((c) => ({ ...c, text: c.text ?? '' })))
// служба определяется неисправностью
const service = computed(() => (draft.faultType ? faultDefaults(draft.faultType).service : null))

// выбранный вагон: от его рейса зависит, может ли ЛНП выбрать исполнителя
const pickedWagon = shallowRef<RequestWagon | null>(null)
// ЛНП выбирает исполнителя по вагону своего рейса: вагон выбран на первом шаге (не выбран — считается вагоном текущего
// рейса пользователя); вагон не из рейса ЛНП — поля нет
const canChooseExecutor = computed(() => {
  const user = currentUser.value
  if (props.id || !user) return false
  const wagonTrip = pickedWagon.value ? (pickedWagon.value.trip?.id ?? null) : (user.trip?.id ?? null)
  return canAssignOnCreate(user, wagonTrip)
})
const staff = shallowRef<StaffCandidate[]>([])
// у сотрудника бригады рейса справа — должность и вагоны (UserOption); предпочтительные — первыми
const staffChoices = computed(() =>
  sortStaff(staff.value, wagonNumber.value || null).map((c) => ({
    id: c.usr.id,
    text: userName(c.usr),
    data: { candidate: c },
  })),
)
// ЛНП выбирает исполнителя из бригады рейса вагона (пока вагон не выбран — своего рейса), мастер депо — из всех
// сотрудников службы
const executorTrip = computed(() => {
  const user = currentUser.value
  if (!user) return null
  return staffTrip(user, pickedWagon.value ? (pickedWagon.value.trip?.id ?? null) : (user.trip?.id ?? null))
})
watch(
  // источники по отдельности: watch сравнивает каждый, а не новый массив — иначе любой пересчёт стирал выбранного
  // исполнителя
  [() => (canChooseExecutor.value ? service.value : null), executorTrip],
  async ([id, trip]) => {
    draft.executorUsr = null
    staff.value = id && isOnline.value ? await fetchServiceStaff(id, trip) : []
  },
)

// --- шаг вагона ---

const requestTrip = shallowRef<TripRef | null>(null)

// выбранный вагон для выбора вагона (WagonModePicker): номер и тип — из черновика
const selectedWagon = computed(() =>
  draft.wagon ? { id: draft.wagon, number: wagonNumber.value, wagonType: wagonType.value } : null,
)

function onWagonPick(w: RequestWagon | null) {
  if (w) chooseWagon(w)
  else clearWagon()
}

function clearWagon() {
  draft.wagon = 0
  draft.trip = null
  wagonNumber.value = ''
  wagonType.value = 0
  pickedWagon.value = null
}

function chooseWagon(w: RequestWagon) {
  draft.wagon = w.id
  wagonNumber.value = w.number
  wagonType.value = w.wagonType
  pickedWagon.value = w
  draft.trip = w.trip?.id ?? null
}

// выбранный рейс для показа, пока черновик в очереди; у рейса пользователя плановое отправление — его отправление
function chosenTrip(): TripRef | null {
  const u = currentUser.value?.trip
  const trips = [pickedWagon.value?.trip, requestTrip.value, u && { ...u, plannedDt: u.departure }]
  const trip = trips.find((x) => x?.id === draft.trip)
  return trip ? { id: trip.id, trainNumber: trip.trainNumber, name: trip.name, plannedDt: trip.plannedDt } : null
}

// --- шаги ---

const canNext = computed(() => {
  if (stepName.value === 'wagon') return !!draft.wagon
  return stepName.value === 'fault' ? !!draft.faultType : true
})

// кнопки мастера: назад по шагам (с первого — отмена) и далее (на последнем — отправить или сохранить)
const navActions = computed<BottomBarAction[]>(() => [
  { key: 'back', title: step.value === 0 ? 'Отмена' : 'Назад', align: 'left' },
  {
    key: 'next',
    title: step.value < 2 ? 'Далее' : props.id ? 'Сохранить' : 'Отправить',
    kind: 'primary',
    disabled: !canNext.value || running.value,
  },
])

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
  // неисправность заявки не из частых — как выбранная в списке: частые скрыты
  faultFromList.value = !frequent.value.some((f) => f.id === draft.faultType)
  wagonNumber.value = queued?.wagonNumber ?? r!.wagon.number
  wagonType.value = queued?.wagonType ?? r!.wagon.wagonType
  requestTrip.value = queued ? toRaw(queued.trip) : r!.trip
}

// загрузка — сразу в setup и в счёт ожидания страницы: спиннер виден с первой отрисовки
void busy
  .run(async () => {
    // дерево неисправностей — главное показывающее шага 1: без групп и видов неисправностей его не показать
    await Promise.all([dictManager.prepareDicts('FaultGroup', 'FaultType'), loadCurrentUser().catch(notifyError)])
    if (props.id) {
      try {
        await fillDraft()
      } catch (e) {
        error.value = e instanceof Error ? t(e.message) : String(e)
      }
    }
  })
  .catch(notifyError)
</script>

<style scoped lang="scss">
.wizard__bars {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.3rem;
}

.wizard__bar {
  height: 0.2rem;
  border-radius: 0.1rem;
  background: $zs-border;

  // пройденные шаги и текущий — цветом кнопки
  &--on {
    background: $primary;
  }
}

.wizard__input {
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

.wizard__input {
  height: 3.3rem;
}

.wizard__input--number {
  font-size: 1.1rem;
}

.wizard__caption {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 700;
}

// подпись группы на странице (не внутри кнопки или строки)
div.wizard__caption {
  // отступ под подписью группы — на треть меньше промежутка между блоками (1rem)
  margin-bottom: calc(-1rem / 3);
}

// частые неисправности: сетка в две колонки, текст переносится по словам внутри кнопки — без пустого места

// шаг неисправности: поле выбора и частые — плотнее, чем блоки страницы
.wizard__fault-step {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: -0.5rem;
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

.wizard__error {
  flex: none;
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
