<template>
  <SidePanel
    :empty="wagon ? '' : 'Выберите вагон в списке'"
    :title="wagon ? t('Вагон № {number}', { number: wagon.number }) : ''"
    :subtitle="
      wagon
        ? `${dictManager.getValue('WagonType', wagon.wagonType)} • ${dictManager.getValue('WagonModel', wagon.modelType)}`
        : ''
    "
    @close="emit('close')"
  >
    <SidePanelFields v-if="wagon">
      <span>{{ t('Депо приписки') }}</span>
      <span>{{ dictManager.getValue('Depot', wagon.depot) }}</span>
      <span>{{ t('Расположение') }}</span>
      <span>{{ wagonPlace(wagon) }}</span>
      <span>{{ t('Состояние') }}</span>
      <span><DictBadge :id="wagon.wagonStatusType" dict="WagonState" /></span>
    </SidePanelFields>
  </SidePanel>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonPlace } from '@/shared/wagonPlace'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'

/** Правая панель списка вагонов: карточка выбранного вагона. */
defineProps<{ wagon: WagonRow | null }>()

const emit = defineEmits<{ close: [] }>()
</script>
