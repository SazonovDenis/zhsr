<template>
  <div class="photo-picker" :class="{ 'photo-picker--large': large }">
    <div v-for="(url, i) in urls" :key="url" class="photo-picker__photo">
      <img :src="url" :alt="t('Фото {n}', { n: i + 1 })" />
      <button type="button" class="photo-picker__remove" :title="t('Убрать фото')" @click="remove(i)">✕</button>
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

/**
 * Фото к заявке или пояснению: миниатюры выбранных фото с крестиком и плитка «+ Фото». На телефоне плитка открывает
 * камеру, на компьютере — выбор файла. Не больше max фото. v-model — выбранные файлы.
 */
const props = withDefaults(defineProps<{ max?: number; large?: boolean }>(), { max: 5, large: false })

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

function onPick(e: Event) {
  const files = Array.from((e.target as HTMLInputElement).files ?? [])
  model.value = [...model.value, ...files].slice(0, props.max)
  ;(e.target as HTMLInputElement).value = ''
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
  border-radius: 0.25rem;
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
  top: 0.25rem;
  right: 0.25rem;
  width: 1.4rem;
  height: 1.4rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.6875rem;
  cursor: pointer;
}

.photo-picker__add {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0.125rem dashed $zs-border;
  color: $zs-text-muted;
  font-size: 0.75rem;
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
