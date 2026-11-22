import { Notify } from 'quasar'

import { t } from '@/boot/i18n'
import DeleteDialog from './DeleteDialog.vue'
import { openDialog } from './openDialog'

/**
 * Спросить подтверждение и удалить запись (DeleteDialog): удалено — всплывашка «Удалено», результат true. Удалить
 * нельзя — причина в окне. Тексты — на русском, переводятся при показе.
 *
 *   if (await confirmDelete('Удаление маршрута', 'Удалить маршрут {name}?', { name }, () => deleteRoute(id))) load()
 */
export async function confirmDelete(
  title: string,
  text: string,
  params: Record<string, unknown>,
  remove: () => Promise<void>,
): Promise<boolean> {
  const removed = await openDialog<boolean>(DeleteDialog, { title, text, params, remove })
  if (removed) Notify.create({ message: t('Удалено'), type: 'positive', position: 'bottom' })
  return !!removed
}
