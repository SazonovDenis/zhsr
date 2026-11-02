<template>
  <div class="wagon-pick">
    <div class="wagon-pick__bar">
      <button
        v-if="pick.hasTrip.value"
        type="button"
        class="wagon-pick__chip zs-focusable"
        :class="{ 'wagon-pick__chip--on bg-selected text-selected': pick.tripOn.value }"
        :title="pick.tripOn.value ? t('Снять фильтр') : t('Включить фильтр')"
        @click="pick.tripOn.value = !pick.tripOn.value"
      >
        {{ t('Рейс {trip}', { trip: pick.tripLabel.value }) }}
        <span v-if="pick.tripOn.value" class="wagon-pick__chip-x">✕</span>
      </button>
      <TextInput v-model="digits" mono inputmode="numeric" placeholder="Цифры номера вагона" width="12rem" />
    </div>
    <div v-if="pick.hasTrip.value && pick.tripOn.value && pick.query.value" class="wagon-pick__note">
      {{ t('Поиск по номеру — фильтр по рейсу не действует') }}
    </div>
    <div class="wagon-pick__list">
      <button
        v-for="w in pick.wagons.value"
        :key="w.number"
        type="button"
        class="wagon-pick__row"
        :class="{ 'wagon-pick__row--selected': model === w.number }"
        @click="choose(w)"
      >
        <span class="zs-mono wagon-pick__number">{{ w.number }}</span>
        <span>{{ dictManager.getValue('WagonType', w.type) }}</span>
        <span class="zs-sub wagon-pick__place">{{ w.place }}</span>
      </button>
      <div v-if="!pick.loading.value && pick.wagons.value.length === 0" class="wagon-pick__empty">
        {{ t('Вагоны не найдены') }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { useWagonPick } from '@/shared/requests/useWagonPick'
import { currentUser } from '@/shared/user'

/**
 * Вагон заявки в диалоге десктопа: фильтр по текущему рейсу пользователя чипом (снимается крестиком), поиск по цифрам
 * номера и список вагонов. v-model — номер вагона; событие pick — выбранный вагон целиком (рейс вагона нужен форме).
 */
const model = defineModel<string>({ default: '' })
const emit = defineEmits<{ pick: [wagon: RequestWagon] }>()

const pick = useWagonPick(() => currentUser.value)

// цифры номера: всё, кроме цифр, отбрасывается
const digits = ref('')
watch(digits, (v) => {
  const clean = v.replace(/\D/g, '')
  if (clean !== v) digits.value = clean
  pick.query.value = clean
})

function choose(w: RequestWagon) {
  model.value = w.number
  emit('pick', w)
}

onMounted(() => void pick.load())
</script>

<style scoped lang="scss">
.wagon-pick {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  color: $zs-text;
  font-weight: 400;
}

.wagon-pick__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.wagon-pick__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  height: var(--zs-control-height);
  padding: 0 0.75rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 1.2rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8125rem;
  cursor: pointer;

  &--on {
    border-color: $zs-selected-border;
  }
}

.wagon-pick__chip-x {
  font-size: 0.6875rem;
  opacity: 0.7;
}

.wagon-pick__note {
  color: $zs-text-muted;
  font-size: 0.75rem;
}

.wagon-pick__list {
  max-height: 12rem;
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
}

.wagon-pick__row {
  display: grid;
  grid-template-columns: 5rem 8rem minmax(0, 1fr);
  gap: 0.75rem;
  align-items: center;
  width: 100%;
  padding: 0.4375rem 0.75rem;
  border: 0;
  border-bottom: 0.0625rem solid $zs-border-row;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: $zs-row-hover-bg;
  }

  &--selected {
    background: $zs-row-selected-bg;
    box-shadow: inset 0.1875rem 0 0 $zs-row-selected-bar;
  }
}

.wagon-pick__number {
  font-weight: 600;
}

.wagon-pick__place {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wagon-pick__empty {
  padding: 0.75rem;
  color: $zs-text-muted;
  font-size: 0.875rem;
}
</style>
