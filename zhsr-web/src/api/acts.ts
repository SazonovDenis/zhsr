import { http } from './http'

/** API отмены актов (Ф-АКТ-06). */

/** Отменить акт приёма-передачи с причиной. */
export async function cancelTransfer(id: number, reason: string): Promise<void> {
  await http.post(`/transfers/${id}/cancel`, { reason })
}
