import type { UserPlacePart } from '@/shared/components/UserOption.vue'
import type { ResponsibleCandidate } from '@/shared/wagons/responsible'
import { responsiblePlace } from '@/shared/wagons/responsible'

/** Где кандидат в ответственные для строки «Списка ФИО»; warning — предупреждение жирным после места. */
export function candidatePlace(c: ResponsibleCandidate, warning: string): UserPlacePart[] {
  const place = responsiblePlace(c)
  return [...(place ? [{ text: place }] : []), ...(warning ? [{ text: warning, match: true }] : [])]
}
