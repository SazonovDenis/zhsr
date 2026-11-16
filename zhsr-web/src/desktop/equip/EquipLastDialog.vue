<template>
  <DialogFrame v-model:show="show" :title="t('Имущество вагона № {number}', { number })" width="40rem">
    <LoadSpinner v-if="busy.active.value" />
    <ErrorBox v-else-if="error" :error="error" :retry="load" />
    <div v-else-if="loaded && !detail" class="equip-last__empty">{{ t('Описей вагона нет') }}</div>
    <template v-else-if="detail">
      <!-- какая опись показана: шаблон и когда составлена -->
      <div class="equip-last__title q-mb-md">
        {{ textOf(detail.equipListTemplate) }} • {{ formatDateTime(detail.createdDt) }}
      </div>
      <EquipListView :detail="detail" />
    </template>
  </DialogFrame>
</template>

<script setup lang="ts">
import { ref, shallowRef } from 'vue'

import type { EquipListDetail } from '@/api/equip'
import { fetchLastEquipList } from '@/api/equip'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { textOf } from '@/shared/checks/texts'
import DialogFrame from '@/shared/components/dialog/DialogFrame.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { loadCurrentUser } from '@/shared/user'
import EquipListView from './EquipListView.vue'

/**
 * Список имущества вагона: последняя опись экипировки вагона — для всех, без ограничения видимости описей. Описей нет
 * — окно говорит об этом. Открывается через openDialog из карточки вагона.
 */
const props = defineProps<{ wagon: number; number: string }>()

const show = defineModel<boolean>('show', { default: false })

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
      Promise.all([fetchLastEquipList(props.wagon), dictManager.prepareDict('WagonType'), loadCurrentUser()]),
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
.equip-last__title {
  font-size: 0.9rem;
  font-weight: 600;
}

.equip-last__empty {
  color: $zs-text-muted;
}
</style>
