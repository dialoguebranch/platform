<script setup>
import { computed, useTemplateRef } from 'vue';
import { useResizablePanel } from '@/composables/resizablepanel.js';
import { BasicReply } from '@/dlb-lib/model/BasicReply';
import { AutoForwardReply } from '@/dlb-lib/model/AutoForwardReply';

const props = defineProps([
    'dialogueSteps',
]);

defineEmits([
    'selectReply',
]);

const root = useTemplateRef('root');

const { resize, resizableClasses } = useResizablePanel(root);

defineExpose({
    resize,
});

const currentStep = computed(() => {
    return props.dialogueSteps.length == 0 ? null :
        props.dialogueSteps[props.dialogueSteps.length - 1];
});
</script>

<template>
    <div ref="root">
        <div v-if="currentStep" ref="root" class="flex flex-col font-title">
            <div
                class="mt-10 flex flex-col"
                :class="resizableClasses({
                    default: 'mx-4',
                    sm: 'ml-10 mr-20',
                })"
            >
                <div class="bg-speech-bubble text-white text-lg rounded-2xl p-5">{{ currentStep.statement.fullStatement() }}</div>
                <div class="border-20 border-transparent border-t-speech-bubble self-end mr-[10%]"></div>
            </div>
            <div class="flex mb-10"
                :class="resizableClasses({
                    default: 'flex-col',
                    sm: 'flex-row-reverse items-start',
                })"
            >
                <img class="w-[300px]" src="@/assets/img/avatar-martin.png"
                    :class="resizableClasses({
                        default: 'self-end',
                        sm: 'self-start',
                    })"
                >
                <div
                    class="flex flex-col gap-2"
                    :class="resizableClasses({
                        default: 'mx-4 mt-4',
                        sm: 'basis-0 grow overflow-x-hidden ml-12 mr-2 mt-0 items-start',
                    })"
                >
                    <template v-for="(reply, index) in currentStep.replies">
                        <button
                            v-if="reply instanceof BasicReply"
                            class="block rounded-xl bg-orange-dark hover:bg-orange-medium text-white text-left p-3 cursor-pointer"
                            @click="$emit('selectReply', currentStep, reply)"
                        >
                            {{ reply.statement.fullStatement() }}
                        </button>
                        <button
                            v-if="reply instanceof AutoForwardReply"
                            class="block rounded-xl bg-orange-dark hover:bg-orange-medium text-white uppercase p-3 min-w-[160px] cursor-pointer"
                            @click="$emit('selectReply', currentStep, reply)"
                        >
                            Continue
                        </button>
                    </template>
                </div>
            </div>
        </div>
    </div>
</template>
