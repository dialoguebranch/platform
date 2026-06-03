<script setup>
import { ref, useTemplateRef } from 'vue';
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

const client = useClient();

const balloons = useTemplateRef('balloons');

const loadDialogue = (name) => {
    dialogueName.value = name;
    dialogueSteps.value = [];
    client.startDialogue(name, 'en')
    .then((dialogueStep) => {
        dialogueSteps.value.push(dialogueStep);
        emit('newDialogueStep');
    });
};

const reloadStep = () => {
    if (dialogueName.value) {
        client.continueDialogue(dialogueName.value)
        .then((dialogueStep) => {
            dialogueSteps.value.pop();
            dialogueSteps.value.push(dialogueStep);
            emit('newDialogueStep');
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
        }
        emit('newDialogueStep');
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
            <BalloonDialogueComponent v-if="selectedMode == 'balloon'" ref="balloons" :dialogueSteps="dialogueSteps" @selectReply="onSelectReply" />
            <TextDialogueComponent v-if="selectedMode == 'text'" :dialogueSteps="dialogueSteps" @selectReply="onSelectReply" />
        </MainPagePanelContainer>
    </div>
</template>
