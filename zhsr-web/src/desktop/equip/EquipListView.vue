<template>
  <SidePanelFields>
    <span>{{ t('Вагон') }}</span>
    <span>
      <span class="zs-mono">{{ detail.wagon.number }}</span> •
      {{ dictManager.getValue('WagonType', detail.wagon.wagonType) }}
    </span>
    <span>{{ t('Рейс') }}</span>
    <span>{{ detail.trip ? tripRefDatedText(detail.trip) : '—' }}</span>
    <span>{{ t('Составил') }}</span>
    <span>{{ userName(detail.createdByUsr) }}</span>
    <!-- подпись — по центру строки с кнопкой фильтра -->
    <span class="self-center">{{ t('Нарушений') }}</span>
    <!-- «Показать только нарушения» — в списке только позиции, которых не хватает, и сколько не хватает -->
    <span class="equip-view__violations">
      <span :class="{ 'equip-view__bad-text': detail.violationCount > 0 }">{{ detail.violationCount }}</span>
      <CbButton v-model="filter" :values="filterChoices" />
    </span>
    <span>{{ t('Заявка по недостаче') }}</span>
    <span>
      <SoftRouteLink
        v-if="detail.repairRequest"
        text="{number}"
        :params="{ number: detail.repairRequest.number }"
        :to="{
          name: 'requests',
          query: { request: String(detail.repairRequest.id), number: detail.repairRequest.number },
        }"
      />
      <template v-else>—</template>
    </span>
  </SidePanelFields>

  <!-- между полями и позициями — действия владельца, например «Внести изменения» -->
  <slot />

  <div class="equip-view__items q-mt-md">
    <div class="equip-view__line equip-view__head" :class="{ 'equip-view__line--short': shortOnly }">
      <span>{{ t('Позиция') }}</span>
      <span>{{ t('Требуется') }}</span>
      <span>{{ t('Фактически') }}</span>
      <span v-if="shortOnly">{{ t('Не хватает') }}</span>
    </div>
    <template v-for="{ item, depth } in rows" :key="item.id">
      <div v-if="item.unit === null" class="equip-view__section q-pt-sm" :style="{ paddingLeft: `${depth}rem` }">
        {{ textOf(item) }}
      </div>
      <div v-else-if="valueOf(item.id)" class="equip-view__pos q-py-xs" :style="{ paddingLeft: `${depth}rem` }">
        <div class="equip-view__line equip-view__line--plain" :class="{ 'equip-view__line--short': shortOnly }">
          <!-- нарушение — красное название, без нарушения по нормативу — зелёное -->
          <span :class="toneClass(valueOf(item.id)!)">{{ textOf(item) }}, {{ item.unit }}</span>
          <span>{{ numText(valueOf(item.id)!.quantityRequired) || '—' }}</span>
          <span>{{ numText(valueOf(item.id)!.quantityActual) }}</span>
          <span v-if="shortOnly" class="equip-view__bad-text">{{ numText(shortage(valueOf(item.id)!)) }}</span>
        </div>
        <!-- текст — v-text: переносы строк примечания видны (pre-wrap), а отступы разметки — нет -->
        <div v-if="valueOf(item.id)!.comment" class="equip-view__comment q-mt-xs" v-text="valueOf(item.id)!.comment" />
        <PhotoStrip
          v-if="valueOf(item.id)!.photos.length > 0"
          :photos="photosOf(valueOf(item.id)!)"
          small
          class="q-mt-xs"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { EquipListDetail, EquipListValue } from '@/api/equip'
import { equipPhotoUrl } from '@/api/equip'
import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import { numText, textOf } from '@/shared/checks/texts'
import { treeRows, treeRowsWhere } from '@/shared/checks/tree'
import { valueTone } from '@/shared/checks/valueView'
import type { ChoiceId, ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import SoftRouteLink from '@/shared/components/form/SoftRouteLink.vue'
import { dictManager } from '@/shared/dict/DictManager'
import PhotoStrip from '@/shared/requests/PhotoStrip.vue'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { userName } from '@/shared/user'
import SidePanelFields from '../frame/SidePanelFields.vue'

/**
 * Опись экипировки (Ф-ЭКП-03): поля — вагон, рейс, составил, нарушения, заявка; позиции по разделам — требуемое и
 * фактическое количество, примечание, фото. «Недостача» у числа нарушений оставляет в списке только позиции, которых
 * меньше требуемого, и показывает, сколько не хватает. Справочник WagonType и текущего пользователя загружает владелец.
 */
const props = defineProps<{ detail: EquipListDetail }>()

const SHORT = 'short'

// фильтр списка: [SHORT] — только недостача; сбрасывается при смене описи
const filter = ref<ChoiceId[]>([])
watch(
  () => props.detail.id,
  () => (filter.value = []),
)
const shortOnly = computed(() => filter.value.includes(SHORT))

const values = computed(() => new Map(props.detail.values.map((v) => [v.equipListTemplateItem, v])))

/** Сколько не хватает до требуемого; 0 — хватает или требуемое не задано. */
function shortage(v: EquipListValue): number {
  return v.quantityRequired !== null && v.quantityActual < v.quantityRequired
    ? v.quantityRequired - v.quantityActual
    : 0
}

function valueOf(item: number): EquipListValue | undefined {
  return values.value.get(item)
}

const filterChoices: ChoiceItem[] = [{ id: SHORT, text: 'Показать только нарушения' }]

const rows = computed(() => {
  const all = treeRows(props.detail.items)
  if (!shortOnly.value) return all
  return treeRowsWhere(all, (item) => {
    const v = item.unit === null ? undefined : valueOf(item.id)
    return v !== undefined && shortage(v) > 0
  })
})

function toneClass(v: EquipListValue): string {
  const tone = valueTone(v.violation, v.equipListItemNorm)
  return tone ? `equip-view__name--${tone}` : ''
}

function photosOf(v: EquipListValue): RequestPhoto[] {
  return v.photos.map((p) => ({ id: String(p), url: equipPhotoUrl(p) }))
}
</script>

<style scoped lang="scss">
.equip-view__violations {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.equip-view__items {
  display: flex;
  flex-direction: column;
  font-size: 0.8rem;
}

.equip-view__section {
  font-weight: 600;
}

.equip-view__head {
  color: $zs-text-muted;
}

.equip-view__line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 5rem 5rem;
  gap: 0.5rem;
  border-bottom: 0.0625rem solid $zs-border-row;

  > :not(:first-child) {
    text-align: right;
  }

  // строка позиции: линия снизу — у всей позиции с примечанием и фото
  &--plain {
    border-bottom: 0;
  }

  // недостача: ещё колонка — сколько не хватает
  &--short {
    grid-template-columns: minmax(0, 1fr) 5rem 5rem 5rem;
  }
}

.equip-view__pos {
  border-bottom: 0.0625rem solid $zs-border-row;
}

.equip-view__comment {
  white-space: pre-wrap;
}

.equip-view__name--bad {
  color: $zs-error-fg;
}

.equip-view__name--ok {
  color: $zs-ok-fg;
}

.equip-view__bad-text {
  color: $zs-error-fg;
  font-weight: 600;
}
</style>
