<template>
  <div class="wagon-list">
    <button
      v-if="pick.myWagon.value"
      type="button"
      class="wagon-list__row wagon-list__row--mine q-py-none q-px-md"
      @click="emit('pick', pick.myWagon.value)"
    >
      <span class="wagon-list__caption">{{ t('Мой вагон') }}</span>
      <span class="zs-mono wagon-list__number">{{ pick.myWagon.value.number }}</span>
      <span class="wagon-list__muted">{{ dictManager.getValue('WagonType', pick.myWagon.value.wagonType) }}</span>
    </button>
    <button
      v-if="pick.hasTrip.value"
      type="button"
      class="wagon-list__chip q-py-none q-px-md"
      :class="{ 'wagon-list__chip--on': pick.tripOn.value }"
      @click="pick.tripOn.value = !pick.tripOn.value"
    >
      {{ t('Рейс {trip}', { trip: pick.tripLabel.value }) }}
      <span v-if="pick.tripOn.value">✕</span>
    </button>
    <input
      :value="pick.query.value"
      class="wagon-list__input q-px-md"
      inputmode="numeric"
      :placeholder="t('Цифры номера вагона')"
      @input="onDigits"
    />
    <button
      v-for="w in pick.wagons.value"
      :key="w.number"
      type="button"
      class="wagon-list__row q-py-none q-px-md"
      @click="emit('pick', w)"
    >
      <span class="zs-mono wagon-list__number">{{ w.number }}</span>
      <span class="wagon-list__muted">{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
    </button>
    <LoadSpinner v-if="pick.loading.value && pick.wagons.value.length === 0" size="1.5rem" />
    <ErrorBox v-else-if="pick.error.value" :error="pick.error.value" :retry="pick.load" />
    <div v-else-if="!pick.loading.value && pick.wagons.value.length === 0" class="wagon-list__muted q-pa-md">
      {{ t('Вагоны не найдены') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { useWagonPick } from '@/shared/requests/useWagonPick'
import { currentUser } from '@/shared/user'

/**
 * Выбор вагона на телефоне для проверки и описи: «Мой вагон» проводника, фильтр по текущему рейсу чипом, поиск по цифрам
 * номера. Только при связи. Событие pick — выбранный вагон.
 */
const emit = defineEmits<{ pick: [wagon: RequestWagon] }>()

const pick = useWagonPick(() => currentUser.value)
void pick.load()

function onDigits(e: Event) {
  const input = e.target as HTMLInputElement
  input.value = input.value.replace(/\D/g, '')
  pick.query.value = input.value
}
</script>

<style scoped lang="scss">
.wagon-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.wagon-list__row {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  min-height: 3.3rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.5rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &--mine {
    border-color: $zs-selected-border;
    background: $zs-selected-bg;
  }
}

.wagon-list__number {
  font-size: 1.1rem;
  font-weight: 600;
}

.wagon-list__caption {
  color: $zs-text-muted;
  font-size: 0.7rem;
  font-weight: 700;
}

.wagon-list__muted {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.wagon-list__chip {
  align-self: flex-start;
  min-height: 2.1rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 62.5rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8rem;

  &--on {
    border-color: $zs-selected-border;
    background: $zs-selected-bg;
    color: $zs-selected-fg;
  }
}

.wagon-list__input {
  height: 3.3rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  font: inherit;
  font-size: 1.1rem;
}
</style>
