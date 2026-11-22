import type { Router } from 'vue-router'

import type { TrainRow } from '@/api/trains'
import { openDialog } from '@/shared/components/dialog/openDialog'
import TrainNameDialog from './TrainNameDialog.vue'

/**
 * Открыть диалог нового поезда и, если поезд создан, перейти в его состав: вагоны добавляются там.
 * Вызывается из списка поездов.
 */
export async function openTrainNew(router: Router): Promise<void> {
  const train = await openDialog<TrainRow>(TrainNameDialog)
  if (train) await router.push({ name: 'train-form', params: { id: train.id } })
}
