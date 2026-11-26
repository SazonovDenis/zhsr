import type { PlanProblem, TrainJournalId } from '@/api/trains'
import { fetchJournalProblems } from '@/api/trains'
import { openDialog } from '@/shared/components/dialog/openDialog'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { loadProblems } from '@/shared/trains/useWagonProblems'
import ChangeWarningsDialog from './ChangeWarningsDialog.vue'

/**
 * Перед отменой плана: есть предупреждения — спросить подтверждение окном с ними (не загрузились — окном с ошибкой);
 * предупреждений нет — действие без окна.
 *
 * @return true — выполнять действие
 */
export async function confirmCancelByWarnings(change: TrainJournalId): Promise<boolean> {
  let rows: PlanProblem[] = []
  let error: ShownError | null = null
  try {
    rows = await loadProblems(() => fetchJournalProblems(change, 'cancel'))
  } catch (e) {
    error = shownError(e)
  }
  if (rows.length === 0 && !error) return true
  return !!(await openDialog<true>(ChangeWarningsDialog, {
    title: 'Отменить план?',
    okText: 'Отменить план',
    rows,
    error,
  }))
}
