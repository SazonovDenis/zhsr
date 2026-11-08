import type { InjectionKey, Ref } from 'vue'

/**
 * Выбор вагонов акта приёма-передачи — общий для страницы акта и ячейки «Передача» / «Приём» в таблице вагонов:
 * номер вагона → передаётся с имуществом. Вагон без записи не отмечен.
 */
export interface TransferChoice {
  chosen: Ref<Record<string, boolean>>
  /** отметить вагон с имуществом или без; null — снять отметку */
  set(number: string, withProperty: boolean | null): void
}

export const TRANSFER_CHOICE: InjectionKey<TransferChoice> = Symbol('transferChoice')
