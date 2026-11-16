import type { InjectionKey } from 'vue'
import { inject, provide } from 'vue'

import type { RequestPhoto } from '@/api/requests'

/**
 * Исправление примечаний и удаление фото в карточке заявки (Ф-ЗАЯ-05): автор — в течение 10 минут после создания.
 * Карточка (useRequestCard) передаёт эти действия вниз — миниатюрам фото (PhotoStrip) и ленте истории
 * (RequestHistory), чтобы не протаскивать их через все промежуточные компоненты. Нет карточки выше — кнопок нет.
 * Кнопка недоступного исправления не показывается и исчезает сама, когда срок вышел; окончательно решает сервер.
 */
export interface RequestChanges {
  canDeletePhoto(photo: RequestPhoto): boolean
  deletePhoto(photo: RequestPhoto): void
  canEditNote(noteId: number): boolean
  /** открыть ввод нового текста примечания; text — текущий текст */
  editNote(noteId: number, text: string): void
}

const KEY: InjectionKey<RequestChanges> = Symbol('requestChanges')

export function provideRequestChanges(changes: RequestChanges): void {
  provide(KEY, changes)
}

/** Действия карточки заявки выше по дереву; null — компонент показан не в карточке. */
export function useRequestChanges(): RequestChanges | null {
  return inject(KEY, null)
}
