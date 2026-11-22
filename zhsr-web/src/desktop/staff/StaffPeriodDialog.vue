<template>
  <FormDialog
    v-model:show="show"
    height="min(clamp(14rem, 30vh, 19rem), calc(100vh - 3rem))"
    :title="depot ? 'Перемещение сотрудника' : 'Смена категории'"
    :ok-text="depot ? 'Переместить' : 'Сменить'"
    width="40rem"
    :running="running"
    :error="error"
    @ok="save"
  >
    <SidePanelFields>
      <span>{{ t('Сотрудник') }}</span>
      <span>{{ row.fullName }}</span>
      <span>{{ depot ? t('Текущее расположение') : t('Текущая категория') }}</span>
      <span class="staff-period__current">{{ current }}</span>
    </SidePanelFields>
    <FormSection columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Дата">
        <DatePicker v-model="time" with-time date-text="Время изменения" />
      </FormField>
      <FormField :label="depot ? 'Новое расположение *' : 'Новая категория *'">
        <CbList
          v-model="value"
          single
          :placeholder="depot ? 'Выберите депо' : 'Выберите категорию'"
          :values="choices"
          :source="dictSource(depot ? 'Depot' : 'ConductorCategory')"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref } from 'vue'

import type { StaffRow } from '@/api/staff'
import { changeStaffCategory, moveStaffDepot } from '@/api/staff'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import type { ChoiceId } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import SidePanelFields from '../frame/SidePanelFields.vue'
import { placeLabel } from './staffText'

/**
 * Перемещение сотрудника в другое депо (kind = 'depot') или смена категории проводника (kind = 'category'): подпись —
 * текущее расположение или категория, ниже дата изменения (по умолчанию — сейчас) и новое значение. Проверки — на
 * сервере. Открывается через openDialog, результат — сотрудник после изменения.
 */
const props = defineProps<{ row: StaffRow; kind: 'depot' | 'category' }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog)
provideBusy()
const emit = defineEmits<{ done: [row: StaffRow] }>()

const depot = computed(() => props.kind === 'depot')
const current = computed(() =>
  depot.value
    ? placeLabel(props.row)
    : props.row.categoryType === null
      ? t('нет')
      : dictManager.getValue('ConductorCategory', props.row.categoryType),
)
const choices = computed(() => dictChoices(depot.value ? 'Depot' : 'ConductorCategory'))

// значение DatePicker — время браузера без пояса, на сервер уходит с поясом
const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const value = ref<ChoiceId | null>(null)
const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

async function save() {
  if (value.value === null) {
    error.value = formError(t(depot.value ? 'Выберите новое расположение' : 'Выберите новую категорию'))
    return
  }
  error.value = null
  running.value = true
  const at = time.value ? isoDateTime(new Date(time.value)) : null
  try {
    const row = depot.value
      ? await moveStaffDepot(props.row.id, Number(value.value), at)
      : await changeStaffCategory(props.row.id, Number(value.value), at)
    emit('done', row)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.staff-period__current {
  font-weight: 600;
}
</style>
