<script setup>
import { computed, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useResizablePanel } from '@/composables/resizablepanel.js';
import { sanitizeHtml } from '@/composables/sanitize-html.js';
import { BasicReply } from '@/dlb-lib/model/BasicReply';
import { AutoForwardReply } from '@/dlb-lib/model/AutoForwardReply';
import CollapsibleErrorList from '../widgets/CollapsibleErrorList.vue';
import avatarMartin from '@/assets/img/avatar-martin.png';
import avatarMartinTools from '@/assets/img/avatar-martin-tools.png';

const props = defineProps([
    'dialogueName',
    'dialogueSteps',
    'dialogueEnded',
    'dialogueCancelled',
    'awaitingReply',
    'startError',
    'isDraftTest',
]);

const avatarSrc = computed(() => props.isDraftTest ? avatarMartinTools : avatarMartin);

defineEmits([
    'selectReply',
    'restartDialogue',
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
    <div ref="root" class="h-full">
        <div v-if="!dialogueName" class="flex items-center justify-center h-full font-title text-sm text-grey-dark">
            Open a dialogue from the Dialogue Browser to start testing.
        </div>
        <div v-else-if="startError" class="flex flex-col items-center justify-center h-full gap-3 font-title text-sm text-grey-dark px-8 text-center">
            <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="text-red-dark text-2xl" />
            <span>{{ startError.message }}</span>
            <CollapsibleErrorList :errors="startError.errors" />
            <button
                type="button"
                class="rounded-xl bg-orange-dark hover:bg-orange-medium text-white uppercase p-3 min-w-[160px] cursor-pointer"
                @click="$emit('restartDialogue')"
            >Try Again</button>
        </div>
        <div v-else-if="currentStep" class="flex flex-col font-title">
            <div
                class="mt-10 flex flex-col relative"
                :class="resizableClasses({
                    default: 'mx-4',
                    sm: 'ml-10 mr-20',
                })"
            >
                <div class="bg-speech-bubble text-white text-lg rounded-2xl p-5" v-html="sanitizeHtml(currentStep.statement.fullStatement())"></div>
                <div class="border-20 border-transparent border-t-speech-bubble self-end mr-[10%]"></div>
                <div v-if="dialogueEnded" class="absolute top-full font-title text-sm font-bold italic text-center pt-2 w-full flex items-center justify-center gap-2">
                    {{ dialogueCancelled ? 'This dialogue has been cancelled.' : 'The dialogue has finished.' }}
                    <button title="Restart dialogue." class="cursor-pointer text-interaction-reply-option hover:text-interaction-reply-option-hover" @click="$emit('restartDialogue')"><FontAwesomeIcon icon="fa-solid fa-rotate-right" /></button>
                </div>
            </div>
            <div class="flex mb-10"
                :class="resizableClasses({
                    default: 'flex-col',
                    sm: 'flex-row-reverse items-start',
                })"
            >
                <img class="w-[300px]" :src="avatarSrc"
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
                    <template v-if="!dialogueEnded" v-for="(reply, index) in currentStep.replies">
                        <button
                            v-if="reply instanceof BasicReply"
                            class="block rounded-xl bg-orange-dark hover:bg-orange-medium text-white text-left p-3 disabled:bg-icon-button-disabled disabled:cursor-not-allowed"
                            :class="{ 'cursor-pointer': !awaitingReply }"
                            :disabled="awaitingReply"
                            @click="$emit('selectReply', currentStep, reply)"
                        >
                            <FontAwesomeIcon v-if="reply.endsDialogue" icon="fa-solid fa-xmark" class="mr-2 opacity-75" title="This reply ends the dialogue" />{{ reply.statement.fullStatement() }}
                        </button>
                        <button
                            v-if="reply instanceof AutoForwardReply"
                            class="block rounded-xl border border-grey-light text-grey-dark hover:bg-grey-lighter hover:border-orange-medium hover:text-orange-darker uppercase p-3 min-w-[160px] disabled:opacity-50 disabled:cursor-not-allowed"
                            :class="{ 'cursor-pointer': !awaitingReply }"
                            :disabled="awaitingReply"
                            @click="$emit('selectReply', currentStep, reply)"
                        >
                            <FontAwesomeIcon v-if="reply.endsDialogue" icon="fa-solid fa-xmark" class="mr-2 opacity-75" title="This reply ends the dialogue" />Continue
                        </button>
                    </template>
                </div>
            </div>
        </div>
    </div>
</template>
