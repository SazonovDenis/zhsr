<template>
  <FormDialog v-model:show="show" min-height="lg" :title="title" ok-text="Готово" width="40rem" @ok="emit('done', true)">
    <div class="dict-values">
      <ErrorBox v-if="loadError" :error="loadError" :retry="load" />
      <template v-else>
        <div
          v-for="(v, i) in values"
          :key="v.id"
          class="dict-values__row q-py-sm q-px-md"
          :class="{ 'dict-values__row--off': v.disabled }"
        >
          <span class="dict-values__order">
            <button
              v-for="up in [false, true]"
              :key="String(up)"
              type="button"
              class="dict-values__move q-pa-none"
              :disabled="moving || (up ? i === 0 : i === values.length - 1)"
              :title="up ? t('Выше') : t('Ниже')"
              @click="move(v, up)"
            >
              {{ up ? '▲' : '▼' }}
            </button>
          </span>
          <button
            type="button"
            class="dict-values__text zs-focusable q-pa-none"
            :title="t('Изменить')"
            @click="edit(v)"
          >
            {{ v.textRu }}
          </button>
          <span class="zs-sub">{{ [v.textKk, v.textEn].filter(Boolean).join(' • ') }}</span>
        </div>
        <div v-if="!busy.active.value && values.length === 0" class="zs-sub q-pa-md">
          {{ t('Вариантов ответа нет') }}
        </div>
      </template>
    </div>
    <template #actions-left>
      <q-btn no-caps unelevated outline color="primary" :label="t('Добавить вариант')" @click="edit(null)" />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'

import type { CheckDict, CheckDictValue } from '@/api/checks'
import { deleteCheckDictValue, fetchCheckDictValues, saveCheckDictValue } from '@/api/checks'
import { moveSortOrder } from '@/api/admin'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { nextOrder, openSpecDialog, ORDER_FIELDS, TEXT_FIELDS, textValues } from '../shared/spec'

/**
 * Варианты ответа шаблона ответов (Ф-ЧЕК-01): клик по варианту — правка, «+ Вариант» — новый вариант. Отключённый
 * вариант зачёркнут. Нарушение ли вариант, решает норматив пункта, а не вариант.
 */
const props = defineProps<{ dict: CheckDict }>()
const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [ok: boolean] }>()

const busy = provideBusy()
const values = shallowRef<CheckDictValue[]>([])
const loadError = ref<ShownError | null>(null)
const title = computed(() => t('Варианты ответов: {name}', { name: props.dict.textRu }))

async function load() {
  loadError.value = null
  try {
    values.value = await busy.run(fetchCheckDictValues(props.dict.id))
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void load()

// перестановка вариантов кнопками ▲▼: ошибка — в месте списка
const moving = ref(false)
async function move(value: CheckDictValue, up: boolean) {
  moving.value = true
  try {
    await moveSortOrder('check_dict_value', value.id, up)
    await load()
  } catch (e) {
    loadError.value = shownError(e)
  } finally {
    moving.value = false
  }
}

async function edit(value: CheckDictValue | null) {
  const result = await openSpecDialog({
    title: value ? 'Вариант ответа' : 'Новый вариант ответа',
    okText: value ? 'Сохранить' : 'Добавить',
    fields: [...TEXT_FIELDS, ...ORDER_FIELDS],
    initial: textValues(value, nextOrder(values.value)),
    save: (v) => saveCheckDictValue(props.dict.id, value?.id ?? null, v as never),
    remove: value ? () => deleteCheckDictValue(value.id) : null,
  })
  if (result !== undefined) await load()
}
</script>

<style scoped lang="scss">
.dict-values {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 6rem;
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
}

.dict-values__row {
  display: grid;
  grid-template-columns: 6rem minmax(0, 1fr) minmax(0, 1fr);
  gap: 0.8rem;
  align-items: center;
  border-bottom: 0.0625rem solid $zs-border;
  color: $zs-text;
  font-size: 0.9rem;

  &:hover {
    background: $zs-bg-hover;
  }

  &--off .dict-values__text {
    text-decoration: line-through;
  }
}

.dict-values__text {
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.dict-values__order {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
}

.dict-values__move {
  width: 2.5em;
  height: 1.8em;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.2rem;
  background: #fff;
  color: $zs-text-muted;
  font-size: 0.8rem;
  cursor: pointer;

  &:disabled {
    opacity: 0.3;
    cursor: default;
  }
}
</style>
