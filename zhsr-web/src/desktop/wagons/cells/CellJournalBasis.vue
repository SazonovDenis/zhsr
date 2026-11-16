<template>
  <div>
    <button
      v-if="row.checkList || row.equipList"
      type="button"
      class="cell-journal-basis__link q-pa-none"
      @click="openDocument"
    >
      {{ basisText(row) }}
    </button>
    <button v-else-if="row.basisTrip" type="button" class="cell-journal-basis__link q-pa-none" @click="openTrip">
      {{ basisText(row) }}
    </button>
    <span v-else>{{ basisText(row) }}</span>
  </div>
  <div v-if="row.comment" class="zs-sub">«{{ row.comment }}»</div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import type { CellProps } from '@/shared/components/table/types'
import type { JournalRow } from '../journalColumns'
import { basisText } from '../journalText'

/**
 * Основание записи журнала: документ и причина из него. Отправление, прибытие, отцепка и прицепка — ссылка на
 * формирование рейса, где видны и акты рейса; проверка и опись — ссылка на карточку в своём списке.
 */
const props = defineProps<CellProps<JournalRow>>()

const router = useRouter()

/** Проверка или опись: список с выбранной карточкой. */
function openDocument() {
  const r = props.row
  if (r.checkList) void router.push({ name: 'checks', query: { id: String(r.checkList) } })
  else if (r.equipList) void router.push({ name: 'equip', query: { id: String(r.equipList) } })
}

function openTrip() {
  if (props.row.basisTrip) void router.push({ name: 'trip-form', params: { id: props.row.basisTrip.id } })
}
</script>

<style scoped lang="scss">
.cell-journal-basis__link {
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
</style>
