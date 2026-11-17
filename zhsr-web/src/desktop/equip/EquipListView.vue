<template>
  <!-- обёртки по умолчанию не влияют на раскладку (display: contents); columns — карточка слева, позиции справа,
       над каждой подзаголовок -->
  <div class="equip-view" :class="{ 'equip-view--columns': columns }">
    <div class="equip-view__col equip-view__col--card">
      <div v-if="columns" class="equip-view__section-title">{{ t('Опись') }}</div>
      <div class="equip-view__card">
        <SidePanelFields>
          <!-- в две колонки заголовка с шаблоном и датой нет — они первыми полями -->
          <template v-if="columns">
            <span>{{ t('Шаблон описи') }}</span>
            <span>{{ textOf(detail.equipListTemplate) }}</span>
            <span>{{ t('Дата') }}</span>
            <span>{{ formatDateTime(detail.createdDt) }}</span>
          </template>
          <span>{{ t('Вагон') }}</span>
          <span>
            <!-- номер ссылкой: список вагонов с поиском по этому номеру -->
            <SoftRouteLink
              text="{number}"
              :params="{ number: detail.wagon.number }"
              :to="{ name: 'wagons', query: { number: detail.wagon.number } }"
            />
            •
            {{ dictManager.getValue('WagonType', detail.wagon.wagonType) }}
          </span>
          <span>{{ t('Рейс') }}</span>
          <span>{{ detail.trip ? tripRefDatedText(detail.trip) : '—' }}</span>
          <span>{{ t('Составил') }}</span>
          <span><UserName :user="detail.createdByUsr" /></span>
          <!-- подпись — по центру строки с кнопкой фильтра -->
          <span class="self-center">{{ t('Нарушений') }}</span>
          <!-- «Показать только нарушения» — в списке только позиции, которых не хватает, и сколько не хватает -->
          <span class="equip-view__violations">
            <!-- нарушений нет — «нет» и без кнопки -->
            <span v-if="detail.violationCount > 0" class="equip-view__bad-text">{{ detail.violationCount }}</span>
            <template v-else>{{ t('нет') }}</template>
            <CbButton v-if="detail.violationCount > 0" v-model="filter" :values="filterChoices" />
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
      </div>
    </div>

    <div class="equip-view__col equip-view__col--items">
      <div v-if="columns" class="equip-view__section-title">{{ t('Имущество') }}</div>
      <div class="equip-view__items" :class="columns ? 'q-mt-none' : 'q-mt-md'">
        <div class="equip-view__line equip-view__head" :class="{ 'equip-view__line--short': shortColumn }">
          <span>{{ t('Позиция') }}</span>
          <span>{{ t('Требуется') }}</span>
          <span>{{ t('Фактически') }}</span>
          <span v-if="shortColumn">{{ t('Не хватает') }}</span>
        </div>
        <template v-for="{ item, depth } in rows" :key="item.id">
          <div v-if="item.unit === null" class="equip-view__section q-pt-sm" :style="{ paddingLeft: `${depth}rem` }">
            {{ textOf(item) }}
          </div>
          <div v-else-if="valueOf(item.id)" class="equip-view__pos q-py-xs" :style="{ paddingLeft: `${depth}rem` }">
            <div class="equip-view__line equip-view__line--plain" :class="{ 'equip-view__line--short': shortColumn }">
              <!-- нарушение — красное название, без нарушения по нормативу — зелёное -->
              <span :class="toneClass(valueOf(item.id)!)">{{ textOf(item) }}, {{ item.unit }}</span>
              <span>{{ numText(valueOf(item.id)!.quantityRequired) || '—' }}</span>
              <span>{{ numText(valueOf(item.id)!.quantityActual) }}</span>
              <!-- хватает — «по норме»; требуемое не задано — пусто -->
              <span v-if="shortColumn" :class="{ 'equip-view__bad-text': shortage(valueOf(item.id)!) > 0 }">
                {{ shortageText(valueOf(item.id)!) }}
              </span>
            </div>
            <!-- текст — v-text: переносы строк примечания видны (pre-wrap), а отступы разметки — нет -->
            <div
              v-if="valueOf(item.id)!.comment"
              class="equip-view__comment q-mt-xs"
              v-text="valueOf(item.id)!.comment"
            />
            <PhotoStrip
              v-if="valueOf(item.id)!.photos.length > 0"
              :photos="photosOf(valueOf(item.id)!)"
              small
              class="q-mt-xs"
            />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import UserName from '@/shared/components/UserName.vue'
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
import { formatDateTime } from '@/shared/format'
import PhotoStrip from '@/shared/requests/PhotoStrip.vue'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import SidePanelFields from '../frame/SidePanelFields.vue'

/**
 * Опись экипировки (Ф-ЭКП-03): поля — вагон, рейс, составил, нарушения, заявка; позиции по разделам — требуемое и
 * фактическое количество, примечание, фото. «Недостача» у числа нарушений оставляет в списке только позиции, которых
 * меньше требуемого, и показывает, сколько не хватает. Справочник WagonType и текущего пользователя загружает владелец.
 * columns — в две колонки: слева поля описи (карточка, подзаголовок «Опись»), справа позиции (подзаголовок
 * «Имущество»); первые поля карточки — шаблон описи и дата; карточка и позиции прокручиваются по
 * отдельности, владелец даёт высоту (flex-контейнер столбиком). shortageAlways — колонка «Не хватает» видна
 * всегда, не только при фильтре недостачи; у позиции без недостачи в ней «по норме».
 */
const props = defineProps<{ detail: EquipListDetail; columns?: boolean; shortageAlways?: boolean }>()

const SHORT = 'short'

// фильтр списка: [SHORT] — только недостача; сбрасывается при смене описи
const filter = ref<ChoiceId[]>([])
watch(
  () => props.detail.id,
  () => (filter.value = []),
)
const shortOnly = computed(() => filter.value.includes(SHORT))
// колонка «Не хватает»: всегда (shortageAlways) или при фильтре недостачи
const shortColumn = computed(() => props.shortageAlways || shortOnly.value)

const values = computed(() => new Map(props.detail.values.map((v) => [v.equipListTemplateItem, v])))

/** Сколько не хватает до требуемого; 0 — хватает или требуемое не задано. */
function shortage(v: EquipListValue): number {
  return v.quantityRequired !== null && v.quantityActual < v.quantityRequired
    ? v.quantityRequired - v.quantityActual
    : 0
}

/** «Не хватает»: сколько не хватает; хватает — «по норме»; требуемое не задано — пусто. */
function shortageText(v: EquipListValue): string {
  if (v.quantityRequired === null) return ''
  const n = shortage(v)
  return n > 0 ? numText(n) : t('по норме')
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
.equip-view,
.equip-view__col,
.equip-view__card {
  display: contents;
}

.equip-view--columns {
  display: flex;
  align-items: flex-start;
  gap: 1rem;

  // на всю высоту владельца: прокручиваются карточка и позиции по отдельности, а не страница
  flex: 1;
  min-height: 0;

  // колонка: подзаголовок и рамка под ним
  > .equip-view__col {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    min-width: 0;
    max-height: 100%;
  }

  // карточка и позиции растут вместе с окном в отношении 2 : 3; карточка не уже 24rem
  > .equip-view__col--card {
    flex: 2 1 0;
    min-width: 24rem;
  }

  > .equip-view__col--items {
    flex: 3 1 0;
    align-self: stretch;
  }

  .equip-view__card {
    display: flex;
    flex-direction: column;
    gap: 1rem;
    min-height: 0;
    padding: 1rem;
    overflow-y: auto;
  }

  // позиции: сверху без отступа — заголовки колонок прилипают к самому краю рамки и сами дают отступ
  .equip-view__items {
    flex: 1;
    min-height: 0;
    padding: 0 1rem 1rem;
    overflow-y: auto;
  }

  // карточка и позиции — в рамках, как группы в редакторе маршрута (RouteEditPage)
  .equip-view__card,
  .equip-view__items {
    border: 0.0625rem solid $zs-border;
    border-radius: 0.3rem;
    background: $zs-bg-panel;
  }

  // заголовки колонок при прокрутке позиций остаются наверху
  .equip-view__head {
    position: sticky;
    top: 0;
    z-index: 1;
    padding-top: 1rem;
    background: $zs-bg-panel;
  }
}

.equip-view__section-title {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.equip-view__violations {
  display: flex;
  // число и кнопка — в одной строке
  flex-wrap: nowrap;
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
