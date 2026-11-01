import type { Component } from 'vue'
import { markRaw, reactive } from 'vue'

/**
 * Открытие диалога из кода, без v-if и флага показа в шаблоне вызывающего:
 *
 *   const wagon = await openDialog(WagonNewDialog, { depot: 'D1' })
 *   if (wagon) reload()
 *
 * Диалог для openDialog — компонент на основе DialogFrame с контрактом:
 * - prop show и событие update:show — показ, как у DialogFrame;
 * - событие done(payload) — диалог завершён с результатом.
 * openDialog возвращает payload, а если диалог просто закрыли — undefined.
 *
 * Диалоги рендерит DialogHost (подключён в App.vue) внутри приложения, поэтому в них работают i18n, роутер и Pinia.
 */

export interface OpenedDialog {
  id: number
  component: Component
  props: Record<string, unknown>
  show: boolean
  finish: (payload: unknown) => void
}

let nextId = 1

/** Открытые диалоги; их рендерит DialogHost. */
export const openedDialogs = reactive<OpenedDialog[]>([])

export function openDialog<T = unknown>(
  component: Component,
  props: Record<string, unknown> = {},
): Promise<T | undefined> {
  return new Promise((resolve) => {
    const id = nextId++
    let finished = false
    openedDialogs.push({
      id,
      component: markRaw(component),
      props,
      show: true,
      finish: (payload) => {
        if (finished) return
        finished = true
        resolve(payload as T | undefined)
        const entry = openedDialogs.find((d) => d.id === id)
        if (entry) entry.show = false
        // убираем из списка после анимации закрытия
        setTimeout(() => {
          const index = openedDialogs.findIndex((d) => d.id === id)
          if (index !== -1) openedDialogs.splice(index, 1)
        }, 300)
      },
    })
  })
}
