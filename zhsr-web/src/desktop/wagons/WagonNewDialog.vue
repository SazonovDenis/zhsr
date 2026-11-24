<template>
  <FormDialog
    v-model:show="show"
    min-height="xl"
    :title="wagon ? 'Исправить данные вагона' : 'Ввод вагона'"
    :ok-text="wagon ? 'Сохранить' : 'Ввести вагон'"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection title="Паспорт" columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Номер вагона">
        <TextInput v-model="draft.number" mono placeholder="5–20 знаков" />
      </FormField>
      <FormField label="Тип вагона">
        <CbList
          v-model="draft.wagonType"
          single
          placeholder="Выберите…"
          :values="dictChoices('WagonType')"
          :source="dictSource('WagonType')"
        />
      </FormField>
      <FormField label="Модель">
        <CbList
          v-model="draft.modelType"
          single
          placeholder="Выберите…"
          :values="dictChoices('WagonModel')"
          :source="dictSource('WagonModel')"
        />
      </FormField>
      <FormField label="Год постройки">
        <TextInput v-model="draft.buildYear" inputmode="numeric" />
      </FormField>
      <FormField label="Изготовитель">
        <TextInput v-model="draft.maker" />
      </FormField>
      <FormField label="Пробег на момент ввода, км">
        <TextInput v-model="draft.initialRunKm" inputmode="numeric" />
      </FormField>
    </FormSection>

    <FormSection title="Депо приписки" divided>
      <CbList
        v-model="draft.depot"
        single
        placeholder="Выберите…"
        :values="dictChoices('Depot')"
        :source="dictSource('Depot')"
      />
    </FormSection>

    <!-- вагон в рейсе: расположение и состояние меняют отметки рейса -->
    <FormSection
      v-if="!inTrip"
      :title="wagon ? 'Расположение и состояние' : 'Начальное расположение и состояние'"
      divided
      columns="repeat(2, minmax(0, 1fr))"
    >
      <FormField label="Расположение" class="wagon-new__kind">
        <CbSegment v-model="draft.locationType" single required :values="locationKinds" :max-button-count="3" />
      </FormField>
      <FormField :label="refLabel">
        <CbList
          v-model="draft.place"
          single
          placeholder="Выберите…"
          :values="refChoices"
          :source="refDict ? dictSource(refDict) : null"
        />
      </FormField>
      <FormField label="Состояние">
        <CbList v-model="draft.wagonStatusType" single placeholder="Выберите…" :values="initialStates" />
      </FormField>
    </FormSection>

    <FormSection divided columns="max-content">
      <FormField label="Ввод в эксплуатацию">
        <DatePicker
          v-model="draft.dbeg"
          date-text="Ввод в эксплуатацию"
          :quick="['today', 'yesterday', 'monthStart', 'yearStart']"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import type { WagonRow, WagonDraft } from '@/api/wagons'
import { createWagon, updateWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import type { DictName } from '@/shared/dict/dicts'
import { LocationType, WagonStatus } from '@/shared/constants/systemDicts'

/**
 * Диалог «Ввод вагона» (Ф-ВАГ-01): паспорт с пробегом на момент ввода, депо приписки, начальное расположение и
 * состояние. С вагоном wagon — «Исправить данные вагона» (Ф-ВАГ-08): те же поля, заполненные данными вагона.
 * Открывается через openDialog, результат — созданный или исправленный вагон.
 */

const props = withDefaults(defineProps<{ wagon?: WagonRow | null }>(), { wagon: null })

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
provideBusy()
const emit = defineEmits<{ done: [wagon: WagonRow] }>()

const inTrip = props.wagon?.locationType === LocationType.TRIP

const draft = reactive<WagonDraft>({
  number: props.wagon?.number ?? '',
  wagonType: props.wagon?.wagonType ?? null,
  modelType: props.wagon?.modelType ?? null,
  buildYear: props.wagon?.buildYear?.toString() ?? '',
  maker: props.wagon?.maker ?? '',
  depot: props.wagon?.depot ?? null,
  locationType: (inTrip ? null : props.wagon?.locationType) ?? LocationType.DEPOT,
  place: (inTrip ? null : props.wagon?.place) ?? null,
  wagonStatusType: (inTrip ? null : props.wagon?.wagonStatusType) ?? null,
  dbeg: props.wagon?.dbeg ?? null,
  initialRunKm: String(props.wagon?.initialRunKm ?? 0),
})

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// вагон вводится в депо, в парк отстоя или на станцию; «в рейсе» он оказывается только при формировании рейса
const locationKinds = computed(() => dictChoices('LocationKind').filter((c) => c.id !== LocationType.TRIP))

// откуда выбирается место — по виду расположения
const refDicts: Record<number, { dict: DictName; label: string }> = {
  [LocationType.DEPOT]: { dict: 'Depot', label: 'Депо' },
  [LocationType.YARD]: { dict: 'Yard', label: 'Парк отстоя' },
  [LocationType.STATION]: { dict: 'Station', label: 'Станция' },
}
const refLabel = computed(() => refDicts[draft.locationType]?.label ?? '')
const refDict = computed(() => refDicts[draft.locationType]?.dict ?? null)
const refChoices = computed(() => (refDict.value ? dictChoices(refDict.value) : []))
watch(
  () => draft.locationType,
  () => (draft.place = null),
)

// «В пути» вагон получает только в рейсе, поэтому начальным состоянием быть не может
const initialStates = computed(() => dictChoices('WagonState').filter((c) => c.id !== WagonStatus.IN_TRIP))

// справочники формы контролы загружают сами (dictSource): спиннер и ошибка — в выпадающем списке

function validate(): string {
  if (!/^[\d/]{5,20}$/.test(draft.number)) return t('Номер вагона — от 5 до 20 знаков: цифры и дробная черта')
  if (!draft.wagonType || !draft.dbeg) return t('Заполните все поля')
  if (props.wagon) {
    // исправление: модель, депо и место могут быть не заданы; заданное место не очищается
    const placeLost = props.wagon.place !== null && !draft.place
    if (!inTrip && (placeLost || !draft.wagonStatusType)) return t('Заполните все поля')
  } else if (!draft.modelType || !draft.depot || !draft.place || !draft.wagonStatusType) {
    return t('Заполните все поля')
  }
  if (!/^\d+$/.test(draft.initialRunKm.trim())) return t('Пробег — целое неотрицательное число, км')
  if (draft.buildYear.trim() && !/^\d{4}$/.test(draft.buildYear.trim())) return t('Год постройки — четыре цифры')
  return ''
}

async function save() {
  const invalid = validate()
  error.value = invalid ? formError(invalid) : null
  if (error.value) return
  running.value = true
  try {
    emit('done', await (props.wagon ? updateWagon(props.wagon.id, { ...draft }) : createWagon({ ...draft })))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.wagon-new__kind {
  grid-column: 1 / -1;
}
</style>
