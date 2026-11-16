<template>
  <!-- шаг вагона: страница не прокручивается, прокручивается только список вагонов -->
  <!-- «назад» в шапке закрывает мастер; по шагам назад — кнопка «Назад» внизу -->
  <PwaPage :title="t(stepName === 'wagon' ? 'Вагон' : 'Что проводим')" :back="close" :fill="stepName === 'wagon'">
    <template #bottom>
      <div class="start__bars q-px-sm">
        <span v-for="i in steps.length" :key="i" class="start__bar" :class="{ 'start__bar--on': i - 1 <= step }" />
      </div>
    </template>
    <LoadSpinner v-if="busy.active.value" />
    <ErrorBox v-if="error" :error="error" :retry="loadTemplates" class="start__error q-py-sm q-px-md" />

    <!-- шаг 1: вид (чек-лист или опись) и шаблон -->
    <template v-if="stepName === 'template'">
      <CbSegment
        v-if="kinds.length > 1"
        :model-value="kind"
        single
        required
        :values="kinds"
        @update:model-value="setKind($event as Kind)"
      />
      <CbList
        :model-value="templateId"
        single
        :placeholder="kind === 'check' ? 'Выберите чек-лист' : 'Выберите опись'"
        :values="templateChoices"
        @update:model-value="templateId = typeof $event === 'number' ? $event : null"
      />
    </template>

    <!-- шаг 2: вагон — как при подаче заявки -->
    <WagonModePicker
      v-else
      :user="currentUser"
      :selected="wagon"
      @pick="wagon = $event ? { id: $event.id, number: $event.number, wagonType: $event.wagonType } : null"
    />

    <template #bar>
      <BottomBar :actions="navActions" @action="(key) => (key === 'back' ? back() : next())" />
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { CheckTemplate } from '@/api/checks'
import { CheckObject, fetchCheckTemplates } from '@/api/checks'
import type { EquipTemplate } from '@/api/equip'
import { fetchEquipTemplates } from '@/api/equip'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { anyCheckReason, checkReason, equipReason } from '@/shared/checks/rights'
import { textOf } from '@/shared/checks/texts'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { currentUser, loadCurrentUser } from '@/shared/user'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'
import PwaPage from '../components/PwaPage.vue'
import WagonModePicker from '../components/WagonModePicker.vue'
import { exitPage } from './wizardHistory'

/**
 * Начало чек-листа (Ф-ЧЕК-03) или описи (Ф-ЭКП-02) на телефоне — мастер, как подача заявки:
 *
 * 1. вид — чек-лист или опись (только доступные пользователю; доступен один — переключателя нет) — и шаблон из
 *    выпадающего списка;
 * 2. вагон — тем же выбором, что при подаче заявки (WagonModePicker). Шага нет у чек-листа сотрудника или рейса:
 *    их объект выбирается на странице чек-листа.
 *
 * «Провести чек-лист» / «Провести опись» открывает заполнение (CheckFillPage, EquipFillPage) с выбранными шаблоном и
 * вагоном.
 */

type Kind = 'check' | 'equip'

const router = useRouter()
const busy = provideBusy()
const error = ref<ShownError | null>(null)

const kinds = computed<ChoiceItem[]>(() => [
  ...(anyCheckReason() === '' ? [{ id: 'check', text: 'Чек-лист' }] : []),
  ...(equipReason() === '' ? [{ id: 'equip', text: 'Опись' }] : []),
])
const kind = ref<Kind>('check')
const templateId = ref<number | null>(null)
const wagon = shallowRef<{ id: number; number: string; wagonType: number } | null>(null)

const checkTemplates = shallowRef<CheckTemplate[]>([])
const equipTemplates = shallowRef<EquipTemplate[]>([])

const templateChoices = computed<ChoiceItem[]>(() =>
  (kind.value === 'check' ? checkTemplates.value : equipTemplates.value).map((x) => ({ id: x.id, text: textOf(x) })),
)

/** Чек-лист сотрудника или рейса — без шага вагона. */
const needsWagon = computed(() => {
  if (kind.value === 'equip') return true
  const tpl = checkTemplates.value.find((x) => x.id === templateId.value)
  return !tpl || tpl.checkObject === CheckObject.WAGON
})

const steps = computed(() => (needsWagon.value ? (['template', 'wagon'] as const) : (['template'] as const)))
const step = ref(0)
const stepName = computed(() => steps.value[step.value] ?? 'template')
const last = computed(() => step.value === steps.value.length - 1)

const canNext = computed(() => (stepName.value === 'template' ? templateId.value !== null : wagon.value !== null))

function setKind(value: Kind) {
  if (value === kind.value) return
  kind.value = value
  templateId.value = null
}

const navActions = computed<BottomBarAction[]>(() => [
  { key: 'back', title: step.value === 0 ? 'Отмена' : 'Назад', align: 'left' },
  {
    key: 'next',
    title: last.value ? (kind.value === 'check' ? 'Провести чек-лист' : 'Провести опись') : 'Далее',
    kind: 'primary',
    disabled: !canNext.value,
  },
])

/**
 * Закрыть мастер — туда, откуда его открыли (мастер открыт первым — в список описей и чек-листов): ничего ещё не
 * заполнено, подтверждать нечего; мастер в истории не остаётся.
 */
function close() {
  void exitPage(router, { name: 'pwa-checks' })
}

/** «Назад» внизу: на шаг назад, с первого шага — закрыть. */
function back() {
  if (step.value === 0) close()
  else step.value--
}

function next() {
  if (!canNext.value) return
  if (!last.value) {
    step.value++
    return
  }
  const query: Record<string, string> = { template: String(templateId.value) }
  if (needsWagon.value && wagon.value) {
    query.wagon = String(wagon.value.id)
    query.number = wagon.value.number
    query.wagonType = String(wagon.value.wagonType)
  }
  void router.push({ name: kind.value === 'check' ? 'pwa-check-new' : 'pwa-equip-new', query })
}

// шаблоны, которые пользователь проводит: чек-листы — по объекту проверки (checkReason), описи — все
async function loadTemplates() {
  error.value = null
  try {
    await busy.run(Promise.all([loadCurrentUser(), dictManager.prepareDict('WagonType')]))
    const [checks, equips] = await busy.run(
      Promise.all([
        anyCheckReason() === '' ? fetchCheckTemplates() : Promise.resolve([]),
        equipReason() === '' ? fetchEquipTemplates() : Promise.resolve([]),
      ]),
    )
    checkTemplates.value = checks.filter((x) => checkReason(x.checkObject) === '')
    equipTemplates.value = equips
    if (anyCheckReason() !== '') kind.value = 'equip'
  } catch (e) {
    error.value = shownError(e)
  }
}
void loadTemplates()
</script>

<style scoped lang="scss">
.start__bars {
  display: grid;
  grid-auto-columns: 1fr;
  grid-auto-flow: column;
  gap: 0.3rem;
}

.start__bar {
  height: 0.2rem;
  border-radius: 0.1rem;
  background: $zs-border;

  // пройденные шаги и текущий — цветом кнопки
  &--on {
    background: $primary;
  }
}

.start__error {
  flex: none;
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
