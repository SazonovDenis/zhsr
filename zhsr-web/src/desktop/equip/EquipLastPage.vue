<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Имущество вагона № {number}', { number }) }}</h1>
    </template>

    <!-- на всю высоту фрейма: прокручивается список позиций, а не страница; отступы слева, справа и снизу — фрейма -->
    <div class="equip-last q-pt-md">
      <LoadSpinner v-if="busy.active.value" />
      <ErrorBox v-else-if="error" :error="error" :retry="load" />
      <div v-else-if="loaded && !detail" class="equip-last__empty">{{ t('Описей вагона нет') }}</div>
      <EquipListView v-else-if="detail" :detail="detail" columns shortage-always />
    </div>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { EquipListDetail } from '@/api/equip'
import { fetchLastEquipList } from '@/api/equip'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import EquipListView from './EquipListView.vue'

/**
 * Список имущества вагона: последняя опись экипировки вагона — для всех, без ограничения видимости описей. Слева
 * карточка описи, справа позиции. Описей нет — страница говорит об этом. Открывается из карточки вагона; «назад» —
 * в список вагонов.
 */
const props = defineProps<{ id: string; number: string }>()

const router = useRouter()
const back = () => void router.push({ name: 'wagons' })

const busy = provideBusy()
const detail = shallowRef<EquipListDetail | null>(null)
const error = ref<ShownError | null>(null)
// поиск последней описи закончен: detail пуст — описей нет
const loaded = ref(false)

async function load() {
  error.value = null
  loaded.value = false
  try {
    const [last] = await busy.run(
      Promise.all([fetchLastEquipList(Number(props.id)), dictManager.prepareDict('WagonType'), loadCurrentUser()]),
    )
    detail.value = last
    loaded.value = true
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()
</script>

<style scoped lang="scss">
.equip-last {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.equip-last__empty {
  color: $zs-text-muted;
}
</style>
