import { FaultCategory, RequestStatus, Severity, TripStatus, WagonStatus } from '@/shared/constants/systemDicts'
import type { DictName } from './dicts'

/**
 * Оформление значений системных справочников - только на фронте, в БД цветов нет. Привязка - по id через константы.
 * DictManager добавляет эти поля к записям справочника:
 * - bg, fg — цвета фона и текста плашки (DictBadge);
 * - fg, weight — цвет текста и насыщенность шрифта критичности в списках;
 * - color — цвет метки категории неисправности.
 */
export const DICT_COLORS: Partial<Record<DictName, Record<number, Record<string, string | number>>>> = {
  WagonState: {
    [WagonStatus.IN_IDLE]: { bg: '#ece3fa', fg: '#5b2ba8' },
    [WagonStatus.IN_TRIP]: { bg: '#ddebfa', fg: '#14508f' },
    [WagonStatus.IN_REPAIR]: { bg: '#fcebc8', fg: '#8a4b00' },
    [WagonStatus.RESERVE]: { bg: '#e6e8f0', fg: '#3b4470' },
    [WagonStatus.OUT_OF_SERVICE]: { bg: '#fbe1de', fg: '#a3261b' },
  },
  TripState: {
    [TripStatus.PLANNED]: { bg: '#ece3fa', fg: '#5b2ba8' },
    [TripStatus.IN_PROGRESS]: { bg: '#ddebfa', fg: '#14508f' },
    [TripStatus.DONE]: { bg: '#dff0e2', fg: '#1d5e2c' },
    [TripStatus.CANCELLED]: { bg: '#eef0f3', fg: '#62666d' },
  },
  RequestStatus: {
    [RequestStatus.NEW]: { bg: '#ddebfa', fg: '#14508f' },
    [RequestStatus.ASSIGNED]: { bg: '#e3e9fa', fg: '#2a3f8f' },
    [RequestStatus.IN_PROGRESS]: { bg: '#fcebc8', fg: '#8a4b00' },
    [RequestStatus.DONE]: { bg: '#ece3fa', fg: '#5b2ba8' },
    [RequestStatus.CLOSED]: { bg: '#dff0e2', fg: '#1d5e2c' },
    [RequestStatus.REJECTED]: { bg: '#eef0f3', fg: '#62666d' },
    [RequestStatus.DEFERRED]: { bg: '#e6e8f0', fg: '#3b4470' },
    [RequestStatus.CANCELLED]: { bg: '#fbe1de', fg: '#a3261b' },
  },
  Severity: {
    [Severity.LOW]: { fg: '#62666d', weight: 400 },
    [Severity.MEDIUM]: { fg: '#3d4650', weight: 500 },
    [Severity.HIGH]: { fg: '#b45309', weight: 600 },
    [Severity.CRITICAL]: { fg: '#b91c1c', weight: 700 },
  },
  FaultCategory: {
    [FaultCategory.TECHNICAL]: { color: '#3b6fb6' },
    [FaultCategory.CLEANING]: { color: '#2f8f6a' },
    [FaultCategory.EQUIPMENT]: { color: '#b7832a' },
  },
}
