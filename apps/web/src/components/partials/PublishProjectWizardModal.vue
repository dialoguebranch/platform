<script setup>
import { computed, ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { logEvent } from '../../composables/debug-log.js';
import PushButton from '../widgets/PushButton.vue';

const props = defineProps({
    projectSlug: { type: String, required: true },
    projectDisplayName: { type: String, required: true },
});

const emit = defineEmits(['close', 'published']);

const client = useClient();

const TOTAL_STEPS = 3;
const currentStep = ref(1);

// Step 2: verifying the draft for errors, without publishing anything.
const verifying = ref(false);
const verifyError = ref('');
const verifyResult = ref(null); // { valid, errors } — see PublishService.VerifyResult

function runVerify() {
    verifying.value = true;
    verifyError.value = '';
    verifyResult.value = null;
    client.verifyProject(props.projectSlug)
        .then((result) => {
            verifyResult.value = result;
        })
        .catch(() => {
            verifyError.value = 'Could not verify the project. Please try again.';
        })
        .finally(() => {
            verifying.value = false;
        });
}

const errorDialogueNames = computed(() => Object.keys(verifyResult.value?.errors ?? {}));

// Step 3: the version number this publish would create, and the final publish action itself.
const loadingNextVersion = ref(false);
const nextVersionError = ref('');
const nextVersionNumber = ref(null);
const publishing = ref(false);
const publishError = ref('');

function loadNextVersion() {
    loadingNextVersion.value = true;
    nextVersionError.value = '';
    client.getNextProjectVersion(props.projectSlug)
        .then((version) => {
            nextVersionNumber.value = version;
        })
        .catch(() => {
            nextVersionError.value = 'Could not determine the next version number. Please try again.';
        })
        .finally(() => {
            loadingNextVersion.value = false;
        });
}

watch(currentStep, (step) => {
    if (step === 2) runVerify();
    if (step === 3) loadNextVersion();
});

function goNext() {
    if (currentStep.value === 2 && !verifyResult.value?.valid) return;
    currentStep.value += 1;
}

function goBack() {
    currentStep.value -= 1;
}

function submitPublish() {
    publishing.value = true;
    publishError.value = '';
    client.publishProject(props.projectSlug)
        .then((result) => {
            if (result.success) {
                logEvent('project', 'Project $1 published as version $2',
                    props.projectSlug, result.version?.versionNumber);
                emit('published', result.version);
                emit('close');
            } else {
                // The draft changed between verifying and publishing (e.g. edited in another tab) —
                // go back to the verify step so the user sees exactly what's now wrong.
                verifyResult.value = {
                    valid: false,
                    errors: Object.fromEntries(
                        Object.entries(result.errors ?? {}).map(([name, errs]) =>
                            [name, errs.map((e) => e.message ?? String(e))])
                    ),
                };
                currentStep.value = 2;
            }
        })
        .catch(() => {
            publishError.value = 'Publishing failed. Please try again.';
        })
        .finally(() => {
            publishing.value = false;
        });
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="!publishing && emit('close')">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-rocket" />
                        Publish Project
                    </div>
                    <div class="flex items-center gap-3">
                        <span class="text-orange-light text-xs font-title">Step {{ currentStep }} of {{ TOTAL_STEPS }}</span>
                        <button class="text-orange-light hover:text-white cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed" :disabled="publishing" @click="emit('close')">
                            <FontAwesomeIcon icon="fa-solid fa-xmark" />
                        </button>
                    </div>
                </div>

                <!-- Content -->
                <div class="px-5 py-4 flex flex-col gap-4">

                    <!-- Step 1: Explanation -->
                    <template v-if="currentStep === 1">
                        <h3 class="font-title font-bold text-base text-orange-darker">About Publishing</h3>
                        <p class="text-sm text-grey-dark">
                            Publishing takes the current draft state of every dialogue in
                            <strong>{{ projectDisplayName }}</strong> and creates a new, immutable
                            project version from it. That version immediately becomes what runs live
                            &mdash; replacing what's currently published &mdash; and this cannot be
                            undone. Any pending changes to the project's settings &mdash; its
                            display name, description, and translation languages, from the
                            Configure Project window &mdash; are published at the same time.
                        </p>
                        <p class="text-sm text-grey-dark">
                            Next, this wizard will verify that all dialogues are free of errors, and
                            then ask for a final confirmation before publishing.
                        </p>
                    </template>

                    <!-- Step 2: Verify -->
                    <template v-else-if="currentStep === 2">
                        <h3 class="font-title font-bold text-base text-orange-darker">Verify Project</h3>
                        <div v-if="verifying" class="flex items-center gap-2 text-sm text-grey-dark">
                            <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin" />
                            Checking all dialogues for errors...
                        </div>
                        <div v-else-if="verifyError" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                            {{ verifyError }}
                        </div>
                        <template v-else-if="verifyResult">
                            <div v-if="verifyResult.valid" class="flex items-center gap-2 border border-icon-button/40 bg-icon-button/10 text-icon-button rounded-xl px-4 py-3 text-sm font-title">
                                <FontAwesomeIcon icon="fa-solid fa-circle-check" class="shrink-0" />
                                No errors found. This project is ready to publish.
                            </div>
                            <template v-else>
                                <div class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                                    <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                                    {{ errorDialogueNames.length }} dialogue{{ errorDialogueNames.length === 1 ? '' : 's' }}
                                    {{ errorDialogueNames.length === 1 ? 'has' : 'have' }} errors that must be fixed before publishing.
                                </div>
                                <div class="max-h-64 overflow-y-auto border border-grey-light rounded-lg divide-y divide-grey-light">
                                    <div v-for="name in errorDialogueNames" :key="name" class="px-3 py-2">
                                        <div class="font-title font-bold text-sm text-orange-darker">{{ name }}</div>
                                        <ul class="list-disc list-inside text-sm text-grey-dark">
                                            <li v-for="(msg, i) in verifyResult.errors[name]" :key="i">{{ msg }}</li>
                                        </ul>
                                    </div>
                                </div>
                            </template>
                        </template>
                        <button
                            type="button"
                            class="self-start flex items-center gap-1.5 text-xs font-title text-orange-darker hover:underline cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                            :disabled="verifying"
                            @click="runVerify"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-rotate-right" />
                            Re-check
                        </button>
                    </template>

                    <!-- Step 3: Final confirmation -->
                    <template v-else-if="currentStep === 3">
                        <h3 class="font-title font-bold text-base text-orange-darker">Confirm Publish</h3>
                        <div v-if="publishError" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                            {{ publishError }}
                        </div>
                        <div v-if="loadingNextVersion" class="flex items-center gap-2 text-sm text-grey-dark">
                            <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin" />
                            Determining the next version number...
                        </div>
                        <div v-else-if="nextVersionError" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                            {{ nextVersionError }}
                            <button type="button" class="ml-auto underline cursor-pointer" @click="loadNextVersion">Retry</button>
                        </div>
                        <p v-else class="text-sm text-grey-dark">
                            This will publish <strong>{{ projectDisplayName }}</strong> as
                            <strong>version {{ nextVersionNumber }}</strong>, immediately replacing
                            what's currently live. This cannot be undone.
                        </p>
                    </template>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-between gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <button
                        v-if="currentStep === 1"
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer"
                        @click="emit('close')"
                    >Cancel</button>
                    <button
                        v-else
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                        :disabled="publishing"
                        @click="goBack"
                    >Back</button>

                    <PushButton v-if="currentStep === 1" text="Next" @click="goNext" />
                    <PushButton v-else-if="currentStep === 2" text="Next" :disabled="!verifyResult?.valid" @click="goNext" />
                    <PushButton v-else text="Publish" variant="green" :loading="publishing" :disabled="loadingNextVersion || !!nextVersionError" @click="submitPublish" />
                </div>
            </div>
        </div>
    </Teleport>
</template>
