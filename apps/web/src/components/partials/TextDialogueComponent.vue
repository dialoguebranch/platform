<script setup>
import { nextTick, ref, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { BasicReply } from '@/dlb-lib/model/BasicReply';
import { AutoForwardReply } from '@/dlb-lib/model/AutoForwardReply';
import { sanitizeHtml } from '@/composables/sanitize-html.js';
import CollapsibleErrorList from '../widgets/CollapsibleErrorList.vue';

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

const scrollToBottom = () => {
    nextTick(() => {
        const container = sentinel.value?.closest('.overflow-y-auto');
        const steps = container?.querySelectorAll('.dialogue-step');
        const lastStep = steps?.[steps.length - 1];

        if (container && lastStep) {
            const stepMarginBottom = parseInt(getComputedStyle(lastStep).marginBottom);
            sentinelHeight.value = Math.max(0, container.clientHeight - lastStep.offsetHeight - stepMarginBottom);
        } else {
            sentinelHeight.value = 0;
        }

        nextTick(() => sentinel.value?.scrollIntoView({ behavior: 'smooth' }));
    });
};

defineExpose({ scrollToBottom });

const selectedReplies = ref({});

function isReplySelectable(stepIndex) {
    return stepIndex === props.dialogueSteps.length - 1 && !props.awaitingReply;
}

function onReplyClick(step, stepIndex, reply) {
    if (!isReplySelectable(stepIndex)) return;
    selectedReplies.value[stepIndex] = reply.replyId;
    emit('selectReply', step, reply);
}

function getBasicReplyNumberClasses(stepIndex, reply) {
    if (selectedReplies.value[stepIndex] === reply.replyId) {
        return 'text-interaction-reply-option';
    } else if (selectedReplies.value[stepIndex] !== undefined) {
        return 'text-icon-button-disabled';
    } else if (isReplySelectable(stepIndex)) {
        return 'text-interaction-reply-option';
    } else {
        return 'text-icon-button-disabled';
    }
}

function getBasicReplyTextClasses(stepIndex, reply) {
    if (selectedReplies.value[stepIndex] === reply.replyId) {
        return 'text-interaction-reply-option';
    } else if (selectedReplies.value[stepIndex] !== undefined) {
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
            <div class="basis-0 grow-4 font-light" v-html="sanitizeHtml(step.statement.fullStatement())"></div>
        </div>
        <div>
            <template v-for="(reply, replyIndex) in step.replies">
                <div v-if="reply instanceof BasicReply" class="font-semibold flex gap-2">
                    <div
                        class="basis-0 grow-1 text-right"
                        :class="getBasicReplyNumberClasses(stepIndex, reply)"
                    >
                        {{ replyIndex + 1 }}: -
                    </div>
                    <div class="basis-0 grow-8">
                        <span
                            :class="getBasicReplyTextClasses(stepIndex, reply)"
                            @click="onReplyClick(step, stepIndex, reply)"
                        >
                            {{ reply.statement.fullStatement() }}
                        </span>
                    </div>
                </div>
                <div v-if="reply instanceof AutoForwardReply">
                    <button
                        class="block m-auto rounded-xl text-white uppercase p-3 min-w-[160px] bg-orange-dark hover:bg-orange-darker disabled:bg-icon-button-disabled"
                        :class="{
                            'cursor-pointer': isReplySelectable(stepIndex),
                        }"
                        :disabled="isReplySelectable(stepIndex) ? null : true"
                        @click="$emit('selectReply', step, reply)"
                    >
                        Continue
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
