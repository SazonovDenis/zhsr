import type { Ref } from 'vue'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import type { RowKey } from '@/shared/components/table/types'

// выбранная строка списка по адресу страницы — в памяти, пока открыто приложение (не в хранилище браузера)
const kept = new Map<string, RowKey | null>()

/**
 * Выбранная строка списка, которая остаётся при возврате на страницу из экрана функции (кнопкой «назад», Esc, назад
 * браузера), пока приложение открыто. Переход пунктом левого меню выбор забывает (forgetKeptSelection). Ключ — адрес
 * страницы без параметров (у списка шаблона — с его id).
 *
 * @param initial выбор, заданный переходом (например, ?id= в адресе), — сильнее запомненного; null — запомненный
 */
export function useKeptSelection(initial: RowKey | null = null): Ref<RowKey | null> {
  const key = useRoute().path
  const selected = ref<RowKey | null>(initial ?? kept.get(key) ?? null)
  watch(selected, (id) => kept.set(key, id), { immediate: true })
  return selected
}

/** Забыть выбранные строки всех списков: переход пунктом левого меню открывает раздел без выбора. */
export function forgetKeptSelection() {
  kept.clear()
}
