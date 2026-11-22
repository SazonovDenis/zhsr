import type { UserRef } from '@/api/requests'
import type { CrewWagon, TripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { CrewDuty, LocationType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { crewNorm, normDone } from './crewNorm'

/** Пункт готовности рейса к отправлению: ok — выполнен; detail — пояснение текстом или people — ФИО разметкой. */
export interface ReadinessItem {
  ok: boolean
  title: string
  detail: string
  people?: { label: string; user: UserRef | null }[]
}

/** Вагон на станции отправления рейса. */
export const atDepartureStation = (crew: TripCrew, w: CrewWagon) =>
  w.locationType === LocationType.STATION && w.place === crew.trip.departureStation

/**
 * Готовность рейса к отправлению (Ф-РЕЙ-02): поезд назначен и планы до отправления отмечены, вагоны на станции
 * отправления, начальник поезда и ПЭМ назначены, проводники по норме. Справочник Station должен быть загружен.
 */
export function tripReadiness(crew: TripCrew): ReadinessItem[] {
  const n = crewNorm(crew)
  const riding = crew.wagons.filter((w) => w.riding)
  const pending = crew.pending
  const station = dictManager.getValue('Station', crew.trip.departureStation)
  const chief = crew.crew.find((m) => m.crewDutyType === CrewDuty.LNP)
  const pem = crew.crew.find((m) => m.crewDutyType === CrewDuty.PEM)
  const atStation = riding.filter((w) => atDepartureStation(crew, w))
  return [
    {
      ok: !!crew.trip.train && pending.length === 0,
      title: t('Поезд назначен, планы до отправления отмечены'),
      detail: !crew.trip.train
        ? t('поезд не назначен')
        : pending.length
          ? t('не отмечены: {list}', { list: pending.map((c) => c.wagon.number).join(', ') })
          : crew.trip.train.name,
    },
    {
      ok: riding.length > 0 && atStation.length === riding.length,
      title: t('Вагоны на станции отправления'),
      detail: riding.length
        ? t('{a} из {b} на {station}', { a: atStation.length, b: riding.length, station })
        : t('в поезде нет вагонов'),
    },
    {
      ok: n.chiefAndPem,
      title: t('Начальник поезда и ПЭМ'),
      detail: '',
      people: [
        { label: t('ЛНП'), user: chief?.usr ?? null },
        { label: t('ПЭМ'), user: pem?.usr ?? null },
      ],
    },
    {
      ok: normDone(n),
      title: t('Проводники по норме'),
      detail:
        t('назначено {have} из {need}', { have: n.have, need: n.need }) +
        (n.notOk.length ? ` • ${t('не выполнена: {list}', { list: n.notOk.join(', ') })}` : ''),
    },
  ]
}
