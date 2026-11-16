<template>
  <!-- ссылки «Добавить примечание» и «Добавить фото» подряд; поле и текст примечания, фото — на всю строку -->
  <div class="item-extras">
    <div class="item-extras__note" :class="{ 'item-extras__note--wide': editing || comment !== '' }">
      <!-- крестик в поле очищает текст и закрывает поле -->
      <NoteInput
        v-if="editing"
        v-model="comment"
        :rows="2"
        placeholder="Примечание"
        autofocus
        clear-empty
        @blur="close"
        @clear="close"
      />
      <!-- вне поля примечание — текстом: нажатие открывает поле, крестик — как у фото — удаляет примечание -->
      <div v-else-if="comment !== ''" class="item-extras__box">
        <button type="button" class="item-extras__text q-pa-none" @click="editing = true">{{ comment }}</button>
        <button
          type="button"
          class="item-extras__remove q-pa-none"
          :title="t('Удалить примечание')"
          @click="comment = ''"
        >
          ✕
        </button>
      </div>
      <SoftLink v-else text="Добавить примечание" @click="editing = true" />
    </div>
    <!-- пока фото нет — ссылка «Добавить фото»: сразу камера -->
    <PhotoPicker
      v-model="photos"
      :stored="stored"
      compress
      large
      link
      @remove-stored="(id) => emit('remove-stored', id)"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'

/**
 * Примечание и фото пункта чек-листа или позиции описи на телефоне. Примечание правится в поле; вне поля — текстом
 * (нажатие открывает поле), пустое — ссылкой «Добавить примечание». Уход из поля или крестик в поле закрывают поле;
 * примечание из одних пробелов — пустое. Фото — PhotoPicker со ссылкой «Добавить фото»; stored — сохранённые фото
 * (исправление описи), крестик на них посылает remove-stored.
 */
withDefaults(defineProps<{ stored?: RequestPhoto[] }>(), { stored: () => [] })

const comment = defineModel<string>('comment', { required: true })
const photos = defineModel<Blob[]>('photos', { required: true })
const emit = defineEmits<{ 'remove-stored': [id: string] }>()

/** примечание открыто в поле */
const editing = ref(false)

function close() {
  editing.value = false
  if (comment.value.trim() === '') comment.value = ''
}
</script>

<style scoped lang="scss">
.item-extras {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
}

.item-extras__note {
  flex: none;

  // поле примечания и введённое примечание — на всю строку; ссылка «Добавить фото» — строкой ниже
  &--wide {
    flex: 1 1 100%;
  }
}

.item-extras__box {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.item-extras__text {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  text-align: left;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.item-extras__remove {
  flex: none;
  width: 1.4rem;
  height: 1.4rem;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.7rem;
  cursor: pointer;
}
</style>
