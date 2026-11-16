<template>
  <!-- телефон: миниатюры с плиткой — всю строку; ссылка «Добавить фото» — по своей ширине -->
  <div class="photo-picker" :class="{ 'photo-picker--large': large, 'photo-picker--row': isMobile && !empty }">
    <input ref="input" type="file" accept="image/*" capture="environment" multiple hidden @change="onPick" />
    <!-- фото ещё нет и задан link — ссылка «Добавить фото»; отмена съёмки ничего не меняет -->
    <SoftLink v-if="empty" text="Добавить фото" @click="pick" />
    <template v-else>
      <!-- сохранённые фото — первыми; крестик просит вызывающего убрать фото (remove-stored) -->
      <div v-for="(photo, i) in stored" :key="photo.id" class="photo-picker__photo">
        <button type="button" class="photo-picker__view q-pa-none" :title="t('Открыть фото')" @click="view(i)">
          <img :src="photo.url ?? undefined" :alt="t('Фото {n}', { n: i + 1 })" />
        </button>
        <button
          type="button"
          class="photo-picker__remove q-pa-none"
          :title="t('Убрать фото')"
          @click="emit('remove-stored', photo.id)"
        >
          ✕
        </button>
      </div>
      <div v-for="(url, i) in urls" :key="url" class="photo-picker__photo">
        <button
          type="button"
          class="photo-picker__view q-pa-none"
          :title="t('Открыть фото')"
          @click="view(stored.length + i)"
        >
          <img :src="url" :alt="t('Фото {n}', { n: stored.length + i + 1 })" />
        </button>
        <button type="button" class="photo-picker__remove q-pa-none" :title="t('Убрать фото')" @click="remove(i)">
          ✕
        </button>
      </div>
      <button
        v-if="stored.length + model.length < max"
        type="button"
        class="photo-picker__add zs-focusable q-pa-none"
        @click="pick"
      >
        + {{ t('Фото') }}
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import { isMobile } from '@/shared/device'
import { openDialog } from '@/shared/components/dialog/openDialog'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { compressPhoto } from './compressPhoto'
import PhotoViewer from './PhotoViewer.vue'

/**
 * Фото к заявке, примечанию или пункту проверки: добавление, миниатюры с крестиком и просмотр. Плитка «+ Фото» на
 * телефоне открывает камеру, на компьютере — выбор файла. Нажатие на миниатюру открывает просмотр на весь экран — и у
 * только что добавленных фото. Не больше max фото. v-model — выбранные файлы. compress — сжимать фото при выборе: на
 * телефоне, где фото могут ждать связи и уходить по плохой связи. link — пока фото нет, вместо плитки ссылка «Добавить
 * фото»: нажатие сразу открывает камеру, отмена ничего не меняет, снятое фото показывает миниатюры и плитку. На
 * телефоне миниатюры с плиткой занимают всю строку. stored — уже сохранённые фото (исправление документа): они
 * показываются первыми и входят в max; крестик посылает remove-stored с id фото, убирает фото вызывающий.
 */
const props = withDefaults(
  defineProps<{ max?: number; large?: boolean; compress?: boolean; link?: boolean; stored?: RequestPhoto[] }>(),
  { max: 5, large: false, compress: false, link: false, stored: () => [] },
)

const model = defineModel<Blob[]>({ default: () => [] })
const emit = defineEmits<{ 'remove-stored': [id: string] }>()

/** фото нет и задан link — вместо миниатюр ссылка «Добавить фото» */
const empty = computed(() => props.link && model.value.length === 0 && props.stored.length === 0)

const input = ref<HTMLInputElement | null>(null)
const urls = ref<string[]>([])

// миниатюры — адреса файлов в памяти; старые адреса освобождаются
watch(
  model,
  (files) => {
    urls.value.forEach((url) => URL.revokeObjectURL(url))
    urls.value = files.map((file) => URL.createObjectURL(file))
  },
  { immediate: true },
)

onBeforeUnmount(() => urls.value.forEach((url) => URL.revokeObjectURL(url)))

async function onPick(e: Event) {
  const files = Array.from((e.target as HTMLInputElement).files ?? [])
  ;(e.target as HTMLInputElement).value = ''
  // по одному: несколько больших фото разом не помещаются в память телефона. Список копится здесь: model.value после
  // присваивания остаётся прежним, пока родитель не перерисуется, — иначе остаётся только последнее фото
  let picked = model.value
  const max = props.max - props.stored.length
  for (const file of files) {
    if (picked.length >= max) break
    const photo = props.compress ? await compressPhoto(file) : file
    picked = [...picked, photo].slice(0, max)
    model.value = picked
  }
}

function remove(index: number) {
  model.value = model.value.filter((_, i) => i !== index)
}

/** Открыть камеру или выбор файла. */
function pick() {
  input.value?.click()
}

/** Просмотр на весь экран с листанием всех фото — сохранённых и выбранных; index — какое показать первым. */
function view(index: number) {
  const photos = [...props.stored, ...urls.value.map((url) => ({ id: url, url }))]
  void openDialog(PhotoViewer, { photos, index })
}
</script>

<style scoped lang="scss">
.photo-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;

  &--row {
    flex: 1 1 100%;
    width: 100%;
  }
}

.photo-picker__photo,
.photo-picker__add {
  position: relative;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 0.3rem;
}

.photo-picker__photo {
  overflow: hidden;
}

.photo-picker__view {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: none;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.photo-picker__remove {
  position: absolute;
  top: 0.3rem;
  right: 0.3rem;
  width: 1.4rem;
  height: 1.4rem;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.7rem;
  cursor: pointer;
}

.photo-picker__add {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0.1rem dashed $zs-border;
  background: none;
  color: $zs-text-muted;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;

  &:hover {
    background: $zs-bg-hover;
  }
}

.photo-picker--large {
  .photo-picker__photo,
  .photo-picker__add {
    width: 4.8rem;
    height: 4.8rem;
  }
}
</style>
