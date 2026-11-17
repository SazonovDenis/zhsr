<template>
  <BottomSheet v-model:show="show" :title="title" :subtitle="subtitle">
    <ErrorBox v-if="error" :error="error" :retry="retry" class="pick-sheet__error q-py-sm q-px-md" />
    <div v-else class="pick-sheet" @pointerdown="saveError = null">
      <button
        v-for="o in options"
        :key="String(o.id)"
        type="button"
        class="pick-sheet__item q-py-xs q-px-sm"
        :class="{ 'pick-sheet__item--on': picked === o.id }"
        :disabled="o.disabled"
        @click="picked = o.id"
        @dblclick="pickNow(o)"
      >
        <!-- слот option — свой вид строки варианта -->
        <slot name="option" :option="o">
          <span>{{ o.name }}</span>
          <span v-if="o.hint" class="pick-sheet__hint" :class="{ 'pick-sheet__hint--warn': o.warn }">{{ o.hint }}</span>
        </slot>
      </button>
    </div>
    <!-- ошибка сохранения (save) — под списком, с крестиком; лист остаётся открытым; любое касание списка её убирает -->
    <ErrorBox
      v-if="saveError"
      :error="saveError"
      closable
      class="pick-sheet__error q-py-sm q-px-md"
      @close="saveError = null"
    />
    <template #buttons>
      <q-btn
        unelevated
        outline
        no-caps
        color="ink"
        class="pick-sheet__btn zs-pwa-btn zs-soft-border"
        :label="t('Отмена')"
        :disable="saving"
        @click="show = false"
      />
      <q-btn
        unelevated
        no-caps
        color="primary"
        class="pick-sheet__btn pick-sheet__btn--primary zs-pwa-btn"
        :disable="!!error || disabled"
        :loading="saving"
        :label="t(okText)"
        @click="confirm"
      />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts" generic="T extends string | number | null">
import { ref, shallowRef, watch } from 'vue'

import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import BottomSheet from './BottomSheet.vue'

/** Вариант выбора. */
export interface PickOption<T> {
  id: T
  name: string
  /** вторая строка мелким */
  hint?: string
  /** hint — предупреждением */
  warn?: boolean
  disabled?: boolean
}

/**
 * Назначение: лист снизу со списком вариантов и кнопками «Отмена» и «Назначить». Выбор — нажатием на строку, применяется
 * кнопкой: событие pick(id). selected — что выбрано при открытии. Двойное касание варианта — выбор и применение сразу. Слот option — свой вид строки варианта. error — вместо списка, например нет связи; retry —
 * повторить загрузку. save — лист сохраняет выбор сам: спиннер на кнопке, ошибка — в листе, лист остаётся открытым;
 * удалось — лист закрывается и посылает pick. Без save — лист закрывается и посылает pick сразу.
 */
const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    options: PickOption<T>[]
    selected: T
    error?: ShownError | null
    retry?: (() => unknown) | null
    disabled?: boolean
    /** текст кнопки применения на русском */
    okText?: string
    /** сохранить выбор; ошибка — бросить, лист её покажет; null — сохраняет владелец по событию pick */
    save?: ((id: T) => Promise<void>) | null
  }>(),
  { subtitle: '', error: null, retry: null, disabled: false, okText: 'Назначить', save: null },
)

const emit = defineEmits<{ pick: [id: T] }>()

const show = defineModel<boolean>('show', { default: false })

const picked = shallowRef<T>(props.selected)
const saving = ref(false)
const saveError = ref<ShownError | null>(null)
// другой выбор — ошибка прошлого сохранения уже не о нём
watch(picked, () => (saveError.value = null))

watch(show, (open) => {
  if (!open) return
  picked.value = props.selected
  saveError.value = null
})

async function confirm() {
  if (saving.value) return
  const id = picked.value
  if (props.save) {
    saving.value = true
    saveError.value = null
    try {
      await props.save(id)
    } catch (e) {
      saveError.value = shownError(e)
      return
    } finally {
      saving.value = false
    }
  }
  show.value = false
  emit('pick', id)
}

// двойное касание варианта — выбор и применение сразу, как кнопкой
function pickNow(o: PickOption<T>) {
  if (o.disabled || props.disabled || props.error || saving.value) return
  picked.value = o.id
  void confirm()
}
</script>

<style scoped lang="scss">
.pick-sheet {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  // список шире полей листа (1rem, поля тела листа): до края листа — 0.5rem
  margin: 0 -0.5rem;
  overflow-y: auto;
}

.pick-sheet__item {
  display: flex;
  flex-direction: column;
  min-height: 2.8rem;
  justify-content: center;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }

  &--on {
    border-color: $zs-selected-border;
    background: $zs-selected-bg;
    color: $zs-selected-fg;
  }
}

.pick-sheet__hint {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;

  &--warn {
    color: $zs-error-fg;
  }
}

.pick-sheet__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

// кнопки — q-btn размера zs-pwa-btn; здесь только доли ширины в ряду
.pick-sheet__btn {
  flex: 1;

  &--primary {
    flex: 2;
  }
}
</style>
