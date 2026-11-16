import type { CheckListValue } from '@/api/checks'

/**
 * Цвет названия пункта в просмотре чек-листа или описи: нарушение — 'bad' (красный), ответ дан, норматив есть и
 * нарушения нет — 'ok' (зелёный); без ответа или без норматива — '' (обычный цвет).
 */
export function valueTone(violation: boolean, norm: number | null, answered = true): 'bad' | 'ok' | '' {
  if (violation) return 'bad'
  return answered && norm !== null ? 'ok' : ''
}

/** Пункт чек-листа отвечен: выбран вариант, введено число или текст. */
export function answered(v: CheckListValue): boolean {
  return v.checkDictValue !== null || v.valueNumber !== null || !!v.valueString
}
