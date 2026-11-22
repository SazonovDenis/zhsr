<template>
  <SidePanel
    :empty="item ? '' : 'Выберите позицию в списке'"
    :title="item?.textRu ?? ''"
    :subtitle="item ? (item.unit === null ? t('Раздел') : item.unit) : ''"
    @close="emit('close')"
  >
    <template v-if="item">
      <SidePanelFields>
        <span>{{ t('На казахском') }}</span>
        <span>{{ item.textKk || '—' }}</span>
        <span>{{ t('На английском') }}</span>
        <span>{{ item.textEn || '—' }}</span>
      </SidePanelFields>

      <div v-if="item.unit === null" class="item-panel__note q-mt-md">
        {{ t('У раздела нет нормативов: нормативы задаются у позиций.') }}
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
          {{ t('Нормативов нет: позиция сравнивается с прошлой описью.') }}
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
          <div>
            {{ equipQuantityKindText(n.quantityKind) }} {{ numText(n.quantityPerBasis) }}
            {{ equipBasisText(n.basis) }}
          </div>
          <div v-if="n.normText" class="zs-sub">{{ t('В приказе: {text}', { text: n.normText }) }}</div>
        </button>
      </template>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'

import type { EquipItem, EquipNorm } from '@/api/equip'
import { deleteEquipNorm, EquipBasis, EquipQuantityKind, fetchEquipNorms, saveEquipNorm } from '@/api/equip'
import { t } from '@/boot/i18n'
import SidePanel from '@/desktop/frame/SidePanel.vue'
import SidePanelFields from '@/desktop/frame/SidePanelFields.vue'
import { provideBusy } from '@/shared/busy'
import { EQUIP_BASES, EQUIP_QUANTITY_KINDS, equipBasisText, equipQuantityKindText } from '@/shared/checks/labels'
import { numText } from '@/shared/checks/texts'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { openSpecDialog } from '../shared/spec'

/**
 * Нормативы выбранной позиции описи (Ф-ЭКП-01): тип вагона, количество на единицу базы, база расчёта, способ сравнения,
 * норма текстом, как в приказе, «критичный». Требуемое количество = количество × база; число мест, купе и окон — у
 * модели вагона. Норматив, по которому посчитаны описи, сервер при правке закрывает и заводит новый.
 */
const props = defineProps<{ item: EquipItem | null }>()
const emit = defineEmits<{ close: [] }>()

const busy = provideBusy()
const norms = shallowRef<EquipNorm[]>([])
const error = ref<ShownError | null>(null)

async function load() {
  const item = props.item
  norms.value = []
  error.value = null
  if (!item || item.unit === null) return
  try {
    const [list] = await busy.run(Promise.all([fetchEquipNorms(item.id), dictManager.prepareDict('WagonType')]))
    if (props.item?.id === item.id) norms.value = list
  } catch (e) {
    error.value = shownError(e)
  }
}
watch(() => props.item, load, { immediate: true })

async function editNorm(norm: EquipNorm | null) {
  const item = props.item
  if (!item) return
  const result = await openSpecDialog<EquipNorm>({
    title: norm ? 'Норматив позиции' : 'Новый норматив позиции',
    okText: norm ? 'Сохранить' : 'Добавить',
    hint: 'Добавка для головного и хвостового вагона пока не входит в требуемое количество.',
    fields: [
      {
        name: 'wagonType',
        label: 'Тип вагона',
        kind: 'single',
        wide: true,
        placeholder: 'Все типы вагона',
        choices: dictChoices('WagonType'),
        source: dictSource('WagonType'),
      },
      {
        name: 'quantityKind',
        label: 'Способ сравнения',
        kind: 'single',
        required: true,
        choices: EQUIP_QUANTITY_KINDS,
      },
      { name: 'quantityPerBasis', label: 'Количество на единицу базы', kind: 'number', required: true },
      { name: 'basis', label: 'База расчёта', kind: 'single', required: true, choices: EQUIP_BASES },
      { name: 'headTailExtra', label: 'Добавка в головной и хвостовой вагон', kind: 'number' },
      { name: 'normText', label: 'Норма текстом, как в приказе', kind: 'text', wide: true },
      { name: 'critical', label: 'Критичный: блокирует отправку', kind: 'bool' },
    ],
    initial: {
      wagonType: norm?.wagonType ?? null,
      quantityKind: norm?.quantityKind ?? EquipQuantityKind.AT_LEAST,
      quantityPerBasis: norm?.quantityPerBasis ?? null,
      basis: norm?.basis ?? EquipBasis.WAGON,
      headTailExtra: norm?.headTailExtra ?? 0,
      normText: norm?.normText ?? '',
      critical: norm?.critical ?? false,
    },
    save: (v) =>
      saveEquipNorm(item.id, norm?.id ?? null, {
        wagonType: v.wagonType as number | null,
        quantityKind: v.quantityKind as number,
        quantityPerBasis: v.quantityPerBasis as number,
        basis: v.basis as number,
        headTailExtra: (v.headTailExtra as number | null) ?? 0,
        normText: v.normText as string | null,
        critical: v.critical as boolean,
      }),
    remove: norm ? () => deleteEquipNorm(norm.id) : null,
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
