<template>
  <PwaPage :stale="stale">
    <!-- текущий рейс пользователя или соседний (по ссылке) -->
    <div v-if="trip" class="today__trip q-pa-md">
      <!-- заголовок, статус -->
      <div class="today__trip-head">
        <div class="today__trip-name">{{ trip.trainNumber }} • {{ trip.name }}</div>
        <div class="today__trip-badges"><DictBadge :id="trip.tripStatusType" dict="TripState" /></div>
      </div>
      <div class="today__tiles">
        <div v-if="trip.crewDutyType === CrewDuty.CONDUCTOR && trip.wagons.length > 0" class="today__tile">
          <span class="today__tile-key">{{ t('Мой вагон') }}</span>
          <span class="today__tile-value zs-mono">{{ trip.wagons.join(', ') }}</span>
        </div>
        <div
          v-else
          role="button"
          tabindex="0"
          class="today__tile q-py-sm q-px-md"
          :class="{ 'today__tile--action': canDepart || fixDeparture }"
          @click="canDepart ? openMark(true) : fixDeparture && openFix(true)"
        >
          <span class="today__tile-key">{{ trip.departedDt ? t('Отправление') : t('Плановое отправление') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.departedDt ?? trip.plannedDt) }}</span>
          <span v-if="trip.departedDt" class="today__tile-delay">{{ delayText(trip.plannedDt, trip.departedDt) }}</span>
        </div>
        <div
          role="button"
          tabindex="0"
          class="today__tile q-py-sm q-px-md"
          :class="{ 'today__tile--action': canArrive || fixArrival }"
          @click="canArrive ? openMark(false) : fixArrival && openFix(false)"
        >
          <span class="today__tile-key">{{ trip.arrivedDt ? t('Прибытие') : t('Плановое прибытие') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.arrivedDt ?? trip.arrival) }}</span>
          <span v-if="trip.arrivedDt" class="today__tile-delay">{{ delayText(trip.arrival, trip.arrivedDt) }}</span>
        </div>
      </div>
      <!-- ошибка отметки отправления или прибытия — здесь, в карточке рейса, а не в окне даты -->
      <ErrorBox
        v-if="markError"
        :error="markError"
        closable
        class="today__error q-py-sm q-px-md"
        @close="markError = null"
      />
      <!-- плановые прицепки и отцепки рейса — под плитками, над ними разделитель; «Отцепка» / «Прицепка» — ниже, под
           своим разделителем. Слева операция, справа кнопки; ожидающие, подтверждённые и отменённые (с кнопкой «Вернуть
           план») — все строками; без прав и связи кнопок нет -->
      <div v-if="planRows.length > 0" class="today__plans q-pt-md">
        <div v-for="c in planRows" :key="c.id" class="today__plan q-py-xs">
          <!-- три строки: операция и вагон; станция и время по плану; состояние — «не подтверждён», «просрочен»,
               «выполнено 21.12 23:00», «отменено» -->
          <div class="today__plan-text">
            <span class="today__plan-what">{{ planTitle(c) }}</span>
            <span class="today__plan-where">{{ planWhere(c) }}</span>
            <span v-if="statusText(c)" :class="statusClass(c)">{{ statusText(c) }}</span>
          </div>
          <div v-if="planButtons(c) && isOnline" class="today__plan-btns">
            <!-- подтверждённый и выполненный: «Отменить» — удалить факт (план снова ожидает); после факта вагон ещё
                 прицепляли или отцепляли — серая, нажатие объясняет, что сначала отменить -->
            <q-btn
              v-if="isFactRow(c) || isDone(c)"
              unelevated
              outline
              no-caps
              :color="undoLast(c) ? 'primary' : 'grey-6'"
              class="zs-soft-border"
              :disable="markRunning"
              :label="t('Отменить')"
              @click="undoing = c"
            />
            <q-btn
              v-else-if="isCancelled(c)"
              unelevated
              outline
              no-caps
              color="primary"
              class="zs-soft-border"
              :disable="markRunning"
              :label="t('Вернуть план')"
              @click="restorePlan(c)"
            />
            <template v-else>
              <q-btn
                v-if="c.canMark"
                unelevated
                no-caps
                color="primary"
                :label="t('Подтвердить')"
                @click="router.push({ name: 'pwa-trip-mark', params: { id: trip.id, change: c.id } })"
              />
              <q-btn
                unelevated
                outline
                no-caps
                color="primary"
                class="zs-soft-border"
                :disable="markRunning"
                :label="t('Отменить план')"
                @click="cancelling = c"
              />
            </template>
          </div>
        </div>
      </div>
      <div v-if="canArrive" class="today__coupling q-pt-md">
        <router-link :to="couplingLink(trip.id, 'detach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Отцепка')
        }}</router-link>
        <router-link :to="couplingLink(trip.id, 'attach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Прицепка')
        }}</router-link>
      </div>
      <!-- отметку можно отменить, пока её можно исправить: 10 минут после неё -->
      <q-btn
        v-if="fixDeparture || fixArrival"
        unelevated
        outline
        no-caps
        color="error"
        class="bg-white text-weight-bold q-py-sm q-px-md"
        :disable="markRunning"
        :label="fixDeparture ? t('Отменить отметку об отправлении') : t('Отменить отметку о прибытии')"
        @click="cancelMark(fixDeparture)"
      />
      <SoftLink v-if="switchTo" :text="switchText" class="today__switch" @click="showOtherTrip = !showOtherTrip" />
    </div>

    <!-- начальник поезда распределяет проводников по вагонам; оформление — как у разделов заявок -->
    <section v-if="trip?.crewDutyType === CrewDuty.LNP" class="today__crew">
      <div class="today__crew-card q-pa-md">
        <div v-if="norm" class="today__crew-attrs">
          <!-- «Вагоны: 7, без ответственных 4» — вагоны поезда рейса, за которые никто из бригады не отвечает -->
          <span>{{ t('Вагоны') }}</span>
          <span
            >{{ norm.wagons.length
            }}<template v-if="noResponsible > 0"
              >,
              <span class="today__norm q-py-none q-px-sm">{{
                t('без ответственных {n}', { n: noResponsible })
              }}</span></template
            ><SoftLink
              text="Состав"
              class="q-ml-sm"
              @click="router.push({ name: 'pwa-trip-wagons', params: { id: trip.id } })"
          /></span>
          <span>{{ t('ЛНП') }}</span>
          <span
            ><UserName v-if="member(CrewDuty.LNP)" :user="member(CrewDuty.LNP)!.usr" /><template v-else>{{
              t('не назначен')
            }}</template></span
          >
          <span>{{ t('ПЭМ') }}</span>
          <span
            ><UserName v-if="member(CrewDuty.PEM)" :user="member(CrewDuty.PEM)!.usr" /><template v-else>{{
              t('не назначен')
            }}</template></span
          >
          <!-- «Проводники: 10, не назначено 2 проводника» — проводники рейса без вагона; строкой ниже ПЭМ -->
          <span>{{ t('Проводники') }}</span>
          <span
            >{{ conductorCount
            }}<template v-if="norm.pool.length > 0"
              >,
              <span class="today__norm q-py-none q-px-sm">{{
                t('не назначено {n} проводников', norm.pool.length)
              }}</span></template
            ></span
          >
        </div>
        <!-- ЛНП меняет ответственных за вагоны поезда (Ф-РЕЙ-08) — на своём экране, кнопками в карточке бригады;
             рядом — план ответственных после рейса, пока рейс не прибыл; у прибывшего рейса первая кнопка правит
             текущих ответственных после рейса — так она и называется -->
        <div v-if="isChief && trip.tripStatusType !== TripStatus.CANCELLED" class="today__coupling q-pt-md">
          <router-link
            :to="{ name: 'pwa-trip-responsibles', params: { id: trip.id } }"
            class="today__coupling-link q-py-sm q-px-md"
            >{{
              trip.tripStatusType === TripStatus.DONE ? t('Ответственные после рейса') : t('Ответственные на рейс')
            }}</router-link
          >
          <router-link
            v-if="trip.tripStatusType === TripStatus.PLANNED || trip.tripStatusType === TripStatus.IN_PROGRESS"
            :to="{ name: 'pwa-trip-responsibles', params: { id: trip.id }, query: { after: '1' } }"
            class="today__coupling-link q-py-sm q-px-md"
            >{{ t('Ответственные после рейса') }}</router-link
          >
        </div>
      </div>
    </section>

    <!-- мастер депо: рейсы в пути через станцию депо -->
    <div v-for="tr in depotTrips" :key="tr.id" class="today__trip q-pa-md">
      <div class="today__trip-name">{{ tr.trainNumber }} • {{ tr.name }}</div>
      <div class="today__coupling q-pt-md">
        <router-link :to="couplingLink(tr.id, 'detach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Отцепка')
        }}</router-link>
        <router-link :to="couplingLink(tr.id, 'attach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Прицепка')
        }}</router-link>
      </div>
    </div>

    <ErrorBox v-if="error" :error="error" :retry="load" class="today__error q-py-sm q-px-md" />
    <LoadSpinner v-if="busy.active.value" />

    <!-- отметка отправления или прибытия: календарь во весь экран, без заголовка -->
    <CalendarDialog
      v-model="markTime"
      :show="marking !== null"
      with-time
      :label="marking ? 'Дата отправления' : 'Дата прибытия'"
      :running="markRunning"
      :ok-text="fixing ? 'Сохранить время' : 'Подтвердить'"
      @update:show="(v) => !v && (marking = null)"
      @confirm="confirmMark"
    />
    <!-- отмена плана — с подтверждением -->
    <BottomSheet
      :show="cancelling !== null"
      :title="t('Отменить план?')"
      @update:show="(v) => !v && (cancelling = null)"
    >
      <!-- операция и вагон — жирным; место и время, пояснение — каждое с новой строки -->
      <div v-if="cancelling" class="today__sheet-text">
        <b>{{ planTitle(cancelling) }}</b>
        <span>{{ planWhere(cancelling) }}</span>
        <span>{{ t('План останется в списке, его можно вернуть.') }}</span>
      </div>
      <template #buttons>
        <q-btn
          unelevated
          outline
          no-caps
          color="ink"
          no-wrap
          class="today__sheet-btn zs-pwa-btn zs-pwa-btn--lg zs-soft-border"
          :label="t('Не отменять')"
          @click="cancelling = null"
        />
        <q-btn
          unelevated
          no-caps
          color="primary"
          no-wrap
          class="today__sheet-btn zs-pwa-btn zs-pwa-btn--lg"
          :loading="markRunning"
          :label="t('Отменить план')"
          @click="confirmCancel"
        />
      </template>
    </BottomSheet>
    <!-- отмена выполнения или подтверждения — с подтверждением; не последний факт поезда — почему нельзя и что
         сначала отменить -->
    <BottomSheet :show="undoing !== null" :title="undoTitle" @update:show="(v) => !v && (undoing = null)">
      <div v-if="undoing" class="today__sheet-text">
        <b>{{ planTitle(undoing) }}</b>
        <span>{{ planWhere(undoing) }}</span>
        <template v-if="undoBlockers">
          <span class="q-mt-sm">{{ t('После этого вагон ещё прицепляли или отцепляли.') }}</span>
          <span>{{ t('Сначала отмените, начиная с последней:') }}</span>
          <span v-for="b in undoBlockers" :key="b.id">• {{ planTitle(b) }} — {{ planWhere(b) }}</span>
        </template>
        <span v-else>{{
          isFactRow(undoing)
            ? t('Прицепка или отцепка удаляется.')
            : t('Отметка выполнения удаляется, план снова ожидает.')
        }}</span>
      </div>
      <template #buttons>
        <q-btn
          v-if="undoBlockers"
          unelevated
          no-caps
          color="primary"
          no-wrap
          class="today__sheet-btn zs-pwa-btn zs-pwa-btn--lg"
          :label="t('Понятно')"
          @click="undoing = null"
        />
        <template v-else>
          <q-btn
            unelevated
            outline
            no-caps
            color="ink"
            no-wrap
            class="today__sheet-btn zs-pwa-btn zs-pwa-btn--lg zs-soft-border"
            :label="t('Не отменять')"
            @click="undoing = null"
          />
          <q-btn
            unelevated
            no-caps
            color="primary"
            no-wrap
            class="today__sheet-btn zs-pwa-btn zs-pwa-btn--lg"
            :loading="markRunning"
            :label="t('Отменить')"
            @click="confirmUndo"
          />
        </template>
      </template>
    </BottomSheet>
  </PwaPage>
</template>

<script setup lang="ts">
import { date, Notify } from 'quasar'
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import UserName from '@/shared/components/UserName.vue'
import type { TrainChange } from '@/api/trains'
import { cancelChangePlan, restoreChangePlan, undoChange } from '@/api/trains'
import type { TripRow, TripCrew } from '@/api/trips'
import {
  cancelArrival,
  cancelDeparture,
  fetchTripCrew,
  fetchTrips,
  markArrival,
  markDeparture,
  retimeArrival,
  retimeDeparture,
} from '@/api/trips'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { dictManager } from '@/shared/dict/DictManager'
import { provideBusy } from '@/shared/busy'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { formError, notifyError, shownError } from '@/shared/errors'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { loadCached } from '@/shared/offline/cache'
import { CrewDuty, LocationType, RoleType, TripStatus } from '@/shared/constants/systemDicts'
import { TrainWagonChangeType, TrainWagonKind, TrainWagonPlanStatus } from '@/shared/constants/TrainWagonChange'
import { isWaiting, laterFactsOfWagon } from '@/shared/trains/trainJournal'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import { crewNorm, normDone } from '@/shared/trips/crewNorm'
import { delayText, markEditable, useNow } from '@/shared/trips/tripTimes'
import BottomSheet from '../components/BottomSheet.vue'
import CalendarDialog from '../components/CalendarDialog.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import PwaPage from '../components/PwaPage.vue'
import { showOtherTrip } from './homeState'

/**
 * Главный экран мобильной части «Рейс»: текущий рейс пользователя — без заявок (они на вкладке «Заявки»):
 * - у начальника поезда в карточке рейса — плановые прицепки и отцепки, клики по отправлению и прибытию отмечают их,
 *   ниже — карточка бригады с ответственными и составом;
 * - мастер депо и администратор — рейсы в пути с переходом к отцепке и прицепке.
 * Без связи показываются сохранённые на телефоне данные.
 */

const user = computed(() => currentUser.value)
// показан текущий рейс или, по ссылке, соседний — предыдущий или следующий
const trip = computed(() => (showOtherTrip.value && user.value?.otherTrip) || user.value?.trip || null)
// рейс, на который переключает ссылка
const switchTo = computed(() => (showOtherTrip.value ? user.value?.trip : user.value?.otherTrip) ?? null)
const switchText = computed(() =>
  switchTo.value?.tripStatusType === TripStatus.DONE ? 'Показать предыдущий рейс' : 'Показать следующий рейс',
)

const router = useRouter()

const stale = ref(false)
const error = ref<ShownError | null>(null)
const crew = shallowRef<TripCrew | null>(null)
const norm = computed(() => (crew.value ? crewNorm(crew.value) : null))

/** Проводников в бригаде рейса. */
const conductorCount = computed(() => crew.value?.crew.filter((m) => m.crewDutyType === CrewDuty.CONDUCTOR).length ?? 0)
/** Вагоны поезда рейса, за которые никто из бригады не отвечает. */
const noResponsible = computed(() => {
  const n = norm.value
  if (!n || !crew.value) return 0
  const covered = new Set(crew.value.crew.flatMap((m) => m.wagons))
  return n.wagons.filter((w) => !covered.has(w.number)).length
})

// сотрудник бригады на должности; нет — undefined («не назначен»)
const member = (duty: number) => crew.value?.crew.find((x) => x.crewDutyType === duty)

// отправление и прибытие отмечают начальник поезда и администратор; нужна связь
const isChief = computed(
  () => (trip.value?.crewDutyType === CrewDuty.LNP || hasRole(user.value, RoleType.ADMIN)) && isOnline.value,
)
const canDepart = computed(() => isChief.value && trip.value?.tripStatusType === TripStatus.PLANNED)
const canArrive = computed(() => isChief.value && trip.value?.tripStatusType === TripStatus.IN_PROGRESS)

// отметку можно исправить или отменить 10 минут после неё; выполненный рейс остаётся текущим, пока нет нового
const now = useNow()
const fixDeparture = computed(() => canArrive.value && markEditable(trip.value?.departureMarkedDt, now.value))
const fixArrival = computed(
  () =>
    isChief.value &&
    trip.value?.tripStatusType === TripStatus.DONE &&
    markEditable(trip.value.arrivalMarkedDt, now.value),
)

const couplingLink = (id: number, kind: string) => ({ name: 'pwa-trip-coupling', params: { id, kind } })

// рейсы в пути для отцепки и прицепки: у мастера сервер ограничивает их станцией депо, администратору видны все
const depotTrips = shallowRef<TripRow[]>([])

// отметка: true — отправление, false — прибытие, null — лист закрыт
const marking = ref<boolean | null>(null)
const markTime = ref<string | null>(null)
const markError = ref<ShownError | null>(null)
const markRunning = ref(false)

// исправляется время уже сделанной отметки
const fixing = ref(false)

/**
 * Почему отправление или прибытие сейчас не отметить — те же проверки, что на сервере, по формированию рейса; можно —
 * пусто. Формирование не загружено (не ЛНП рейса) — проверяет сервер.
 */
function markBlock(depart: boolean): string {
  const c = crew.value
  if (!c) return ''
  if (!depart) {
    const waiting = plannedChanges.value.map((x) => x.wagon.number)
    return waiting.length
      ? t('Отметьте или отмените плановые прицепки и отцепки рейса: {list}', { list: waiting.join(', ') })
      : ''
  }
  if (!c.trip.train) return t('Поезд рейса не назначен')
  if (c.pending.length > 0) {
    return t('Отметьте или отмените плановые прицепки и отцепки до отправления: {list}', {
      list: c.pending.map((x) => x.wagon.number).join(', '),
    })
  }
  const riding = c.wagons.filter((w) => w.riding)
  if (riding.length === 0) return t('В поезде рейса нет вагонов')
  const away = riding
    .filter((w) => w.locationType !== LocationType.STATION || w.place !== c.trip.departureStation)
    .map((w) => w.number)
  if (away.length > 0) return t('Вагоны не на станции отправления: {list}', { list: away.join(', ') })
  if (!member(CrewDuty.LNP) || !member(CrewDuty.PEM)) return t('Не назначены начальник поезда и ПЭМ')
  const n = norm.value
  if (n && !normDone(n)) {
    return t('Не выполнена норма проводников: {list}', { list: [...n.notOk, ...n.tailBad].join(', ') })
  }
  return ''
}

/** Отметить отправление или прибытие: сначала проверки — не прошли, ошибка в карточке рейса, окно даты не открывается. */
function openMark(depart: boolean) {
  const block = markBlock(depart)
  if (block) {
    markError.value = formError(block)
    return
  }
  markTime.value = date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm')
  markError.value = null
  fixing.value = false
  marking.value = depart
}

/** Время отметки: не позже текущего, прибытие — позже отправления; не так — текст ошибки. */
function timeBlock(): string {
  const at = new Date(markTime.value!).getTime()
  if (at > Date.now()) return t('Укажите время не позже текущего')
  const departed = trip.value?.departedDt
  if (marking.value === false && departed && at <= Date.parse(departed))
    return t('Прибытие должно быть позже отправления')
  return ''
}

// плановые прицепки и отцепки рейса ЛНП: из формирования рейса
const plannedChanges = computed(() => (crew.value?.changes ?? []).filter(isWaiting))

// отменённые планы рейса — строками с кнопкой «Вернуть план»
const isCancelled = (c: TrainChange) => c.planStatus === TrainWagonPlanStatus.CANCELLED
const isDone = (c: TrainChange) => c.planStatus === TrainWagonPlanStatus.DONE
/** Строка — выполненная без плана прицепка или отцепка рейса (факт). */
const isFactRow = (c: TrainChange) => c.kind === TrainWagonKind.FACT
/** Факт, который отменяет «Отменить»: у факта — он сам, у подтверждённого плана — отработавший его факт. */
const undoFact = (c: TrainChange) => (isFactRow(c) ? c.id : c.fact)

/** Факт строки — последний у поезда: «Отменить» доступна. */
/** Более поздние факты того же вагона в рейсе — от последнего; факта строки нет в данных — пусто. */
function blockersOf(c: TrainChange): TrainChange[] {
  const facts = (crew.value?.changes ?? []).filter(isFactRow)
  const fact = facts.find((x) => x.id === undoFact(c))
  return fact ? laterFactsOfWagon(facts, fact) : []
}

/** «Отменить» доступна: после факта строки у этого вагона нет других прицепок и отцепок. */
const undoLast = (c: TrainChange) => blockersOf(c).length === 0

/** Что мешает отменить: более поздние факты того же вагона, от последнего; ничего — null, отменить можно. */
const undoBlockers = computed<TrainChange[] | null>(() => {
  const c = undoing.value
  if (!c) return null
  const list = blockersOf(c)
  return list.length > 0 ? list : null
})
const undoTitle = computed(() => {
  const c = undoing.value
  if (!c) return ''
  if (undoBlockers.value) return t('Отменить пока нельзя')
  return isFactRow(c) ? t('Отменить выполнение?') : t('Отменить подтверждение?')
})
// план, отмену которого подтверждают; null — лист закрыт
const cancelling = ref<TrainChange | null>(null)
// подтверждённый план, отмену подтверждения которого подтверждают; null — лист закрыт
const undoing = ref<TrainChange | null>(null)

/** Есть ли у строки кнопки: подтверждённый — отменить подтверждение, отменённый — вернуть, ожидающий — отменить. */
function planButtons(c: TrainChange): boolean {
  // «Отменить» — всегда при правах; не последний факт поезда — при нажатии объяснение, что сначала отменить
  if (isFactRow(c)) return c.canUndo
  if (isDone(c)) return c.canUndo && c.fact !== null
  return isCancelled(c) ? c.canRestore : c.canCancel
}
/** Подтверждённые планы рейса в списке: факт такого плана показан строкой плана. */
const shownDone = computed(() => new Set((crew.value?.done ?? []).map((c) => c.id)))

/**
 * Строки: ожидающие, подтверждённые и отменённые планы рейса и выполненные прицепки и отцепки рейса — факты без
 * плана или с планом не этого рейса (факт плана рейса показан строкой своего плана) — по времени.
 */
const planRows = computed(() =>
  [
    ...plannedChanges.value,
    ...(crew.value?.done ?? []),
    ...(crew.value?.cancelled ?? []),
    ...(crew.value?.changes ?? []).filter((c) => isFactRow(c) && (c.plan === null || !shownDone.value.has(c.plan))),
  ].sort((a, b) => Date.parse(rowTime(a)) - Date.parse(rowTime(b)) || a.id - b.id),
)

/** «Прицепка вагона 958694» или «Отцепка вагона 958694». */
const planTitle = (c: TrainChange) =>
  t(c.changeType === TrainWagonChangeType.ATTACH ? 'Прицепка вагона {n}' : 'Отцепка вагона {n}', {
    n: c.wagon.number,
  })

/** «Караганда, 23.12, в 21:32». */
function planWhere(c: TrainChange): string {
  const where = dictManager.getValue('Station', c.station)
  const d = new Date(c.dt)
  const at = { where, date: date.formatDate(d, 'DD.MM'), time: date.formatDate(d, 'HH:mm') }
  // ручная (без плана) — станция и время факта; у плана — его время с пометкой «(по плану)»
  return isFactRow(c) ? t('{where}, {date}, в {time}', at) : t('{where}, {date}, в {time} (по плану)', at)
}

/**
 * Состояние строки плана: «выполнено 21.12 23:00» (время факта), «отменено», «просрочен», «не подтверждён»; у ручной
 * (без плана) — пусто: время уже во второй строке.
 */
function statusText(c: TrainChange): string {
  if (isFactRow(c)) return ''
  if (isDone(c)) return t('выполнено {time}', { time: formatDateTime(rowTime(c)) })
  if (isCancelled(c)) return t('отменено')
  return c.overdue ? t('просрочен') : t('не подтверждён')
}

function statusClass(c: TrainChange): string {
  if (isFactRow(c) || isDone(c)) return 'today__plan-done'
  if (isCancelled(c)) return 'today__plan-cancelled'
  return c.overdue ? 'today__plan-overdue' : 'today__plan-waiting'
}

/**
 * Время строки: у подтверждённого плана — фактическое время отработавшего его факта (по нему и порядок отмены), у
 * остальных — своё. Факта нет в данных рейса — плановое.
 */
function rowTime(c: TrainChange): string {
  if (!isDone(c) || c.fact === null) return c.dt
  return (crew.value?.changes ?? []).find((x) => x.id === c.fact)?.dt ?? c.dt
}

// отменить план — кнопкой в строке, без окна: строка остаётся отменённым планом с кнопкой «Вернуть план»
async function cancelPlan(c: TrainChange) {
  markRunning.value = true
  try {
    await cancelChangePlan(c.id)
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    markRunning.value = false
  }
}

/** Подтверждённая отмена плана: лист закрывается после отмены. */
async function confirmCancel() {
  const c = cancelling.value
  if (!c) return
  await cancelPlan(c)
  cancelling.value = null
}

/** Подтверждённая отмена подтверждения: факт удаляется, план снова ожидает; ошибка — всплывашкой. */
async function confirmUndo() {
  const c = undoing.value
  const fact = c ? undoFact(c) : null
  if (!c || fact === null) return
  markRunning.value = true
  try {
    await undoChange(fact)
    undoing.value = null
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    markRunning.value = false
  }
}

// вернуть отменённый план; ошибка — всплывашкой
async function restorePlan(c: TrainChange) {
  markRunning.value = true
  try {
    await restoreChangePlan(c.id)
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    markRunning.value = false
  }
}

// ошибка отметки — в карточке рейса: окно даты закрывается, ошибок в нём не бывает
async function confirmMark() {
  const current = trip.value
  if (!current) return
  const block = timeBlock()
  if (block) {
    marking.value = null
    markError.value = formError(block)
    return
  }
  markRunning.value = true
  markError.value = null
  try {
    const value = isoDateTime(new Date(markTime.value!))
    if (fixing.value) await (marking.value ? retimeDeparture : retimeArrival)(current.id, value)
    else if (marking.value) await markDeparture(current.id, value)
    else await markArrival(current.id, value)
    marking.value = null
    await loadCurrentUser(true)
    await load()
  } catch (e) {
    marking.value = null
    markError.value = shownError(e)
  } finally {
    markRunning.value = false
  }
}

function openFix(depart: boolean) {
  const time = depart ? trip.value?.departedDt : trip.value?.arrivedDt
  markTime.value = date.formatDate(time ? new Date(time) : new Date(), 'YYYY-MM-DDTHH:mm')
  markError.value = null
  fixing.value = true
  marking.value = depart
}

// отменить отметку — кнопкой под панелью рейса, без окна; ошибка — всплывашкой
async function cancelMark(depart: boolean) {
  const current = trip.value
  if (!current) return
  markRunning.value = true
  try {
    await (depart ? cancelDeparture : cancelArrival)(current.id)
    Notify.create({ message: depart ? t('Отправление отменено') : t('Прибытие отменено'), position: 'bottom' })
    await loadCurrentUser(true)
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    markRunning.value = false
  }
}

// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      const [u] = await Promise.all([loadCurrentUser(), dictManager.prepareDicts('WagonType', 'Station')])
      await loadCrew()
      if (hasRole(u, RoleType.MASTER) || hasRole(u, RoleType.ADMIN)) {
        const filter = { route: null, station: null, from: null, to: null, states: [TripStatus.IN_PROGRESS] }
        depotTrips.value = (await loadCached('depot-trips', () => fetchTrips(filter, null))).data
      }
    })
  } catch (e) {
    error.value = shownError(e)
  }
}

// формирование показанного рейса — начальнику поезда этого рейса
async function loadCrew() {
  const shown = trip.value
  crew.value = null
  if (shown?.crewDutyType !== CrewDuty.LNP) return
  const cached = await loadCached(`crew:${shown.id}`, () => fetchTripCrew(shown.id))
  crew.value = cached.data
  // без связи — сохранённая на телефоне копия
  stale.value = cached.stale
}

// переключили рейс — формирование другого рейса
watch(
  () => trip.value?.id,
  () => void loadCrew().catch((e: unknown) => (error.value = shownError(e))),
)

const onRefresh = () => void load()
bus.on('connection-restored', onRefresh)
onBeforeUnmount(() => {
  bus.off('connection-restored', onRefresh)
})

// загрузка — сразу в setup: спиннер виден с первой отрисовки
void load()
</script>

<style scoped lang="scss">
.today__trip {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
}

.today__trip-head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.today__trip-badges {
  display: flex;
  font-size: 0.8rem;
}

.today__trip-name {
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.3;
}

.today__tiles {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem;
}

.today__tile {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  border: 0.0625rem solid transparent;
  border-radius: 0.3rem;
  background: $zs-row-selected-bg;
  color: inherit;
  font: inherit;
  text-align: left;

  // плитка, по которой сейчас отмечается отправление или прибытие. Недоступную плитку не делаем disabled:
  // Quasar глобально бледнит всё с атрибутом disabled, а текст отметки должен оставаться обычным
  &--action {
    border-color: $primary;
    cursor: pointer;
  }
}

.today__coupling {
  display: flex;
  gap: 0.5rem;
  border-top: 0.0625rem solid $zs-border;
}

.today__coupling-link {
  flex: 1;
  border: 0.0625rem solid $primary;
  border-radius: 0.3rem;
  color: $primary;
  font-size: 0.9rem;
  font-weight: 600;
  text-align: center;
  text-decoration: none;
}

.today__tile-key {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.today__tile-value {
  font-size: 1.1rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.today__crew {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.today__crew-card {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
}

.today__crew-attrs {
  display: grid;
  flex: 1;
  grid-template-columns: auto 1fr;
  gap: 0.4rem 0.8rem;
  align-items: center;
  min-width: 0;
  font-size: 0.9rem;
}

.today__norm {
  flex: none;
  border-radius: 62.5rem;
  background: #fbe9cf;
  color: #8a4b05;
  font-size: 0.8rem;
}

.today__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.today__tile-delay {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.today__switch {
  align-self: flex-end;
}

// над планами — разделитель, как над «Отцепка» / «Прицепка»
.today__plans {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  border-top: 0.0625rem solid $zs-border;
}

// слева операция двумя строками, справа кнопки; выровнены по верху
.today__plan {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.6rem;
}

.today__plan-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  font-size: 0.9rem;
}

.today__plan-what {
  font-weight: 600;
}

.today__plan-where {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.today__plan-done {
  color: $zs-ok-fg;
  font-weight: 600;
}

.today__plan-waiting {
  color: $zs-text-muted;
  font-weight: 600;
}

.today__plan-cancelled {
  color: $zs-text-muted;
  font-weight: 600;
}

// текст листа подтверждения: строки друг под другом
.today__sheet-text {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

// кнопки листа — по своему тексту, в одну строку; свободное место делят поровну
.today__sheet-btn {
  flex: 1 1 auto;
}

.today__plan-overdue {
  color: $zs-error-fg;
  font-weight: 600;
}

// кнопки — друг под другом, одной ширины; скругление — как у соседних кнопок «Отцепка» / «Прицепка»
.today__plan-btns {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: stretch;
  gap: 0.4rem;

  .q-btn {
    border-radius: 0.3rem;
  }
}
</style>
