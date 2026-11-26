<template>
  <div v-if="groups.length" :class="strip ? null : 'wagon-problems q-pa-md'">
    <div class="wagon-problems__title">{{ t(title) }}</div>
    <div v-for="g in groups" :key="g.wagon" class="q-mt-xs">
      <!-- одна проблема вагона — одной фразой с номером; несколько — под номером вагона -->
      <div v-if="g.sentences.length === 1">
        <PhraseView :phrase="{ key: 'Вагон {n} {text}', params: { n: { bold: g.number }, text: g.sentences[0]! } }" />
      </div>
      <template v-else>
        <div><PhraseView :phrase="{ key: 'Вагон {n}:', params: { n: { bold: g.number } } }" /></div>
        <div v-for="(sentence, i) in g.sentences" :key="i" class="q-pl-md"><PhraseView :phrase="sentence" /></div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { PlanProblem } from '@/api/trains'
import { t } from '@/boot/i18n'
import type { Phrase } from './phrase'
import PhraseView from './PhraseView.vue'
import { planProblemPhrase } from './wagonProblemText'

/**
 * Предупреждения перед сохранением: под заголовком по каждому вагону — где окажется вагон, когда, где и для чего вагон
 * нужен; проблемы одного вагона собраны вместе, по времени. Только сведения: сохранить они не мешают. Пусто — ничего.
 * Геометрия — как у ошибки: на странице и в листе телефона — блоком как ErrorBox; в диалоге (strip) — только строки:
 * полосу над кнопками даёт слот warnings диалога. Справочники Station, Depot, Yard и LocationKind готовит владелец
 * (useWagonProblems).
 *
 * @prop title заголовок на русском; у действий без сохранения формы — свой
 */
const props = withDefaults(defineProps<{ rows: PlanProblem[]; strip?: boolean; title?: string }>(), {
  strip: false,
  title: 'После сохранения будут проблемы:',
})

/** Фразы по вагонам: вагоны — в порядке первой проблемы, фразы вагона — в порядке строк сервера (по времени). */
const groups = computed(() => {
  const res = new Map<number, { wagon: number; number: string; sentences: Phrase[] }>()
  for (const row of props.rows) {
    for (const p of row.problems) {
      const group = res.get(p.wagon.id) ?? { wagon: p.wagon.id, number: p.wagon.number, sentences: [] }
      group.sentences.push(planProblemPhrase(row, p))
      res.set(p.wagon.id, group)
    }
  }
  return [...res.values()]
})
</script>

<style scoped lang="scss">
.wagon-problems {
  flex: none;
  max-height: 40vh;
  overflow-y: auto;
  border-radius: 0.3rem;
  background: $zs-warning-bg;
  font-size: 0.9rem;
}

.wagon-problems__title {
  font-weight: 600;
}
</style>
