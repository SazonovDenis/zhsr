import type { RequestDetail } from '@/api/requests'
import { textOf } from '@/shared/checks/texts'

/** Источник заявки: чек-лист или опись, по которым Система создала заявку. */
export interface RequestSource {
  kind: 'check' | 'equip'
  id: number
  /** текст ссылки на русском с подстановкой name: «Чек-лист «{name}»» */
  text: string
  /** название шаблона на языке интерфейса */
  name: string
}

/** Источник заявки по карточке; null — заявку подал сотрудник. */
export function requestSource(r: RequestDetail | null | undefined): RequestSource | null {
  if (!r) return null
  if (r.checkList)
    return { kind: 'check', id: r.checkList, text: 'Чек-лист «{name}»', name: textOf(r.checkListTemplate) }
  if (r.equipList) return { kind: 'equip', id: r.equipList, text: 'Опись «{name}»', name: textOf(r.equipListTemplate) }
  return null
}
