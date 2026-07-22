<script setup>
import { ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { logEvent } from '../../composables/debug-log.js';
import { describeError } from '../../composables/error-message.js';
import PushButton from '../widgets/PushButton.vue';

const emit = defineEmits(['close', 'created']);

const client = useClient();

const fileInput = ref(null);
const selectedFile = ref(null);
const importing = ref(false);
const importError = ref('');

function pickFile() {
    fileInput.value?.click();
}

function onFileChosen(event) {
    selectedFile.value = event.target.files?.[0] ?? null;
    importError.value = '';
}

// A pending import's `.then()`/`.catch()` still fires after the modal closes if the user closes
// it mid-submit — guard the close paths (header X, Cancel, backdrop click) so the user can't
// dismiss the modal while that request is still in flight.
function closeModal() {
    if (importing.value) return;
    emit('close');
}

function submitImport() {
    if (!selectedFile.value) return;
    importError.value = '';
    importing.value = true;
    client.importProject(selectedFile.value)
        .then((project) => {
            logEvent('project', 'Project $1 imported', project.slug);
            emit('created', project);
            emit('close');
        })
        .catch((error) => {
            importError.value = describeError(error);
        })
        .finally(() => {
            importing.value = false;
        });
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="closeModal">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-file-import" />
                        Import Project
                    </div>
                    <button
                        class="text-orange-light hover:text-white disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:text-orange-light"
                        :class="{ 'cursor-pointer': !importing }"
                        :disabled="importing"
                        @click="closeModal"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <!-- Content -->
                <div class="px-5 py-4 flex flex-col gap-4">
                    <p class="text-sm text-grey-dark">
                        Select a project archive (<span class="font-mono">.zip</span>) previously
                        downloaded via <span class="font-semibold">Export Project</span>. The
                        project's slug (from the archive itself) must not already be in use.
                    </p>

                    <div v-if="importError" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                        {{ importError }}
                    </div>

                    <input
                        ref="fileInput"
                        type="file"
                        accept=".zip,application/zip"
                        class="hidden"
                        @change="onFileChosen"
                    />

                    <button
                        type="button"
                        class="flex items-center justify-center gap-2 border-2 border-dashed border-grey-light rounded-lg px-4 py-8 text-sm font-title text-grey-dark hover:border-orange-medium hover:text-orange-darker cursor-pointer transition-colors"
                        :disabled="importing"
                        @click="pickFile"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-file-zipper" class="text-xl" />
                        <span v-if="selectedFile">{{ selectedFile.name }}</span>
                        <span v-else>Click to choose a .zip archive...</span>
                    </button>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-between gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <button
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                        :class="{ 'cursor-pointer': !importing }"
                        :disabled="importing"
                        @click="closeModal"
                    >Cancel</button>
                    <PushButton text="Import Project" variant="green" :disabled="!selectedFile" :loading="importing" @click="submitImport" />
                </div>
            </div>
        </div>
    </Teleport>
</template>
