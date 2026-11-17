<template>
  <div class="request-history" :class="{ 'request-history--large': large }">
    <template v-for="(node, i) in nodes" :key="node.key">
      <RequestHistoryItem
        v-if="node.event"
        :title="eventTitle(node.event)"
        :meta="formatDateTime(node.event.at)"
        :by="node.event.by ?? null"
        :meta-tail="metaTailOf(node.event)"
        :text="textOf(node.event)"
        :text-user="node.event.executor ?? null"
        :photos="node.event.photos ?? []"
        :pending="pendingOf(node)"
        inline
        :line="lineOf(i)"
      >
        <template v-if="node.queued.length > 0" #photos="scope">
          <slot name="pending" v-bind="scope" :node="node" />
        </template>
        <SoftLink
          v-if="noteEditable(node.event)"
          text="Исправить примечание"
          class="request-history__edit"
          @click="changes!.editNote(node.event.noteId!, node.event.text ?? '')"
        />
      </RequestHistoryItem>
      <slot v-else name="queued" :node="node" :line="lineOf(i)" />
    </template>
    <RequestHistoryItem
      v-for="(step, i) in future"
      :key="step.title"
      :title="step.title"
      :meta="step.meta"
      future
      :line="i < future.length - 1 ? 'future' : 'none'"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { formatDateTime } from '@/shared/format'
import type { HistoryNode, RequestEvent } from './historyTree'
import RequestHistoryItem from './RequestHistoryItem.vue'
import { useRequestChanges } from './requestChanges'
import { eventTitle, futureSteps, receivedLate } from './requestView'

/**
 * История заявки лентой по готовому дереву (historyTree); в конце серым — шаги, которые заявке ещё предстоят (status =
 * null — их нет). Группировки здесь нет: что с чем показывать, решает дерево. Узел только из очереди выводится слотом
 * queued; ожидающие фото записи сервера — слотом pending, их адреса — в pending. large — крупнее, для телефона.
 * Под своим примечанием автор видит ссылку «Исправить примечание», пока не вышел срок (requestChanges.ts).
 */
const props = withDefaults(
  defineProps<{ nodes: HistoryNode[]; status: number | null; large?: boolean; pending?: RequestPhoto[] }>(),
  { large: false, pending: () => [] },
)

const changes = useRequestChanges()

function noteEditable(e: RequestEvent): boolean {
  return !!changes && e.noteId !== undefined && changes.canEditNote(e.noteId)
}

const future = computed(() => (props.status === null ? [] : futureSteps(props.status)))

/** Хвост после даты и автора: без автора — «• Система»; поступило на сервер заметно позже — и когда поступило. */
function metaTailOf(e: RequestEvent): string {
  const who = e.by ? '' : ` • ${t('Система')}`
  if (!receivedLate(e.at, e.receivedAt)) return who
  return `${who}, ${t('поступило: {at}', { at: formatDateTime(e.receivedAt) })}`
}

/** Текст записи; назначение — «Исполнитель:», ФИО после него — разметкой (textUser). */
function textOf(e: RequestEvent): string {
  return e.executor ? t('Исполнитель:') : (e.text ?? '')
}

function pendingOf(node: HistoryNode): RequestPhoto[] {
  const ids = node.queued.map((x) => x.id)
  return props.pending.filter((p) => ids.includes(p.id))
}

// линия от узла: к следующему, к предстоящим шагам или никуда
function lineOf(i: number): 'none' | 'solid' | 'future' {
  if (i < props.nodes.length - 1) return 'solid'
  return future.value.length > 0 ? 'future' : 'none'
}
</script>

<style scoped lang="scss">
.request-history__edit {
  align-self: flex-start;
  margin-top: 0.3rem;
}

.request-history {
  display: flex;
  flex-direction: column;
  font-size: 0.8rem;
}

.request-history--large {
  font-size: 0.9rem;
}
</style>
