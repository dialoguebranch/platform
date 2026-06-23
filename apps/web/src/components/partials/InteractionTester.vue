<script setup>
import { nextTick, ref, useTemplateRef, watch } from 'vue';
import { useClient } from '@/composables/client.js';
import IconButton from '../widgets/IconButton.vue';
import BalloonDialogueComponent from './BalloonDialogueComponent.vue';
import TextDialogueComponent from './TextDialogueComponent.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';
import ModeSelector from '../widgets/ModeSelector.vue';

const emit = defineEmits([
    'newDialogueStep',
]);

const dialogueName = ref(null);
const dialogueSteps = ref([]);
const dialogueEnded = ref(false);

const modes = [
    {
        name: 'balloon',
        icon: 'fa-regular fa-comments',
    },
    {
        name: 'text',
        icon: 'fa-solid fa-paragraph',
    },
];

const selectedMode = ref('balloon');

watch(selectedMode, (mode) => {
    if (mode === 'text') {
        nextTick(() => scrollTextToBottom());
    }
});

const client = useClient();

const balloons = useTemplateRef('balloons');
const textComponent = useTemplateRef('text-component');

const scrollTextToBottom = () => {
    if (textComponent.value) textComponent.value.scrollToBottom();
};

const loadDialogue = (name) => {
    dialogueName.value = name;
    dialogueSteps.value = [];
    dialogueEnded.value = false;
    client.startDialogue(name, 'en')
    .then((dialogueStep) => {
        dialogueSteps.value.push(dialogueStep);
        emit('newDialogueStep');
        scrollTextToBottom();
    });
};

const reloadStep = () => {
    if (dialogueName.value) {
        client.continueDialogue(dialogueName.value)
        .then((dialogueStep) => {
            dialogueSteps.value.pop();
            dialogueSteps.value.push(dialogueStep);
            emit('newDialogueStep');
            scrollTextToBottom();
        });
    }
};

const resize = () => {
    if (balloons.value) {
        balloons.value.resize();
    }
};

defineExpose({
    loadDialogue,
    reloadStep,
    resize,
});

function onSelectReply(dialogueStep, reply) {
    client.progressDialogue(dialogueStep.loggedDialogueId, dialogueStep.loggedInteractionIndex,
        reply.replyId)
    .then((dialogueStep) => {
        if (dialogueStep) {
            dialogueSteps.value.push(dialogueStep);
            dialogueEnded.value = dialogueStep.replies.length === 0;
        } else {
            dialogueEnded.value = true;
        }
        emit('newDialogueStep');
        scrollTextToBottom();
    });
}
</script>

<template>
    <div class="flex flex-col gap-1">
        <MainPagePanelHeader
            title="Interaction Tester"
            :subtitle="dialogueName ? dialogueName + '.dlb' : null"
        >
            <template #buttons>
                <ModeSelector :modes="modes" v-model="selectedMode" />
                <IconButton icon="fa-solid fa-circle-xmark" color="warning" :disabled="dialogueName === null" />
            </template>
        </MainPagePanelHeader>
        <MainPagePanelContainer>
            <BalloonDialogueComponent v-if="selectedMode == 'balloon'" ref="balloons" :dialogueSteps="dialogueSteps" :dialogueEnded="dialogueEnded" @selectReply="onSelectReply" />
            <TextDialogueComponent v-if="selectedMode == 'text'" ref="text-component" :dialogueSteps="dialogueSteps" :dialogueEnded="dialogueEnded" @selectReply="onSelectReply" />
        </MainPagePanelContainer>
    </div>
</template>
