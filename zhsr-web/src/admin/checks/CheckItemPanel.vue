<template>
  <SidePanel
    :empty="item ? '' : 'Выберите пункт в списке'"
    :title="item?.textRu ?? ''"
    :subtitle="item ? (item.valueKind === null ? t('Раздел') : valueKindText(item.valueKind)) : ''"
    @close="emit('close')"
  >
    <template v-if="item">
      <SidePanelFields>
        <template v-if="item.checkDict !== null">
          <span>{{ t('Шаблон ответов') }}</span>
          <span>{{ dictName }}</span>
        </template>
        <span>{{ t('На казахском') }}</span>
        <span>{{ item.textKk || '—' }}</span>
        <span>{{ t('На английском') }}</span>
        <span>{{ item.textEn || '—' }}</span>
      </SidePanelFields>

      <div v-if="item.valueKind === null" class="item-panel__note q-mt-md">
        {{ t('У раздела нет нормативов: нормативы задаются у пунктов со значением.') }}
      </div>

      <template v-else>
        <div class="item-panel__head q-mt-lg">
          <span class="item-panel__title">{{ t('Нормативы') }}</span>
          <q-btn
            unelevated
            outline
            dense
            no-caps
            color="primary"
            class="zs-soft-border"
            padding="0.1rem 0.6rem"
            @click="editNorm(null)"
          >
            {{ t('Добавить норматив') }}
          </q-btn>
        </div>
        <LoadSpinner v-if="busy.active.value && norms.length === 0" />
        <ErrorBox v-else-if="error" :error="error" :retry="load" />
        <div v-else-if="norms.length === 0" class="item-panel__note">
          {{ t('Нормативов нет: пункт не даёт нарушений.') }}
        </div>
        <button
          v-for="n in norms"
          :key="n.id"
          type="button"
          class="item-panel__norm zs-focusable q-pa-sm q-mb-sm"
          :title="t('Изменить')"
          @click="editNorm(n)"
        >
          <div class="item-panel__norm-head">
            <span>{{
              n.wagonType === null ? t('Все типы вагона') : dictManager.getValue('WagonType', n.wagonType)
            }}</span>
            <span v-if="n.critical" class="item-panel__critical">{{ t('критичный') }}</span>
          </div>
          <div class="zs-sub">{{ normText(n) }}</div>
          <div class="zs-sub">
            {{
              n.faultType === null
                ? t('Без заявки')
                : t('Заявка: {fault}', { fault: dictManager.getValue('FaultType', n.faultType) })
            }}
          </div>
        </button>
      </template>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import type { CheckDict, CheckDictValue, CheckItem, CheckNorm } from '@/api/checks'
import { CheckValueKind, deleteCheckNorm, fetchCheckDictValues, fetchCheckNorms, saveCheckNorm } from '@/api/checks'
import { t } from '@/boot/i18n'
import SidePanel from '@/desktop/frame/SidePanel.vue'
import SidePanelFields from '@/desktop/frame/SidePanelFields.vue'
import { provideBusy } from '@/shared/busy'
import { valueKindText } from '@/shared/checks/labels'
import { numText } from '@/shared/checks/texts'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { openSpecDialog } from '../shared/spec'

/**
 * Нормативы выбранного пункта шаблона чек-листа (Ф-ЧЕК-02): для всех типов вагона или для типа вагона, обязательность,
 * границы числа, допустимые варианты ответа, «критичный», неисправность заявки. Клик по нормативу — правка, «+ Норматив»
 * — новый. Норматив, по которому проверены значения, сервер при правке закрывает и заводит новый.
 */
const props = defineProps<{ item: CheckItem | null; dicts: CheckDict[] }>()
const emit = defineEmits<{ close: [] }>()

// ожидание панели: спиннер на месте нормативов — справочники типов вагона и неисправностей, нормативы, варианты ответа
const busy = provideBusy()
const norms = shallowRef<CheckNorm[]>([])
const dictValues = shallowRef<CheckDictValue[]>([])
const error = ref<ShownError | null>(null)

const dictName = computed(() => props.dicts.find((d) => d.id === props.item?.checkDict)?.textRu ?? '')

async function load() {
  const item = props.item
  norms.value = []
  dictValues.value = []
  error.value = null
  if (!item || item.valueKind === null) return
  try {
    const [list, values] = await busy.run(
      Promise.all([
        fetchCheckNorms(item.id),
        item.checkDict === null ? Promise.resolve([]) : fetchCheckDictValues(item.checkDict),
        dictManager.prepareDicts('WagonType', 'FaultType'),
      ]),
    )
    if (props.item?.id !== item.id) return
    norms.value = list
    dictValues.value = values
  } catch (e) {
    error.value = shownError(e)
  }
}
watch(() => props.item, load, { immediate: true })

/** Условие норматива текстом: обязательность, границы числа или допустимые варианты. */
function normText(n: CheckNorm): string {
  const parts: string[] = []
  if (n.required) parts.push(t('обязателен'))
  if (n.numMin !== null || n.numMax !== null) {
    parts.push(t('от {min} до {max}', { min: numText(n.numMin) || '…', max: numText(n.numMax) || '…' }))
  }
  if (n.checkDictValues.length > 0) {
    const names = n.checkDictValues.map((id) => dictValues.value.find((v) => v.id === id)?.textRu ?? `<<${id}>>`)
    parts.push(t('допустимо: {values}', { values: names.join(', ') }))
  }
  return parts.join('; ') || t('без условий')
}

async function editNorm(norm: CheckNorm | null) {
  const item = props.item
  if (!item) return
  const isNumber = item.valueKind === CheckValueKind.NUMBER
  const isDict = item.valueKind === CheckValueKind.DICT_VALUE
  const result = await openSpecDialog<CheckNorm>({
    title: norm ? 'Норматив пункта' : 'Новый норматив пункта',
    okText: norm ? 'Сохранить' : 'Добавить',
    hint: 'Значение вне границ или вариант не из допустимых — нарушение. Заявка создаётся только при проверке вагона.',
    fields: [
      {
        name: 'wagonType',
        label: 'Тип вагона',
        kind: 'single',
        placeholder: 'Все типы вагона',
        choices: dictChoices('WagonType'),
        source: dictSource('WagonType'),
      },
      { name: 'required', label: 'Обязателен к заполнению', kind: 'bool' },
      ...(isNumber
        ? [
            { name: 'numMin', label: 'Не меньше', kind: 'number' as const },
            { name: 'numMax', label: 'Не больше', kind: 'number' as const },
          ]
        : []),
      ...(isDict
        ? [
            {
              name: 'checkDictValues',
              label: 'Допустимые варианты ответа',
              kind: 'multi' as const,
              wide: true,
              placeholder: 'Любой вариант — не нарушение',
              choices: dictValues.value.map((v) => ({ id: v.id, text: v.textRu, disabled: v.disabled })),
            },
          ]
        : []),
      { name: 'critical', label: 'Критичный: блокирует отправку', kind: 'bool' },
      {
        name: 'faultType',
        label: 'Неисправность заявки',
        kind: 'fault',
        wide: true,
        placeholder: 'Без заявки',
      },
    ],
    initial: {
      wagonType: norm?.wagonType ?? null,
      required: norm?.required ?? false,
      numMin: norm?.numMin ?? null,
      numMax: norm?.numMax ?? null,
      checkDictValues: norm?.checkDictValues ?? [],
      critical: norm?.critical ?? false,
      faultType: norm?.faultType ?? null,
    },
    save: (v) =>
      saveCheckNorm(item.id, norm?.id ?? null, {
        wagonType: v.wagonType as number | null,
        required: v.required as boolean,
        numMin: isNumber ? (v.numMin as number | null) : null,
        numMax: isNumber ? (v.numMax as number | null) : null,
        checkDictValues: isDict ? (v.checkDictValues as number[]) : [],
        critical: v.critical as boolean,
        faultType: v.faultType as number | null,
      }),
    remove: norm ? () => deleteCheckNorm(norm.id) : null,
  })
  if (result !== undefined) await load()
}
</script>

<style scoped lang="scss">
.item-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.item-panel__title {
  font-size: 0.9rem;
  font-weight: 600;
}

.item-panel__note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.item-panel__norm {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  width: 100%;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8rem;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: $zs-bg-hover;
  }
}

.item-panel__norm-head {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
}

.item-panel__critical {
  color: $zs-error-fg;
}
</style>
