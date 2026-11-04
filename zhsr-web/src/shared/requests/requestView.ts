import type { RepairRequest, RequestEvent } from '@/api/requests'
import { t } from '@/boot/i18n'
import { RequestStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Показ заявки на ремонт — общее для десктопной и мобильной частей: название неисправности, исполнитель, шаги
 * «Подана → В работе → Выполнена → Закрыта», «что дальше» и тексты истории. Все тексты — уже переведённые.
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

/** Цвет и насыщенность текста критичности — из справочника Severity. */
export function severityStyle(severity: number): Record<string, string> {
  const rec = dictManager.getRec('Severity', severity)
  return {
    color: (rec?.fg as string | undefined) ?? 'inherit',
    fontWeight: String((rec?.weight as number | undefined) ?? 400),
  }
}

/** Шаг заявки на пути «Подана → В работе → Выполнена → Закрыта». */
export interface RequestStep {
  label: string
  state: 'done' | 'current' | 'todo'
}

const STEP_OF: Record<number, number> = {
  [RequestStatus.NEW]: 0,
  [RequestStatus.ASSIGNED]: 0,
  [RequestStatus.IN_PROGRESS]: 1,
  [RequestStatus.DONE]: 2,
  [RequestStatus.CLOSED]: 3,
}

/** Шаги заявки; queued — заявка ещё в очереди на телефоне: ни один шаг не пройден. */
export function requestSteps(status: number, queued = false): RequestStep[] {
  const current = queued ? -1 : (STEP_OF[status] ?? 0)
  return ['Подана', 'В работе', 'Выполнена', 'Закрыта'].map((label, i) => ({
    label: t(label),
    state: i < current || (i === 3 && current === 3) ? 'done' : i === current ? 'current' : 'todo',
  }))
}

/** Что будет с заявкой дальше — подсказка под шагами. */
export function nextText(r: Omit<RepairRequest, 'id'>, queued = false): string {
  if (queued) return t('Отправится, когда появится связь')
  const who = executorText(r)
  switch (r.requestStatusType) {
    case RequestStatus.NEW:
    case RequestStatus.ASSIGNED:
      return t('{who} — взять в работу', { who })
    case RequestStatus.IN_PROGRESS:
      return t('{who} выполняет работу', { who })
    case RequestStatus.DONE:
      return r.wagonTrip
        ? t('Проводник вагона или начальник поезда принимает работу')
        : t('Инструктор поездных бригад или эксплуатационщик депо принимает работу')
    case RequestStatus.CLOSED:
      return t('Работа принята, запись внесена в журнал ВУ-8')
    case RequestStatus.REJECTED:
      return t('Заявка отклонена')
    case RequestStatus.DEFERRED:
      return t('Будет выполнена на следующем ТО')
    case RequestStatus.CANCELLED:
      return t('Заявка отменена')
    default:
      return ''
  }
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
      return e.executor ? t('Назначен исполнитель: {name}', { name: e.executor.name }) : t('Назначение снято')
    case 'NOTE':
      return t('Пояснение')
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
