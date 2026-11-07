<template>
  <div class="photo-picker" :class="{ 'photo-picker--large': large }">
    <div v-for="(url, i) in urls" :key="url" class="photo-picker__photo">
      <img :src="url" :alt="t('Фото {n}', { n: i + 1 })" />
      <button type="button" class="photo-picker__remove q-pa-none" :title="t('Убрать фото')" @click="remove(i)">
        ✕
      </button>
    </div>
    <label
      v-if="model.length < max"
      class="photo-picker__add zs-focusable"
      tabindex="0"
      @keydown.enter="input?.click()"
    >
      + {{ t('Фото') }}
      <input ref="input" type="file" accept="image/*" capture="environment" multiple hidden @change="onPick" />
    </label>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import { t } from '@/boot/i18n'
import { compressPhoto } from './compressPhoto'

/**
 * Фото к заявке или примечанию: миниатюры выбранных фото с крестиком и плитка «+ Фото». На телефоне плитка открывает
 * камеру, на компьютере — выбор файла. Не больше max фото. v-model — выбранные файлы. compress — сжимать фото при
 * выборе: на телефоне, где фото могут ждать связи и уходить по плохой связи.
 */
const props = withDefaults(defineProps<{ max?: number; large?: boolean; compress?: boolean }>(), {
  max: 5,
  large: false,
  compress: false,
})

const model = defineModel<Blob[]>({ default: () => [] })

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
  // по одному: несколько больших фото разом не помещаются в память телефона
  for (const file of files) {
    const photo = props.compress ? await compressPhoto(file) : file
    model.value = [...model.value, photo].slice(0, props.max)
  }
}

function remove(index: number) {
  model.value = model.value.filter((_, i) => i !== index)
}
</script>

<style scoped lang="scss">
.photo-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
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
  color: $zs-text-muted;
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
