<script setup>
import { inject, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue';
import { useClient } from '../../composables/client.js';
import { useStateManagement } from '../../composables/state-management.js';
import { showError, dismissError } from '../../composables/error-toast.js';
import { describeError } from '../../composables/error-message.js';
import { logEvent } from '../../composables/debug-log.js';
import BalloonDialogueComponent from '../partials/BalloonDialogueComponent.vue';

const state = inject('state');
const config = inject('config');
const client = useClient();
const stateManagement = useStateManagement();

const projectSlug = config.participant.projectSlug;
const dialogueName = config.participant.dialogueName;

const balloons = useTemplateRef('balloons');

const dialogueSteps = ref([]);
const dialogueEnded = ref(false);
const dialogueCancelled = ref(false);
const awaitingReply = ref(false);
const startError = ref(null);

function startDialogue() {
    dialogueSteps.value = [];
    dialogueEnded.value = false;
    dialogueCancelled.value = false;
    awaitingReply.value = false;
    startError.value = null;
    dismissError();
    logEvent('dialogue', 'Dialogue started: $1', dialogueName);
    // Empty language: a participant has no language selector, so let the server fall back to
    // the project's source language (DialogueBranchClient concatenates this straight into the
    // query string — passing null here would literally send "language=null").
    client.startDialogue(projectSlug, dialogueName, '')
    .then((dialogueStep) => {
        dialogueSteps.value.push(dialogueStep);
        dialogueEnded.value = dialogueStep.replies.length === 0;
        if (dialogueEnded.value) logEvent('dialogue', 'Dialogue ended immediately: $1', dialogueName);
    })
    .catch((error) => {
        // Shown inline (not as a toast) since this is the participant's whole screen — there's
        // nothing else to look at while it's broken, unlike the editor/admin app's dialogue tabs.
        startError.value = {
            message: error.message ?? `Could not start dialogue "${dialogueName}" in project "${projectSlug}".`,
            errors: error.errors,
        };
    });
}

function onSelectReply(dialogueStep, reply) {
    if (awaitingReply.value) return;
    const replyText = reply.statement?.segments?.map(s => s.text).join('') ?? String(reply.replyId);
    dismissError();
    awaitingReply.value = true;
    logEvent('dialogue', 'Reply selected: $1', replyText);
    client.progressDialogue(dialogueStep.loggedDialogueId, dialogueStep.loggedInteractionIndex,
        reply.replyId)
    .then((nextStep) => {
        if (nextStep) {
            dialogueSteps.value.push(nextStep);
            dialogueEnded.value = nextStep.replies.length === 0;
            if (dialogueEnded.value) logEvent('dialogue', 'Dialogue ended: $1', dialogueName);
        } else {
            dialogueEnded.value = true;
            logEvent('dialogue', 'Dialogue ended: $1', dialogueName);
        }
    })
    .catch((error) => {
        showError(describeError(error));
    })
    .finally(() => {
        awaitingReply.value = false;
    });
}

function onLogoutClick() {
    stateManagement.logout();
}

const onWindowResize = () => balloons.value?.resize();

onMounted(() => {
    startDialogue();
    window.addEventListener('resize', onWindowResize);
});

onBeforeUnmount(() => {
    window.removeEventListener('resize', onWindowResize);
});
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col">
        <div class="w-full flex items-center justify-between px-4 pt-3 shrink-0">
            <span class="font-title text-xs text-grey-dark">
                Logged in as <span class="font-semibold text-orange-darker">{{ state.user.name }}</span>
            </span>
            <button
                type="button"
                class="font-title text-xs text-grey-dark hover:text-orange-darker cursor-pointer"
                @click="onLogoutClick"
            >Log out</button>
        </div>

        <img class="block pt-4 mx-auto w-[200px] sm:w-[320px] shrink-0" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />

        <div class="grow min-h-0 mt-4 max-w-3xl w-full mx-auto">
            <BalloonDialogueComponent
                ref="balloons"
                :dialogueName="dialogueName"
                :dialogueSteps="dialogueSteps"
                :dialogueEnded="dialogueEnded"
                :dialogueCancelled="dialogueCancelled"
                :awaitingReply="awaitingReply"
                :startError="startError"
                @selectReply="onSelectReply"
                @restartDialogue="startDialogue"
            />
        </div>
    </div>
</template>
