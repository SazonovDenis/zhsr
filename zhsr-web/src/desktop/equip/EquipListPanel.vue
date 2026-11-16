<template>
  <SidePanel
    :empty="equipListId ? '' : 'Выберите опись в списке'"
    :title="detail ? textOf(detail.equipListTemplate) : ''"
    :subtitle="detail ? formatDateTime(detail.createdDt) : ''"
    @close="emit('close')"
  >
    <LoadSpinner v-if="busy.active.value && !detail" />
    <ErrorBox v-if="error" :error="error" :retry="load" />

    <EquipListView v-if="detail" :detail="detail">
      <!-- исправить — автор в течение 10 минут после сохранения; кнопка исчезает сама, когда срок вышел -->
      <div v-if="listEditable(detail, now)" class="q-mt-md">
        <q-btn unelevated outline no-caps class="zs-soft-border" color="ink" @click="edit">{{
          t('Внести изменения')
        }}</q-btn>
      </div>
    </EquipListView>
  </SidePanel>
</template>

<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { EquipListDetail } from '@/api/equip'
import { fetchEquipList } from '@/api/equip'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { textOf } from '@/shared/checks/texts'
import { listEditable } from '@/shared/checks/rights'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { useNow } from '@/shared/trips/tripTimes'
import { loadCurrentUser } from '@/shared/user'
import SidePanel from '../frame/SidePanel.vue'
import EquipListView from './EquipListView.vue'

/** Карточка описи экипировки (Ф-ЭКП-03): опись (EquipListView) и «Внести изменения». */
const props = defineProps<{ equipListId: number | null }>()
const emit = defineEmits<{ close: [] }>()

const busy = provideBusy()
const detail = shallowRef<EquipListDetail | null>(null)
const error = ref<ShownError | null>(null)

const router = useRouter()
const now = useNow()

/** Исправить опись: страница заполнения с сохранёнными значениями. */
function edit() {
  if (detail.value) void router.push({ name: 'equip-new', query: { edit: String(detail.value.id) } })
}

async function load() {
  const id = props.equipListId
  detail.value = null
  error.value = null
  if (id === null) return
  try {
    const [d] = await busy.run(
      Promise.all([fetchEquipList(id), dictManager.prepareDict('WagonType'), loadCurrentUser()]),
    )
    if (props.equipListId === id) detail.value = d
  } catch (e) {
    error.value = shownError(e)
  }
}
watch(() => props.equipListId, load, { immediate: true })
</script>
