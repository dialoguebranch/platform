<script setup>
import { computed, inject, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '../../composables/client.js';
import { describeError } from '../../composables/error-message.js';
import PushButton from '../widgets/PushButton.vue';

// Imports .dlb scripts into one folder of the project as draft dialogues (#339). The server does
// all the real checking (and imports all or nothing); this modal previews the resulting names and
// which of them already exist, so existing dialogues are only replaced when the author says so.

// Matches DialogueBranchParser.DIALOGUE_NAME_REGEX in packages/core.
const DIALOGUE_NAME_REGEX = /^([A-Za-z0-9_-]+\/)*[A-Za-z0-9_-]+$/;

const props = defineProps({
    // The project's draft dialogues: [{ name, isDeleted }, ...].
    dialogues: { type: Array, default: () => [] },
});

const emit = defineEmits(['close', 'imported']);

const state = inject('state');
const client = useAuthoringClient();

const fileInput = ref(null);
const folder = ref('');
const files = ref([]);
const replace = ref(false);
const importing = ref(false);
const error = ref('');
const fileErrors = ref({});
const results = ref(null);

const folderPath = computed(() => folder.value.trim().replace(/^\/+|\/+$/g, ''));
const folderValid = computed(() => folderPath.value === '' || DIALOGUE_NAME_REGEX.test(folderPath.value));

const folderSuggestions = computed(() => {
    const folders = new Set();
    for (const { name } of props.dialogues) {
        const parts = name.split('/');
        for (let i = 1; i < parts.length; i++) folders.add(parts.slice(0, i).join('/'));
    }
    return [...folders].sort();
});

const rows = computed(() => files.value.map((file) => {
    const base = file.name.replace(/\.dlb$/i, '');
    const dialogueName = folderPath.value ? `${folderPath.value}/${base}` : base;
    const existing = props.dialogues.find((d) => d.name === dialogueName);
    return {
        file,
        dialogueName,
        validName: /\.dlb$/i.test(file.name) && DIALOGUE_NAME_REGEX.test(dialogueName),
        status: !existing ? 'new' : existing.isDeleted ? 'deleted' : 'exists',
    };
}));

const existingRows = computed(() => rows.value.filter((row) => row.status !== 'new'));
// Without "replace", files for existing dialogues are left out of the import.
const rowsToImport = computed(() => rows.value.filter((row) => row.status === 'new' || replace.value));

function pickFiles() {
    fileInput.value?.click();
}

function onFilesChosen(event) {
    files.value = [...(event.target.files ?? [])];
    error.value = '';
    fileErrors.value = {};
}

function closeModal() {
    if (importing.value) return;
    emit('close');
}

function submitImport() {
    if (rowsToImport.value.length === 0 || !folderValid.value) return;
    importing.value = true;
    error.value = '';
    fileErrors.value = {};
    client.importScripts(state.value.selectedProject?.slug, folderPath.value,
        rowsToImport.value.map((row) => row.file), { replace: replace.value })
        .then((imported) => {
            results.value = imported;
            emit('imported', imported);
        })
        .catch((err) => {
            error.value = describeError(err);
            fileErrors.value = err?.errors ?? {};
        })
        .finally(() => {
            importing.value = false;
        });
}

const statusLabel = { new: 'new', exists: 'exists', deleted: 'pending deletion' };
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col max-h-[85vh]">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-file-import" />
                        Import Scripts
                    </div>
                    <button
                        class="text-orange-light hover:text-white disabled:opacity-40"
                        :class="{ 'cursor-pointer': !importing }"
                        :disabled="importing"
                        @click="closeModal"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <!-- Done -->
                <div v-if="results" class="px-5 py-4 flex flex-col gap-3 overflow-y-auto" data-test="import-results">
                    <p class="text-sm font-title text-grey-dark">
                        Imported {{ results.length }} script(s) as draft dialogues.
                    </p>
                    <ul class="text-sm font-title flex flex-col gap-2">
                        <li v-for="result in results" :key="result.dialogueName">
                            <span class="font-mono">{{ result.dialogueName }}</span>
                            <span class="text-grey-dark"> ({{ result.replaced ? 'replaced' : 'new' }})</span>
                            <ul v-if="result.parseErrors.length" class="mt-1 ml-4 text-xs text-orange-darker list-disc">
                                <li v-for="(message, index) in result.parseErrors" :key="index">{{ message }}</li>
                            </ul>
                        </li>
                    </ul>
                    <p v-if="results.some((r) => r.parseErrors.length)" class="text-xs text-grey-dark">
                        Scripts with syntax errors were imported anyway; fix them before publishing.
                    </p>
                </div>

                <!-- Choose -->
                <div v-else class="px-5 py-4 flex flex-col gap-4 overflow-y-auto">
                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Folder</label>
                        <input
                            v-model="folder"
                            list="import-scripts-folders"
                            data-test="import-folder"
                            class="w-full font-title bg-white p-3 rounded-md border-2 border-gray-300 text-sm"
                            :class="{ 'border-red-500': !folderValid }"
                            placeholder="folder/subfolder (empty for the project root)"
                        />
                        <datalist id="import-scripts-folders">
                            <option v-for="suggestion in folderSuggestions" :key="suggestion" :value="suggestion" />
                        </datalist>
                    </div>

                    <input ref="fileInput" type="file" accept=".dlb" multiple class="hidden" data-test="import-files" @change="onFilesChosen" />
                    <button
                        type="button"
                        class="flex items-center justify-center gap-2 border-2 border-dashed border-grey-light rounded-lg px-4 py-6 text-sm font-title text-grey-dark hover:border-orange-medium hover:text-orange-darker cursor-pointer"
                        :disabled="importing"
                        @click="pickFiles"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-file-lines" class="text-xl" />
                        <span v-if="files.length">{{ files.length }} file(s) chosen, click to choose again</span>
                        <span v-else>Click to choose one or more .dlb scripts...</span>
                    </button>

                    <ul v-if="rows.length" class="text-sm font-title flex flex-col gap-1" data-test="import-rows">
                        <li v-for="row in rows" :key="row.file.name" :data-status="row.status">
                            <span class="font-mono">{{ row.dialogueName }}</span>
                            <span class="ml-2 text-xs" :class="row.status === 'new' ? 'text-green-700' : 'text-orange-darker'">{{ statusLabel[row.status] }}</span>
                            <span v-if="!row.validName" class="ml-2 text-xs text-red-600">not a valid dialogue name</span>
                            <ul v-if="fileErrors[row.file.name]" class="ml-4 text-xs text-red-600 list-disc">
                                <li v-for="(message, index) in fileErrors[row.file.name]" :key="index">{{ message }}</li>
                            </ul>
                        </li>
                    </ul>

                    <div v-if="existingRows.length" class="border border-orange-medium/50 bg-orange-light/20 rounded-lg px-4 py-3 text-sm font-title" data-test="import-replace-warning">
                        <p>
                            {{ existingRows.length }} of these dialogues already exist{{ existingRows.some((r) => r.status === 'deleted') ? ' (some pending deletion, which replacing restores)' : '' }}.
                            Replacing them overwrites their draft content; what is published (if anything) stays until you publish again.
                        </p>
                        <label class="mt-2 flex items-center gap-2 cursor-pointer">
                            <input v-model="replace" type="checkbox" data-test="import-replace" />
                            Replace them (otherwise they are left out)
                        </label>
                    </div>

                    <div v-if="error" class="flex items-start gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title" data-test="import-error">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                        {{ error }}
                    </div>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-between gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <template v-if="results">
                        <span></span>
                        <PushButton text="Close" variant="green" @click="emit('close')" />
                    </template>
                    <template v-else>
                        <button
                            class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light disabled:opacity-40"
                            :class="{ 'cursor-pointer': !importing }"
                            :disabled="importing"
                            @click="closeModal"
                        >Cancel</button>
                        <PushButton
                            :text="`Import ${rowsToImport.length || ''} script(s)`"
                            variant="green"
                            data-test="import-submit"
                            :disabled="rowsToImport.length === 0 || !folderValid"
                            :loading="importing"
                            @click="submitImport"
                        />
                    </template>
                </div>
            </div>
        </div>
    </Teleport>
</template>
