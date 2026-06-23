<script setup>
import { nextTick, ref, useTemplateRef } from 'vue';
import { BasicReply } from '@/dlb-lib/model/BasicReply';
import { AutoForwardReply } from '@/dlb-lib/model/AutoForwardReply';

const props = defineProps([
    'dialogueSteps',
]);

const emit = defineEmits([
    'selectReply',
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

function onReplyClick(step, stepIndex, reply) {
    if (stepIndex !== props.dialogueSteps.length - 1) return;
    selectedReplies.value[stepIndex] = reply.replyId;
    emit('selectReply', step, reply);
}

function getBasicReplyNumberClasses(stepIndex, reply) {
    if (stepIndex === props.dialogueSteps.length - 1) {
        return 'text-interaction-reply-option';
    } else if (selectedReplies.value[stepIndex] === reply.replyId) {
        return 'text-interaction-reply-option';
    } else {
        return 'text-icon-button-disabled';
    }
}

function getBasicReplyTextClasses(stepIndex, reply) {
    if (stepIndex === props.dialogueSteps.length - 1) {
        return 'cursor-pointer text-interaction-reply-option hover:text-interaction-reply-option-hover';
    } else if (selectedReplies.value[stepIndex] === reply.replyId) {
        return 'text-interaction-reply-option';
    } else {
        return 'text-icon-button-disabled';
    }
}
</script>

<template>
    <div>
    <div v-for="(step, stepIndex) in dialogueSteps" class="dialogue-step font-title p-2 mb-8">
        <div class="flex gap-5 mb-5">
            <div class="basis-0 grow-1 font-semibold text-right">{{ step.speaker }}:</div>
            <div class="basis-0 grow-4 font-light">{{ step.statement.fullStatement() }}</div>
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
                            'cursor-pointer': stepIndex === dialogueSteps.length - 1,
                        }"
                        :disabled="stepIndex === dialogueSteps.length - 1 ? null : true"
                        @click="stepIndex === dialogueSteps.length - 1 ? $emit('selectReply', step, reply) : null"
                    >
                        Continue
                    </button>
                </div>
            </template>
        </div>
    </div>
    <div ref="sentinel" :style="{ height: sentinelHeight + 'px' }"></div>
    </div>
</template>
