<script setup>
import { nextTick, ref, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { BasicReply } from '@/dlb-lib/model/BasicReply';
import { AutoForwardReply } from '@/dlb-lib/model/AutoForwardReply';
import CollapsibleErrorList from '../widgets/CollapsibleErrorList.vue';
import InteractiveReply from './InteractiveReply.vue';
import DialogueStatement from './DialogueStatement.vue';

const props = defineProps([
    'dialogueName',
    'dialogueSteps',
    'dialogueEnded',
    'dialogueCancelled',
    'awaitingReply',
    'startError',
]);

const emit = defineEmits([
    'selectReply',
    'restartDialogue',
]);

const sentinel = useTemplateRef('sentinel');
const sentinelHeight = ref(0);

// Resize the bottom spacer so the last dialogue step can sit flush against the top of the scroll
// container (and no further down). Returns the scroll container, or null if not mounted yet.
function resizeSpacerToLastStep() {
    const container = sentinel.value?.closest('.overflow-y-auto');
    const steps = container?.querySelectorAll('.dialogue-step');
    const lastStep = steps?.[steps.length - 1];

    if (container && lastStep) {
        const stepMarginBottom = parseInt(getComputedStyle(lastStep).marginBottom);
        sentinelHeight.value = Math.max(0, container.clientHeight - lastStep.offsetHeight - stepMarginBottom);
    } else {
        sentinelHeight.value = 0;
    }
    return container ?? null;
}

const scrollToBottom = () => {
    nextTick(() => {
        resizeSpacerToLastStep();
        nextTick(() => sentinel.value?.scrollIntoView({ behavior: 'smooth' }));
    });
};

// The scroll container is a single element shared by every tab, so its offset can't just live in
// the DOM across a tab switch — DialogueWorkspace reads it here on the way out and hands it back
// via restoreScroll() on the way in (see its activeTabId watcher).
function getScrollTop() {
    return sentinel.value?.closest('.overflow-y-auto')?.scrollTop;
}

// Re-fit the spacer to this tab's steps, then reapply a saved offset — or, the first time a tab
// is shown (savedTop == null), pin the latest statement to the top as usual.
function restoreScroll(savedTop) {
    nextTick(() => {
        const container = resizeSpacerToLastStep();
        nextTick(() => {
            if (savedTop != null) {
                container?.scrollTo({ top: savedTop });
            } else {
                sentinel.value?.scrollIntoView();
            }
        });
    });
}

defineExpose({ scrollToBottom, getScrollTop, restoreScroll });

// Records which reply the user picked on each already-answered step, so it stays highlighted
// while its siblings grey out. Keyed by the step object itself rather than its index: those
// references are created fresh on every (re)start of a dialogue and are distinct per tab, so a
// stale entry can never match a step of a different run — nothing leaks across a restart, a
// step reload, or a switch to another test tab, and switching back keeps earlier highlights.
const selectedReplies = ref(new Map());

function isReplySelectable(stepIndex) {
    return stepIndex === props.dialogueSteps.length - 1 && !props.awaitingReply;
}

function onReplyClick(step, stepIndex, reply) {
    if (!isReplySelectable(stepIndex)) return;
    selectedReplies.value.set(step, reply.replyId);
    emit('selectReply', step, reply);
}

function getBasicReplyNumberClasses(step, stepIndex, reply) {
    const selected = selectedReplies.value.get(step);
    if (selected === reply.replyId) {
        return 'text-interaction-reply-option';
    } else if (selected !== undefined) {
        return 'text-icon-button-disabled';
    } else if (isReplySelectable(stepIndex)) {
        return 'text-interaction-reply-option';
    } else {
        return 'text-icon-button-disabled';
    }
}

function getBasicReplyTextClasses(step, stepIndex, reply) {
    const selected = selectedReplies.value.get(step);
    if (selected === reply.replyId) {
        return 'text-interaction-reply-option';
    } else if (selected !== undefined) {
        return 'text-icon-button-disabled';
    } else if (isReplySelectable(stepIndex)) {
        return 'cursor-pointer text-interaction-reply-option hover:text-interaction-reply-option-hover';
    } else {
        return 'text-icon-button-disabled';
    }
}
</script>

<template>
    <div class="h-full">
    <div v-if="!dialogueName" class="flex items-center justify-center h-full font-title text-sm text-grey-dark p-8">
        Open a dialogue from the Dialogue Browser to start testing.
    </div>
    <div v-else-if="startError" class="flex flex-col items-center justify-center h-full gap-3 font-title text-sm text-grey-dark p-8 text-center">
        <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="text-red-dark text-2xl" />
        <span>{{ startError.message }}</span>
        <CollapsibleErrorList :errors="startError.errors" />
        <button
            type="button"
            class="rounded-xl bg-orange-dark hover:bg-orange-medium text-white uppercase p-3 min-w-[160px] cursor-pointer"
            @click="$emit('restartDialogue')"
        >Try Again</button>
    </div>
    <div v-for="(step, stepIndex) in dialogueSteps" class="dialogue-step font-title p-2 mb-8">
        <div class="flex gap-5 mb-5">
            <div class="basis-0 grow-1 font-semibold text-right">{{ step.speaker }}:</div>
            <div class="basis-0 grow-4 font-light space-y-3">
                <DialogueStatement :statement="step.statement" />
            </div>
        </div>
        <div>
            <template v-for="(reply, replyIndex) in step.replies">
                <div v-if="reply instanceof BasicReply" class="font-semibold flex gap-2">
                    <div
                        class="basis-0 grow-1 text-right"
                        :class="getBasicReplyNumberClasses(step, stepIndex, reply)"
                    >
                        {{ replyIndex + 1 }}: -
                    </div>
                    <div class="basis-0 grow-8">
                        <InteractiveReply
                            v-if="reply.statement.hasSegmentOfType('INPUT')"
                            :reply="reply"
                            :disabled="!isReplySelectable(stepIndex) || selectedReplies.get(step) !== undefined"
                            button-class="ml-2 rounded border border-orange-dark text-orange-darker hover:bg-orange-light px-3 py-1 text-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                            @submit="(vars) => { selectedReplies.set(step, reply.replyId); $emit('selectReply', step, reply, vars); }"
                        />
                        <span
                            v-else
                            :class="getBasicReplyTextClasses(step, stepIndex, reply)"
                            @click="onReplyClick(step, stepIndex, reply)"
                        >
                            <FontAwesomeIcon v-if="reply.endsDialogue" icon="fa-solid fa-xmark" class="mr-1 opacity-75" title="This reply ends the dialogue" />{{ reply.statement.fullStatement() }}
                        </span>
                    </div>
                </div>
                <div v-if="reply instanceof AutoForwardReply">
                    <button
                        class="block m-auto rounded-xl border border-grey-light text-grey-dark hover:bg-grey-lighter hover:border-orange-medium hover:text-orange-darker uppercase p-3 min-w-[160px] disabled:opacity-50"
                        :class="{
                            'cursor-pointer': isReplySelectable(stepIndex),
                        }"
                        :disabled="isReplySelectable(stepIndex) ? null : true"
                        @click="$emit('selectReply', step, reply)"
                    >
                        <FontAwesomeIcon v-if="reply.endsDialogue" icon="fa-solid fa-xmark" class="mr-2 opacity-75" title="This reply ends the dialogue" />Continue
                    </button>
                </div>
            </template>
        </div>
        <div v-if="dialogueEnded && stepIndex === dialogueSteps.length - 1" class="font-title text-sm font-bold italic text-center pt-4 flex items-center justify-center gap-2">
            {{ dialogueCancelled ? 'This dialogue has been cancelled.' : 'The dialogue has finished.' }}
            <button title="Restart dialogue." class="cursor-pointer text-interaction-reply-option hover:text-interaction-reply-option-hover" @click="$emit('restartDialogue')"><FontAwesomeIcon icon="fa-solid fa-rotate-right" /></button>
        </div>
    </div>
    <div ref="sentinel" :style="{ height: sentinelHeight + 'px' }"></div>
    </div>
</template>
