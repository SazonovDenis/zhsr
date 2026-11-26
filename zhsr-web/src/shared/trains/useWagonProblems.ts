import type { Ref } from 'vue'
import { ref, shallowRef, watch } from 'vue'

import type { JournalAction, PlanProblem, TrainJournalId, WagonProblemQuery } from '@/api/trains'
import { fetchJournalProblems, fetchWagonProblems } from '@/api/trains'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'

/** Предупреждения с сервера и справочники для их текстов. */
export async function loadProblems(fetch: () => Promise<PlanProblem[]>): Promise<PlanProblem[]> {
  const [res] = await Promise.all([fetch(), dictManager.prepareDicts('Station', 'Depot', 'Yard', 'LocationKind')])
  return res
}

/** Предупреждения по запросу query; запрос изменился — загружаются заново; null — запрашивать нечего. */
function useProblemRows<Q>(
  query: () => Q | null,
  fetch: (q: Q) => Promise<PlanProblem[]>,
): { rows: Ref<PlanProblem[]>; error: Ref<ShownError | null> } {
  const rows = shallowRef<PlanProblem[]>([])
  const error = ref<ShownError | null>(null)
  // ответ на прежний запрос не должен заменить ответ на новый
  let last = 0

  watch(
    () => JSON.stringify(query()),
    async () => {
      const current = ++last
      const q = query()
      error.value = null
      if (!q) {
        rows.value = []
        return
      }
      try {
        const res = await loadProblems(() => fetch(q))
        if (current === last) rows.value = res
      } catch (e) {
        if (current !== last) return
        rows.value = []
        error.value = shownError(e)
      }
    },
    { immediate: true },
  )
  return { rows, error }
}

/**
 * Предупреждения окон прицепки, отцепки, отметки и исправления плана — что будет мешать планам вагонов, если сохранить:
 * запрашиваются при открытии окна и при изменении выбора. query — запрос по выбору окна; null — запрашивать нечего.
 * error — ошибка загрузки: окно показывает её у себя.
 */
export function useWagonProblems(query: () => WagonProblemQuery | null) {
  return useProblemRows(
    () => {
      const q = query()
      return q && q.wagons.length > 0 ? q : null
    },
    (q) => fetchWagonProblems(q),
  )
}

/**
 * Предупреждения окна отмены плана или удаления факта. query — запись журнала и действие; null — окно закрыто или
 * действие недоступно. error — ошибка загрузки: окно показывает её у себя.
 */
export function useJournalProblems(query: () => { change: TrainJournalId; action: JournalAction } | null) {
  return useProblemRows(
    () => {
      const q = query()
      return q && { code: q.change.code, id: q.change.id, action: q.action }
    },
    (q) => fetchJournalProblems(q, q.action),
  )
}
