<template>
  <!-- «...»: открывает поиск по всем значениям -->
  <button type="button" class="cb-more" @click.stop="open">
    <q-icon name="more_horiz" size="1.1rem" />

    <q-menu ref="menu" no-parent-event :auto-close="single" @before-show="onBeforeShow" @show="searchInput?.focus()">
      <div class="cb-more__panel">
        <div class="cb-more__search">
          <q-input
            ref="searchInput"
            v-model="query"
            dense
            outlined
            :placeholder="t('Поиск...')"
            @click.stop
            @keydown="onKeydown"
          >
            <!-- свой крестик вместо clearable: крестик Quasar — кнопка, и Tab останавливался бы на нём -->
            <template #append>
              <q-icon v-if="query" name="cancel" class="cursor-pointer" @click.stop="query = ''" />
            </template>
          </q-input>
        </div>

        <q-list ref="list" dense>
          <q-item
            v-for="(item, index) in filtered"
            :key="item.id"
            clickable
            :disable="isDisabled(item)"
            :class="{
              'bg-selected': single && isSelected(item),
              'cb-more__item--highlighted': index === highlighted,
            }"
            @mouseenter="highlighted = index"
          >
            <q-item-section v-if="!single" side>
              <q-checkbox
                :model-value="isSelected(item)"
                dense
                :disable="isDisabled(item)"
                @update:model-value="pick(item)"
              />
            </q-item-section>
            <q-item-section @click.stop="pick(item)"><CbOption :item="item" /></q-item-section>
          </q-item>
          <q-item v-if="filtered.length === 0">
            <q-item-section class="text-grey text-center">{{ t('Нет вариантов') }}</q-item-section>
          </q-item>
        </q-list>

        <div v-if="!single" class="cb-more__footer">
          <q-btn no-caps dense color="primary" class="q-px-md" :label="t('Ок')" @click.stop="hide" />
        </div>
      </div>
    </q-menu>
  </button>
</template>

<script setup lang="ts">
import type { QInput, QList, QMenu } from 'quasar'
import { computed, nextTick, ref, watch } from 'vue'

import { t } from '@/boot/i18n'
import type { ChoiceItem } from './choice'
import { isDisabled, matchesQuery, nextEnabledIndex } from './choice'
import CbOption from './CbOption.vue'

/**
 * Кнопка «...» с меню поиска по всем значениям. Общая для CbButton и CbSegment.
 * Выбор отдаёт событием pick; что из него следует, решает владелец.
 */
const props = defineProps<{
  values: ChoiceItem[]
  single: boolean
  isSelected: (item: ChoiceItem) => boolean
}>()

const emit = defineEmits<{ pick: [item: ChoiceItem] }>()

const menu = ref<QMenu | null>(null)
const searchInput = ref<QInput | null>(null)
const list = ref<QList | null>(null)

const query = ref<string | null>('')
const highlighted = ref(-1)

const filtered = computed(() => {
  const q = (query.value ?? '').toLowerCase()
  if (!q) return props.values
  return props.values.filter((item) => matchesQuery(item, q))
})

watch(query, () => (highlighted.value = -1))

function open() {
  query.value = ''
  menu.value?.show()
}

function hide() {
  menu.value?.hide()
}

function onBeforeShow() {
  query.value = ''
  highlighted.value = -1
}

function pick(item: ChoiceItem) {
  if (isDisabled(item)) return
  emit('pick', item)
}

/** Выбирает значение под курсором. */
function activateHighlighted() {
  const item = filtered.value[highlighted.value]
  if (!item || isDisabled(item)) return
  pick(item)
  if (props.single) hide()
}

function onKeydown(e: KeyboardEvent) {
  const stop = () => {
    e.preventDefault()
    e.stopPropagation()
  }

  if (e.key === 'ArrowDown') {
    stop()
    highlighted.value = nextEnabledIndex(filtered.value, highlighted.value, 1)
    scrollHighlighted()
  } else if (e.key === 'ArrowUp') {
    stop()
    // курсора ещё нет или он на первом значении — встаём на первое доступное
    highlighted.value =
      highlighted.value <= 0
        ? nextEnabledIndex(filtered.value, -1, 1)
        : nextEnabledIndex(filtered.value, highlighted.value, -1)
    scrollHighlighted()
  } else if (e.key === 'Escape') {
    e.stopPropagation()
    hide()
  } else if (e.key === 'Enter') {
    stop()
    if (props.single) {
      activateHighlighted()
    } else {
      const item = filtered.value[highlighted.value]
      if (item && !props.isSelected(item) && !isDisabled(item)) pick(item)
      hide()
    }
  } else if (e.key === ' ' && highlighted.value >= 0) {
    stop()
    activateHighlighted()
  }
}

function scrollHighlighted() {
  void nextTick(() => {
    const el = list.value?.$el as HTMLElement | undefined
    el?.querySelectorAll('.q-item')[highlighted.value]?.scrollIntoView({ block: 'nearest' })
  })
}
</script>

<style scoped lang="scss">
.cb-more {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: $zs-text-muted;
  cursor: pointer;
}

.cb-more__panel {
  // ширина — по содержимому, не уже 16.7rem
  min-width: 16.7rem;
}

.cb-more__search {
  padding: 0.25rem;
}

.cb-more__footer {
  display: flex;
  justify-content: flex-end;
  padding: 0.25rem;
  border-top: 0.0625rem solid #f0f0f0;
}

// выбранная строка — общим фоном bg-selected (app.scss)
.cb-more__item--highlighted {
  background: $zs-bg-hover;
}
</style>
