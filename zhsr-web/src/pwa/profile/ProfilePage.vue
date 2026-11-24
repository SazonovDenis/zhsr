<template>
  <!-- fill: кнопки прижаты к низу экрана, прокручиваются только сведения над ними -->
  <PwaPage fill>
    <template v-if="user">
      <div class="profile__main">
        <div class="profile__head">
          <div class="profile__name">{{ user.name }}</div>
          <div class="profile__roles">{{ roles }}</div>
        </div>

        <div class="profile__attrs">
          <span>{{ t('Службы') }}</span>
          <span>{{ services || '—' }}</span>
          <span>{{ t('Депо') }}</span>
          <span>{{ user.depot ? dictManager.getValue('Depot', user.depot) : '—' }}</span>
          <span>{{ t('Станция депо') }}</span>
          <span>{{ user.depotStation ? dictManager.getValue('Station', user.depotStation) : '—' }}</span>
          <template v-if="user.trip">
            <span>{{ t('Текущий рейс') }}</span>
            <span>{{ tripText(user.trip) }}</span>
            <span>{{ t('Должность в рейсе') }}</span>
            <span>{{ dictManager.getValue('CrewDuty', user.trip.crewDutyType) }}</span>
            <template v-if="user.trip.wagons.length > 0">
              <span>{{ t('Вагоны') }}</span>
              <span class="zs-mono">{{ user.trip.wagons.join(', ') }}</span>
            </template>
          </template>
          <template v-if="user.otherTrip">
            <span>{{ t('Соседний рейс') }}</span>
            <span>{{ tripText(user.otherTrip) }}</span>
          </template>
        </div>

        <!-- язык интерфейса: три кнопки, названия языков — на самих языках -->
        <div class="profile__language">
          <div class="zs-group-head">{{ t('Язык интерфейса') }}</div>
          <CbSegment v-model="language" single required :values="languages" />
        </div>
      </div>

      <!-- кнопки страницы телефона — как везде на страницах (zs-pwa-btn--lg), друг под другом внизу: пароль — белая с
           рамкой, выход — синяя -->
      <div class="profile__actions">
        <q-btn
          unelevated
          outline
          no-caps
          color="ink"
          class="zs-pwa-btn zs-pwa-btn--lg zs-soft-border"
          icon="lock"
          :label="t('Сменить пароль')"
          @click="openDialog(ChangePasswordDialog)"
        />
        <q-btn
          unelevated
          no-caps
          color="primary"
          class="zs-pwa-btn zs-pwa-btn--lg"
          :label="t('Выход')"
          @click="bus.emit('pwa-logout')"
        />
      </div>
    </template>
    <LoadSpinner v-else-if="busy.active.value" />
    <ErrorBox v-else-if="error" :error="error" :retry="load" />
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import type { UserTrip } from '@/api/session'
import type { MessageLanguages } from '@/boot/i18n'
import ChangePasswordDialog from '@/auth/ChangePasswordDialog.vue'
import { i18n, t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { provideBusy } from '@/shared/busy'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { LOCALES, setLocale } from '@/shared/locale'
import { openDialog } from '@/shared/components/dialog/openDialog'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { compareText, formatDateTime } from '@/shared/format'
import { currentUser, loadCurrentUser } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'

/**
 * Профиль пользователя на телефоне: фамилия, роли, службы, депо и его станция, текущий и соседний рейс — должность и
 * вагоны; «Сменить пароль», «Выход» и язык интерфейса тремя кнопками. Язык, пароль и выход есть и в меню шапки.
 */
const busy = provideBusy()
const error = ref<ShownError | null>(null)

const languages: ChoiceItem[] = LOCALES.map((l) => ({ id: l.value, text: l.label }))
const language = computed<ChoiceModel>({
  get: () => i18n.global.locale.value,
  set: (v) => {
    if (typeof v === 'string') setLocale(v as MessageLanguages)
  },
})

const user = computed(() => currentUser.value)
const roles = computed(
  () =>
    user.value?.roles
      .map((r) => dictManager.getValue('Role', r))
      .sort(compareText)
      .join(', ') ?? '',
)
const services = computed(() => user.value?.services.map((s) => dictManager.getValue('Service', s)).join(', ') ?? '')

/** Рейс: «002 • Алматы — Астана, отправление 29.10 06:00». */
function tripText(trip: UserTrip): string {
  return `${trip.trainNumber} • ${trip.name}, ${t('отправление {at}', { at: formatDateTime(trip.departure) })}`
}

// пользователь и справочники его данных (депо, станция) — в счёт ожидания страницы
async function load() {
  error.value = null
  try {
    await busy.run(Promise.all([loadCurrentUser(), dictManager.prepareDicts('Depot', 'Station')]))
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()
</script>

<style scoped lang="scss">
// сведения — на всю высоту над кнопками, прокручиваются сами
.profile__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1rem;
  min-height: 0;
  overflow-y: auto;
}

.profile__actions {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 0.5rem;
}

.profile__head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.profile__name {
  font-size: 1.4rem;
  font-weight: 700;
}

.profile__roles {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

// подпись «Язык интерфейса» и кнопки языков — с промежутком, как подпись группы и список в чек-листах
.profile__language {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.profile__attrs {
  display: grid;
  grid-template-columns: 8rem minmax(0, 1fr);
  gap: 0.5rem 1rem;
  font-size: 0.9rem;

  > :nth-child(odd) {
    color: $zs-text-muted;
  }
}
</style>
