import type { Directive } from 'vue'

/**
 * v-roving-buttons — ряд кнопок с клавиатурой как у CbSegment, для готовых рядов (q-btn-toggle, свои кнопки):
 *
 *   <q-btn-toggle v-roving-buttons ... />
 *   <div v-roving-buttons><q-btn :aria-pressed="..." ... /></div>
 *
 * - Ряд — одна остановка Tab: последняя кнопка с фокусом, иначе выбранная (aria-pressed="true"), иначе первая.
 *   Сам приход фокуса ничего не выбирает.
 * - ← → (и ↑ ↓) — фокус на соседнюю доступную кнопку, по кругу, и клик по ней: значение выбирается.
 * - Пробел — клик по кнопке под фокусом (обычное поведение кнопки).
 * - Backspace, Delete — клик по выбранной кнопке: стирает значение, если ряд это разрешает (clearable).
 * - Enter у кнопки не срабатывает: в диалоге он нажимает основную кнопку.
 */

// последняя кнопка с фокусом в каждом ряду
const lastFocused = new WeakMap<HTMLElement, HTMLButtonElement>()

function buttonsOf(el: HTMLElement): HTMLButtonElement[] {
  return Array.from(el.querySelectorAll<HTMLButtonElement>('button')).filter((b) => !b.disabled)
}

function pressedOf(buttons: HTMLButtonElement[]): HTMLButtonElement | undefined {
  return buttons.find((b) => b.getAttribute('aria-pressed') === 'true')
}

function syncTabStop(el: HTMLElement) {
  const buttons = buttonsOf(el)
  const last = lastFocused.get(el)
  const stop = (last && buttons.includes(last) ? last : undefined) ?? pressedOf(buttons) ?? buttons[0]
  for (const b of buttons) b.tabIndex = b === stop ? 0 : -1
}

function onKeydown(this: HTMLElement, e: KeyboardEvent) {
  const buttons = buttonsOf(this)
  const current = (e.target as HTMLElement).closest('button')
  if (!current || !buttons.includes(current)) return

  const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key]
  if (step) {
    e.preventDefault()
    const next = buttons[(buttons.indexOf(current) + step + buttons.length) % buttons.length]
    if (!next || next === current) return
    next.focus()
    if (next.getAttribute('aria-pressed') !== 'true') next.click()
  } else if (e.key === 'Backspace' || e.key === 'Delete') {
    e.preventDefault()
    pressedOf(buttons)?.click()
  } else if (e.key === 'Enter') {
    e.preventDefault()
  }
}

function onFocusIn(this: HTMLElement, e: FocusEvent) {
  const button = (e.target as HTMLElement).closest('button')
  if (button && this.contains(button)) lastFocused.set(this, button)
  syncTabStop(this)
}

export const vRovingButtons: Directive<HTMLElement> = {
  mounted(el) {
    el.addEventListener('keydown', onKeydown)
    el.addEventListener('focusin', onFocusIn)
    syncTabStop(el)
  },
  updated(el) {
    syncTabStop(el)
  },
  beforeUnmount(el) {
    el.removeEventListener('keydown', onKeydown)
    el.removeEventListener('focusin', onFocusIn)
  },
}
