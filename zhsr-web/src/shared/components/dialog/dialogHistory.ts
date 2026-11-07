import { nextTick } from 'vue'

/**
 * Кнопка «назад» (на телефоне — системная) закрывает показанный диалог, а не уводит со страницы.
 *
 * Пока диалог показан, в истории браузера лежит его запись: «назад» снимает её, а мы на это закрываем диалог. Диалог
 * закрыли обычным способом — запись снимаем сами (history.back).
 *
 * Диалоги ведутся общим стеком, и слушатель popstate один на всех: запись из истории снимается одна, значит и
 * закрыться должен ровно один диалог — верхний.
 *
 * Запись кладётся с копией состояния vue-router (history.state) и с тем же адресом: когда запись снимается, роутер
 * видит прежнее состояние и прежний адрес и никуда не переходит.
 *
 * Нужна ли запись (например, только на телефоне), решает сам диалог: сюда он приходит уже с ответом.
 */

/** Диалог для истории: показан ли он сейчас и как его закрыть. */
export interface HistoryDialog {
  isShown: () => boolean
  close: () => void
}

// показанные диалоги в порядке показа; запись в стеке есть тогда и только тогда, когда наша запись лежит в истории
const stack: HistoryDialog[] = []

// сколько ближайших popstate — наши собственные (от history.back при обычном закрытии), а не нажатия «назад»
let ignoreCount = 0

let listening = false

function remove(dialog: HistoryDialog) {
  const index = stack.indexOf(dialog)
  if (index !== -1) stack.splice(index, 1)
  stopListening()
}

/**
 * Слушатель снимается, только когда слушать нечего: диалогов нет и свой history.back уже пришёл. Снять его раньше —
 * свой popstate придёт без слушателя, ignoreCount останется больше нуля, и первое настоящее «назад» в следующем
 * диалоге было бы принято за своё и ничего не закрыло бы.
 */
function stopListening() {
  if (stack.length === 0 && ignoreCount === 0 && listening) {
    window.removeEventListener('popstate', onPopStateListener)
    listening = false
  }
}

const onPopStateListener = () => void onPopState()

async function onPopState() {
  // это наш же history.back при обычном закрытии, а не нажатие «назад»
  if (ignoreCount > 0) {
    ignoreCount--
    stopListening()
    return
  }

  const dialog = stack[stack.length - 1]
  if (!dialog) return

  // запись из истории браузер снял сам — остаётся закрыть диалог
  remove(dialog)
  dialog.close()

  // владелец мог диалог не закрыть (спрашивает подтверждение) — возвращаем запись, иначе следующее «назад» ушло бы
  // со страницы
  await nextTick()
  if (dialog.isShown()) push(dialog)
}

/** Диалог показан — кладём его запись в историю. */
function push(dialog: HistoryDialog) {
  // запись уже лежит — второй раз не кладём: снять её потом было бы нечем
  if (stack.includes(dialog)) return

  // в стек берём только после того, как запись легла: упади pushState (Safari ограничивает частоту вызовов), диалог
  // останется без обработки «назад», но состояние с историей не разойдётся
  history.pushState({ ...(history.state as object), dialog: true }, '')
  stack.push(dialog)

  if (!listening) {
    window.addEventListener('popstate', onPopStateListener)
    listening = true
  }
}

/** Диалог закрыли обычным способом — свою запись из истории снимаем сами. */
function pop(dialog: HistoryDialog) {
  if (!stack.includes(dialog)) return
  remove(dialog)
  ignoreCount++
  history.back()
}

/**
 * Компонент диалога уничтожен. Историю не трогаем: шаг назад отменил бы тот переход, которым компонент убрали. Из
 * стека уходим обязательно: мёртвый диалог остался бы верхним и глушил бы «назад» у живых.
 */
function forget(dialog: HistoryDialog) {
  remove(dialog)
}

export const dialogHistory = { push, pop, forget }
