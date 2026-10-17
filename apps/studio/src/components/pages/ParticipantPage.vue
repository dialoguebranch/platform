<script setup>
import { inject, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient, getBrowserTimeZone } from '../../composables/client.js';
import { useStateManagement } from '../../composables/state-management.js';
import { showError, dismissError } from '../../composables/error-toast.js';
import { describeError } from '../../composables/error-message.js';
import { logEvent } from '../../composables/debug-log.js';
import BalloonDialogueComponent from '../partials/BalloonDialogueComponent.vue';
import StudioWordmark from '../widgets/StudioWordmark.vue';
import OrnamentDivider from '../widgets/OrnamentDivider.vue';

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
    // No language: a participant has no language selector, so let the server fall back to the
    // project's source language.
    client.startDialogue({ dialogueName, projectSlug, timeZone: getBrowserTimeZone() })
    .then((dialogueStep) => {
        dialogueSteps.value.push(dialogueStep);
        announceStatementActions(dialogueStep);
        dialogueEnded.value = dialogueStep.replies.length === 0;
        if (dialogueEnded.value) logEvent('dialogue', 'Dialogue ended immediately: $1', dialogueName);
    })
    .catch((error) => {
        // Shown inline (not as a toast) since this is the participant's whole screen — there's
        // nothing else to look at while it's broken, unlike the editor/admin app's dialogue tabs.
        // The dialogue a participant starts is fixed when Studio is built (see config.js), so a 404
        // means it isn't set up here: trying again can't help.
        startError.value = error?.status === 404
            ? {
                message: 'There is no test dialogue set up to run for your user role (participant). Please contact an administrator.',
                errors: null,
                canRetry: false,
            }
            : {
                message: error.message ?? 'Could not start the dialogue.',
                errors: error.errors,
            };
    });
}

// Surfaces resolved <<action>> commands: a Debug Console line, and a `dlb-action` CustomEvent on
// `window` so a page embedding the participant view can react to a `generic` action.
function announceAction(action, origin) {
    const params = action.parameters && Object.keys(action.parameters).length
        ? ' {' + Object.entries(action.parameters).map(([k, v]) => `${k}=${v}`).join(', ') + '}'
        : '';
    logEvent('dialogue', `${origin} action: $1`, `${action.type} → ${action.value}${params}`);
    window.dispatchEvent(new CustomEvent('dlb-action', {
        detail: { origin, type: action.type, value: action.value, parameters: action.parameters },
    }));
}

function announceStatementActions(step) {
    (step?.statement?.segments ?? [])
        .filter((segment) => segment.type === 'ACTION' && segment.action)
        .forEach((segment) => announceAction(segment.action, 'Statement'));
}

function onSelectReply(dialogueStep, reply, inputValues) {
    if (awaitingReply.value) return;
    (reply.actions ?? []).forEach((action) => announceAction(action, 'Reply'));
    const replyText = reply.statement?.segments?.map(s => s.text).join('') ?? String(reply.replyId);
    const values = inputValues && Object.keys(inputValues).length > 0 ? inputValues : null;
    dismissError();
    awaitingReply.value = true;
    logEvent('dialogue', 'Reply selected: $1', replyText);
    client.progressDialogue(dialogueStep.loggedDialogueId, dialogueStep.loggedInteractionIndex,
        reply.replyId, values)
    .then((nextStep) => {
        if (nextStep) {
            dialogueSteps.value.push(nextStep);
            announceStatementActions(nextStep);
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
        <img class="block pt-6 mx-auto w-[200px] sm:w-[320px] shrink-0" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <StudioWordmark class="mt-2 shrink-0" />

        <!-- One column: a line separating the logo from the page, who is logged in, and the
             dialogue itself, framed in a white box that is as tall as its content (the page
             scrolls if it doesn't fit) -->
        <div class="flex justify-center px-4 mt-4 mb-6">
            <div class="w-full max-w-3xl">
                <OrnamentDivider class="mt-3" />
                <div class="flex items-center justify-between gap-3 mt-6 mb-2">
                    <span class="flex items-start gap-1.5 min-w-0 font-title text-xs text-grey-dark" data-test="greeting">
                        <FontAwesomeIcon icon="fa-regular fa-comment" class="shrink-0 mt-0.5 text-orange-darker" />
                        <span>Hey <span class="font-semibold text-orange-darker">{{ state.user.name }}</span>, welcome to the participant test view.</span>
                    </span>
                    <button
                        type="button"
                        class="shrink-0 flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-orange-darker/40 bg-white/60 font-title text-xs text-orange-darker hover:bg-white cursor-pointer"
                        data-test="log-out"
                        @click="onLogoutClick"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-right-from-bracket" />
                        Log out
                    </button>
                </div>
                <div class="bg-white rounded-2xl shadow-md" data-test="dialogue-frame">
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
        </div>
    </div>
</template>
