import type { RepairRequest } from '@/api/requests'
import { t } from '@/boot/i18n'
import { RequestStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { RequestEvent } from './historyTree'

/**
 * Показ заявки на ремонт — общее для десктопной и мобильной частей: название неисправности, исполнитель и тексты
 * истории. Все тексты — уже переведённые.
 */

/** Справочники, нужные для показа заявок. */
export const REQUEST_DICTS = [
  'RequestStatus',
  'Severity',
  'Service',
  'FaultType',
  'FaultGroup',
  'FaultCategory',
  'WagonType',
  'Station',
  'Depot',
  'Yard',
] as const

/** Название неисправности заявки. */
export function faultName(fault: number): string {
  return dictManager.getValue('FaultType', fault)
}

/** Кому заявка: исполнитель, а если его нет — служба. */
export function executorText(r: Omit<RepairRequest, 'id'>): string {
  return r.executorUsr?.name ?? dictManager.getValue('Service', r.service)
}

/** Текст поверх фото в просмотре: кто и когда — сверху, текст примечания или описание — снизу. */
export interface PhotoCaption {
  meta: string
  text: string
}

/** Заявка, примечание или фото поступили на сервер более чем через минуту после создания: это показывается. */
export function receivedLate(created: string, received: string | null | undefined): boolean {
  return !!received && Math.abs(Date.parse(received) - Date.parse(created)) > 60_000
}

/** Цвет и насыщенность текста критичности — из справочника Severity. */
export function severityStyle(severity: number): Record<string, string> {
  const rec = dictManager.getRec('Severity', severity)
  return {
    color: (rec?.fg as string | undefined) ?? 'inherit',
    fontWeight: String((rec?.weight as number | undefined) ?? 400),
  }
}

/** Номер шага заявки на пути «Подана → В работе → Выполнена → Закрыта» по статусу заявки. */
const STEP_OF: Record<number, number> = {
  [RequestStatus.NEW]: 0,
  [RequestStatus.ASSIGNED]: 0,
  [RequestStatus.IN_PROGRESS]: 1,
  [RequestStatus.DONE]: 2,
  [RequestStatus.CLOSED]: 3,
}

const ACTION_EVENTS: Record<string, string> = {
  TAKE: 'Взята в работу',
  COMPLETE: 'Работа выполнена',
  RELEASE: 'Исполнитель снял себя',
  ACCEPT: 'Работа принята, заявка закрыта',
  RETURN: 'Возвращена на доработку',
  NOT_DONE: 'Не выполнена',
  REJECT: 'Отклонена',
  DEFER: 'Отложена до следующего ТО',
  CANCEL: 'Отменена',
  FORWARD: 'Передана в депо',
  DETACH: 'ТОР: вагон отцеплен',
}

/** Заголовок записи истории. */
export function eventTitle(e: RequestEvent): string {
  switch (e.kind) {
    case 'CREATED':
      return t('Заявка подана')
    case 'STATUS':
      return t(ACTION_EVENTS[e.action ?? ''] ?? 'Статус изменён')
    case 'ASSIGN':
      return t(e.executor ? 'Назначение' : 'Назначение снято')
    case 'NOTE':
      return t(e.text ? 'Примечание' : 'Приложено фото')
  }
}

/** Шаги, которые заявке ещё предстоят, — серым концом истории. */
export function futureSteps(status: number): { title: string; meta: string }[] {
  const all = [
    { title: t('В работе'), meta: t('работа не начата') },
    { title: t('Выполнена'), meta: t('работа не выполнена') },
    { title: t('Закрыта'), meta: t('приёмка не выполнена') },
  ]
  const from = status === RequestStatus.CLOSED ? undefined : STEP_OF[status]
  return from === undefined ? [] : all.slice(from)
}
