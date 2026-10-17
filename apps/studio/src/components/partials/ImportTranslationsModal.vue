<script setup>
import { computed, inject, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '../../composables/client.js';
import { describeError } from '../../composables/error-message.js';
import PushButton from '../widgets/PushButton.vue';

// Imports translation files (.json) into one translation language of the project, as draft
// translations of the dialogues in one folder (#340). The server does all the real checking (and
// imports all or nothing); this modal previews which dialogue each file belongs to and which of
// them already have a translation, so existing translations are only replaced when the author
// says so.

// Matches DialogueBranchParser.DIALOGUE_NAME_REGEX in packages/core.
const DIALOGUE_NAME_REGEX = /^([A-Za-z0-9_-]+\/)*[A-Za-z0-9_-]+$/;

const props = defineProps({
    // The project's draft dialogues: [{ name, isDeleted, translatedInto }, ...].
    dialogues: { type: Array, default: () => [] },
    // The project's translation languages (not pending deletion): [{ code, name }, ...].
    languages: { type: Array, default: () => [] },
});

const emit = defineEmits(['close', 'imported']);

const state = inject('state');
const client = useAuthoringClient();

const fileInput = ref(null);
const language = ref(props.languages[0]?.code ?? '');
const folder = ref('');
const files = ref([]);
const replace = ref(false);
const importing = ref(false);
const error = ref('');
const fileErrors = ref({});
const results = ref(null);

const languageName = computed(() => props.languages.find((l) => l.code === language.value)?.name ?? language.value);

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

// status: 'new' (the dialogue has no translation in this language yet), 'exists' (it has one),
// 'missing' (no such dialogue) or 'deleted' (the dialogue is pending deletion).
const rows = computed(() => files.value.map((file) => {
    const base = file.name.replace(/\.json$/i, '');
    const dialogueName = folderPath.value ? `${folderPath.value}/${base}` : base;
    const dialogue = props.dialogues.find((d) => d.name === dialogueName);
    let status = 'new';
    if (!dialogue) status = 'missing';
    else if (dialogue.isDeleted) status = 'deleted';
    else if ((dialogue.translatedInto ?? []).includes(language.value)) status = 'exists';
    return {
        file,
        dialogueName,
        validName: /\.json$/i.test(file.name) && DIALOGUE_NAME_REGEX.test(dialogueName),
        status,
    };
}));

const blockedRows = computed(() => rows.value.filter((row) => row.status === 'missing' || row.status === 'deleted' || !row.validName));
const existingRows = computed(() => rows.value.filter((row) => row.status === 'exists'));
// Without "replace", files for dialogues that already have a translation are left out.
const rowsToImport = computed(() => rows.value.filter((row) => row.status === 'new' || (row.status === 'exists' && replace.value)));
const canImport = computed(() => !!language.value && folderValid.value
    && rowsToImport.value.length > 0 && blockedRows.value.length === 0);

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
    if (!canImport.value) return;
    importing.value = true;
    error.value = '';
    fileErrors.value = {};
    client.importTranslations(state.value.selectedProject?.slug, language.value, folderPath.value,
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

function coverage(result) {
    if (result.unmatchedEntries === null || result.untranslatedTerms === null) {
        return ['coverage unknown: the dialogue has errors'];
    }
    const notes = [];
    if (result.unmatchedEntries > 0) notes.push(`${result.unmatchedEntries} entr${result.unmatchedEntries === 1 ? 'y doesn\'t' : 'ies don\'t'} match a current term`);
    if (result.untranslatedTerms > 0) notes.push(`${result.untranslatedTerms} term${result.untranslatedTerms === 1 ? '' : 's'} untranslated`);
    if (notes.length === 0) notes.push('covers every term');
    return notes;
}

const statusLabel = {
    new: 'new translation',
    exists: 'has a translation',
    missing: 'no such dialogue',
    deleted: 'dialogue pending deletion',
};
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col max-h-[85vh]">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-language" />
                        Import Translations
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

                <!-- No languages -->
                <div v-if="languages.length === 0" class="px-5 py-4 text-sm font-title text-grey-dark" data-test="import-no-languages">
                    This project has no translation languages yet. Add one in Configure Project first.
                </div>

                <!-- Done -->
                <div v-else-if="results" class="px-5 py-4 flex flex-col gap-3 overflow-y-auto" data-test="import-results">
                    <p class="text-sm font-title text-grey-dark">
                        Imported {{ results.length }} translation(s) into {{ languageName }}.
                    </p>
                    <ul class="text-sm font-title flex flex-col gap-2">
                        <li v-for="result in results" :key="result.dialogueName">
                            <span class="font-mono">{{ result.dialogueName }}</span>
                            <span class="text-grey-dark"> ({{ result.replaced ? 'replaced' : 'new' }})</span>
                            <ul class="mt-1 ml-4 text-xs text-grey-dark list-disc">
                                <li v-for="(note, index) in coverage(result)" :key="index">{{ note }}</li>
                            </ul>
                        </li>
                    </ul>
                    <p v-if="results.some((r) => r.unmatchedEntries > 0)" class="text-xs text-grey-dark">
                        Entries that don't match a current term (by speaker and text) are kept, but not used.
                    </p>
                </div>

                <!-- Choose -->
                <div v-else class="px-5 py-4 flex flex-col gap-4 overflow-y-auto">
                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Language</label>
                        <select
                            v-model="language"
                            data-test="import-language"
                            class="w-full font-title bg-white p-3 rounded-md border-2 border-gray-300 text-sm"
                        >
                            <option v-for="l in languages" :key="l.code" :value="l.code">{{ l.name }} ({{ l.code }})</option>
                        </select>
                    </div>

                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Folder</label>
                        <input
                            v-model="folder"
                            list="import-translations-folders"
                            data-test="import-folder"
                            class="w-full font-title bg-white p-3 rounded-md border-2 border-gray-300 text-sm"
                            :class="{ 'border-red-500': !folderValid }"
                            placeholder="folder/subfolder (empty for the project root)"
                        />
                        <datalist id="import-translations-folders">
                            <option v-for="suggestion in folderSuggestions" :key="suggestion" :value="suggestion" />
                        </datalist>
                    </div>

                    <input ref="fileInput" type="file" accept=".json" multiple class="hidden" data-test="import-files" @change="onFilesChosen" />
                    <button
                        type="button"
                        class="flex items-center justify-center gap-2 border-2 border-dashed border-grey-light rounded-lg px-4 py-6 text-sm font-title text-grey-dark hover:border-orange-medium hover:text-orange-darker cursor-pointer"
                        :disabled="importing"
                        @click="pickFiles"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-file-lines" class="text-xl" />
                        <span v-if="files.length">{{ files.length }} file(s) chosen, click to choose again</span>
                        <span v-else>Click to choose one or more .json translation files...</span>
                    </button>

                    <ul v-if="rows.length" class="text-sm font-title flex flex-col gap-1" data-test="import-rows">
                        <li v-for="row in rows" :key="row.file.name" :data-status="row.status">
                            <span class="font-mono">{{ row.dialogueName }}</span>
                            <span class="ml-2 text-xs" :class="{ 'text-green-700': row.status === 'new', 'text-orange-darker': row.status === 'exists', 'text-red-600': row.status === 'missing' || row.status === 'deleted' }">{{ statusLabel[row.status] }}</span>
                            <span v-if="!row.validName" class="ml-2 text-xs text-red-600">not a valid dialogue name</span>
                            <ul v-if="fileErrors[row.file.name]" class="ml-4 text-xs text-red-600 list-disc">
                                <li v-for="(message, index) in fileErrors[row.file.name]" :key="index">{{ message }}</li>
                            </ul>
                        </li>
                    </ul>

                    <p v-if="blockedRows.length" class="text-sm font-title text-red-600" data-test="import-blocked">
                        A translation can only be imported for an existing dialogue that isn't pending deletion. Choose another folder, or leave these files out.
                    </p>

                    <div v-if="existingRows.length" class="border border-orange-medium/50 bg-orange-light/20 rounded-lg px-4 py-3 text-sm font-title" data-test="import-replace-warning">
                        <p>
                            {{ existingRows.length }} of these dialogues already have a {{ languageName }} translation.
                            Replacing it overwrites the draft translation as a whole; what is published (if anything) stays until you publish again.
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
                    <template v-if="results || languages.length === 0">
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
                            :text="`Import ${rowsToImport.length || ''} translation(s)`"
                            variant="green"
                            data-test="import-submit"
                            :disabled="!canImport"
                            :loading="importing"
                            @click="submitImport"
                        />
                    </template>
                </div>
            </div>
        </div>
    </Teleport>
</template>
