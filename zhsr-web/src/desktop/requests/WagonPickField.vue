<template>
  <div class="wagon-pick">
    <div class="wagon-pick__bar">
      <button
        v-if="pick.hasTrip.value"
        type="button"
        class="wagon-pick__chip zs-focusable q-py-none q-px-md"
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
        class="wagon-pick__row q-py-sm q-px-md"
        :class="{ 'wagon-pick__row--selected': model === w.number }"
        @click="choose(w)"
      >
        <span class="zs-mono wagon-pick__number">{{ w.number }}</span>
        <span>{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
        <span class="zs-sub wagon-pick__place">{{
          placeText(w.locationType, w.place, w.trip ? tripRefText(w.trip) : null)
        }}</span>
      </button>
      <LoadSpinner v-if="pick.loading.value && pick.wagons.value.length === 0" size="1.5rem" />
      <ErrorBox v-else-if="pick.error.value" :error="pick.error.value" :retry="pick.load" />
      <div v-else-if="!pick.loading.value && pick.wagons.value.length === 0" class="wagon-pick__empty q-pa-md">
        {{ t('Вагоны не найдены') }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { placeText } from '@/shared/wagonPlace'
import { useWagonPick } from '@/shared/requests/useWagonPick'
import { currentUser } from '@/shared/user'
import { tripRefText } from '@/shared/trips/tripRefText'

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
  gap: 0.4rem;
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 1.2rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;

  &--on {
    border-color: $zs-selected-border;
  }
}

.wagon-pick__chip-x {
  font-size: 0.7rem;
  opacity: 0.7;
}

.wagon-pick__note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.wagon-pick__list {
  max-height: 12rem;
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
}

.wagon-pick__row {
  display: grid;
  grid-template-columns: 5rem 8rem minmax(0, 1fr);
  gap: 0.8rem;
  align-items: center;
  width: 100%;
  border: 0;
  border-bottom: 0.0625rem solid $zs-border-row;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: $zs-row-hover-bg;
  }

  &--selected {
    background: $zs-row-selected-bg;
    box-shadow: inset 0.2rem 0 0 $zs-row-selected-bar;
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
  color: $zs-text-muted;
  font-size: 0.9rem;
}
</style>
