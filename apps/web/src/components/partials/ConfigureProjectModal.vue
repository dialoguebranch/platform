<script setup>
import { computed, ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { logEvent } from '../../composables/debug-log.js';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';

const props = defineProps({
    projectSlug: { type: String, required: true },
});

const emit = defineEmits(['close', 'saved']);

const client = useClient();

function formatPublishedAt(publishedAt) {
    if (!publishedAt) return 'Unknown';
    return new Date(publishedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
}

const loading = ref(true);
const saving = ref(false);
const error = ref('');

const activeTab = ref('general'); // 'general' | 'languages'

const displayName = ref('');
const description = ref('');
// Snapshots taken on load, used only to detect unsaved changes to displayName/description below.
const originalDisplayName = ref('');
const originalDescription = ref('');
const latestVersion = ref(null);
const sourceLanguageCode = ref('');
const sourceLanguageName = ref('');

// Nothing below is sent to the server until Save is pressed — language add/remove used to call
// the API immediately; they now only stage local changes, reconciled with the server in onSave
// alongside the display name/description, matching Cancel's existing all-or-nothing behavior.
const existingLanguages = ref([]); // as loaded from the server: [{ id, translationLanguageName, translationLanguageCode }]
const removedLanguageIds = ref(new Set()); // ids from existingLanguages staged for removal
const newLanguages = ref([]); // staged additions, not yet persisted: [{ localId, name, code }]
let nextNewLanguageId = 0;

// Every language the table should show, including ones staged for removal (kept visible with a
// "pending deletion" tag, rather than disappearing immediately) — plus staged additions.
const displayedLanguages = computed(() => [
    ...existingLanguages.value.map((t) => ({
        id: t.id,
        translationLanguageName: t.translationLanguageName,
        translationLanguageCode: t.translationLanguageCode,
        isNew: false,
        pendingRemoval: removedLanguageIds.value.has(t.id),
    })),
    ...newLanguages.value.map((t) => ({
        id: `new-${t.localId}`,
        translationLanguageName: t.name,
        translationLanguageCode: t.code,
        isNew: true,
        pendingRemoval: false,
        localId: t.localId,
    })),
].sort((a, b) => a.translationLanguageCode.localeCompare(b.translationLanguageCode)));

// What the project's translation languages would actually look like after Save (i.e. excluding
// ones staged for removal) — used for the tab count and the duplicate-code check.
const activeLanguages = computed(() => displayedLanguages.value.filter((t) => !t.pendingRemoval));

// Whether there's anything Save would actually do — the button is disabled otherwise.
const hasUnsavedChanges = computed(() =>
    displayName.value !== originalDisplayName.value
    || description.value !== originalDescription.value
    || removedLanguageIds.value.size > 0
    || newLanguages.value.length > 0);

const newTranslationName = ref('');
const newTranslationCode = ref('');
const addTranslationLanguageError = ref('');

// Set while a removal's reference-check request is in flight, to prevent double-clicks.
const checkingLanguageUsage = ref(false);
// Set when a removal is pending a reference-check confirmation: { translationLanguage, dialogueNames }
const removeLanguagePrompt = ref(null);

watch(() => props.projectSlug, load, { immediate: true });

function load() {
    loading.value = true;
    error.value = '';
    activeTab.value = 'general';
    removedLanguageIds.value = new Set();
    newLanguages.value = [];
    client.getProject(props.projectSlug)
        .then((project) => {
            displayName.value = project.draftDisplayName ?? project.displayName ?? '';
            description.value = project.draftDescription ?? project.description ?? '';
            originalDisplayName.value = displayName.value;
            originalDescription.value = description.value;
            latestVersion.value = project.latestVersion ?? null;
            sourceLanguageCode.value = project.sourceLanguageCode ?? '';
            sourceLanguageName.value = project.sourceLanguageName ?? '';
            existingLanguages.value = [...(project.draftTranslationLanguages ?? [])]
                .filter((t) => !t.isDeleted);
        })
        .catch(() => { error.value = 'Failed to load project data.'; })
        .finally(() => { loading.value = false; });
}

// A pending save's `.then()` still fires `emit('saved', ...)` after the modal closes if the user
// closes it mid-save — guard the close paths (header X, Cancel, backdrop click) so the user can't
// dismiss the modal while that request is still in flight.
function closeModal() {
    if (saving.value) return;
    emit('close');
}

// Applies every staged change — metadata, language removals, language additions — in one go, only
// once Save is pressed. Runs sequentially per language (rather than Promise.all) so a failure part
// way through leaves a predictable, reportable state instead of a partial race.
function onSave() {
    saving.value = true;
    error.value = '';

    const removalIds = [...removedLanguageIds.value];
    const additions = newLanguages.value.map((t) => ({ name: t.name, code: t.code }));

    client.updateProject(props.projectSlug, displayName.value, description.value)
        .then((updated) => removalIds
            .reduce((chain, id) => chain.then(() =>
                client.removeTranslationLanguage(props.projectSlug, id)), Promise.resolve())
            .then(() => additions.reduce((chain, lang) => chain.then(() =>
                client.addTranslationLanguage(props.projectSlug, lang.name, lang.code)),
                Promise.resolve()))
            .then(() => updated))
        .then((updated) => {
            logEvent('project', 'Draft metadata saved for project $1', props.projectSlug);
            removedLanguageIds.value = new Set();
            newLanguages.value = [];
            originalDisplayName.value = displayName.value;
            originalDescription.value = description.value;
            emit('saved', { slug: updated.slug, displayName: updated.draftDisplayName });
        })
        .catch(() => { error.value = 'Failed to save project changes.'; })
        .finally(() => { saving.value = false; });
}

function onAddTranslationLanguage() {
    addTranslationLanguageError.value = '';
    const name = newTranslationName.value.trim();
    const code = newTranslationCode.value.trim();
    if (!name || !code) {
        addTranslationLanguageError.value = 'Both fields are required.';
        return;
    }
    if (displayedLanguages.value.some((t) => t.translationLanguageCode === code)) {
        addTranslationLanguageError.value = 'A translation language with this code is already in the list.';
        return;
    }
    newLanguages.value.push({ localId: nextNewLanguageId++, name, code });
    newTranslationName.value = '';
    newTranslationCode.value = '';
}

function onRemoveTranslationLanguage(translationLanguage) {
    // Never persisted — just drop it from the staged list, nothing to check against.
    if (translationLanguage.isNew) {
        newLanguages.value = newLanguages.value.filter((t) => t.localId !== translationLanguage.localId);
        return;
    }
    error.value = '';
    checkingLanguageUsage.value = true;
    client.findLanguageReferences(props.projectSlug, translationLanguage.id)
        .then((dialogueNames) => {
            if (dialogueNames.length > 0) {
                removeLanguagePrompt.value = { translationLanguage, dialogueNames };
            } else {
                stageRemoveTranslationLanguage(translationLanguage);
            }
        })
        .catch(() => { error.value = 'Failed to check translation language usage.'; })
        .finally(() => { checkingLanguageUsage.value = false; });
}

function stageRemoveTranslationLanguage(translationLanguage) {
    removedLanguageIds.value.add(translationLanguage.id);
    removeLanguagePrompt.value = null;
}

function onUndoRemoveTranslationLanguage(translationLanguage) {
    removedLanguageIds.value.delete(translationLanguage.id);
}

function confirmRemoveTranslationLanguage() {
    if (!removeLanguagePrompt.value) return;
    stageRemoveTranslationLanguage(removeLanguagePrompt.value.translationLanguage);
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="closeModal">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-sliders" />
                        Configure Project
                    </div>
                    <button
                        class="text-orange-light hover:text-white disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:text-orange-light"
                        :class="{ 'cursor-pointer': !saving }"
                        :disabled="saving"
                        @click="closeModal"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <!-- Loading -->
                <div v-if="loading" class="flex items-center justify-center py-12 text-orange-darker">
                    <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin text-2xl" />
                </div>

                <template v-else>
                    <!-- Tab bar -->
                    <div class="flex items-end bg-white pt-2 px-3 relative shrink-0">
                        <div class="absolute bottom-0 left-0 right-0 h-px bg-grey-light pointer-events-none z-0"></div>
                        <button
                            type="button"
                            class="flex items-center gap-2 px-4 py-2 font-title text-sm rounded-t border shrink-0 cursor-pointer"
                            :class="activeTab === 'general'
                                ? 'bg-white border-grey-light border-b-2 border-b-white text-orange-darker font-semibold relative z-[100] -mb-px'
                                : 'bg-grey-lighter border-grey-light border-b-0 text-grey-dark hover:bg-white'"
                            @click="activeTab = 'general'"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-file-lines" class="text-xs" />
                            General
                        </button>
                        <button
                            type="button"
                            class="flex items-center gap-2 px-4 py-2 font-title text-sm rounded-t border shrink-0 cursor-pointer"
                            :class="activeTab === 'languages'
                                ? 'bg-white border-grey-light border-b-2 border-b-white text-orange-darker font-semibold relative z-[100] -mb-px'
                                : 'bg-grey-lighter border-grey-light border-b-0 text-grey-dark hover:bg-white'"
                            @click="activeTab = 'languages'"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-language" class="text-xs" />
                            Languages
                            <span class="text-xs font-normal text-grey-dark">({{ activeLanguages.length }})</span>
                        </button>
                    </div>

                    <!-- Content -->
                    <!-- Fixed height (not max-height) so the modal doesn't resize when switching
                         tabs — General and Languages have different content heights, and each
                         tab's content scrolls internally within this fixed area instead. -->
                    <div class="overflow-y-auto px-5 py-4 flex flex-col gap-5" style="height: min(32rem, calc(100vh - 14rem))">

                        <!-- Error banner -->
                        <div v-if="error" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                            {{ error }}
                        </div>

                        <!-- General tab -->
                        <template v-if="activeTab === 'general'">
                            <!-- Project slug (read-only — fixed at project creation) -->
                            <div>
                                <label class="flex items-center gap-1.5 font-title font-bold text-sm text-orange-darker mb-1">
                                    <FontAwesomeIcon icon="fa-solid fa-fingerprint" class="text-xs" />
                                    Project Slug
                                </label>
                                <div class="px-3 py-2 border border-grey-light rounded-lg text-sm font-mono bg-grey-lighter text-grey-dark">
                                    {{ projectSlug }}
                                </div>
                            </div>

                            <!-- Display name -->
                            <div>
                                <label class="flex items-center gap-1.5 font-title font-bold text-sm text-orange-darker mb-1">
                                    <FontAwesomeIcon icon="fa-solid fa-heading" class="text-xs" />
                                    Display Name
                                </label>
                                <TextInput class="w-full" v-model="displayName" placeholder="Project display name..." />
                            </div>

                            <!-- Description -->
                            <div>
                                <label class="flex items-center gap-1.5 font-title font-bold text-sm text-orange-darker mb-1">
                                    <FontAwesomeIcon icon="fa-solid fa-align-left" class="text-xs" />
                                    Description
                                </label>
                                <textarea
                                    v-model="description"
                                    rows="3"
                                    placeholder="Project description..."
                                    class="w-full px-3 py-2 border border-grey-light rounded-lg text-sm font-title focus:outline-none focus:border-orange-dark resize-none"
                                ></textarea>
                            </div>

                            <!-- Published version info (read-only) -->
                            <div v-if="latestVersion" class="flex flex-col gap-2">
                                <label class="flex items-center gap-1.5 font-title font-bold text-sm text-orange-darker">
                                    <FontAwesomeIcon icon="fa-solid fa-rocket" class="text-xs" />
                                    Latest Published Version
                                </label>
                                <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 font-mono text-xs text-grey-dark bg-grey-lighter border border-grey-light rounded-lg px-4 py-3">
                                    <dt class="font-title font-semibold text-orange-darker">Version</dt>
                                    <dd>{{ latestVersion.versionNumber }}</dd>
                                    <dt class="font-title font-semibold text-orange-darker">Published At</dt>
                                    <dd class="font-title">{{ formatPublishedAt(latestVersion.publishedAt) }}</dd>
                                    <dt class="font-title font-semibold text-orange-darker">ID</dt>
                                    <dd class="truncate">{{ latestVersion.id }}</dd>
                                </dl>
                            </div>
                        </template>

                        <!-- Languages tab -->
                        <template v-else>
                            <!-- Source language (read-only — fixed at project creation) -->
                            <div>
                                <label class="block font-title font-bold text-sm text-orange-darker mb-1">Source Language</label>
                                <div class="px-3 py-2 border border-grey-light rounded-lg text-sm font-title bg-grey-lighter text-grey-dark">
                                    {{ sourceLanguageName }} <span class="font-mono text-xs">({{ sourceLanguageCode }})</span>
                                </div>
                            </div>

                            <div class="flex flex-col gap-2">
                                <label class="block font-title font-bold text-sm text-orange-darker">Translation Languages</label>

                                <!-- Existing translation languages -->
                                <div v-if="displayedLanguages.length === 0" class="text-sm text-grey-dark italic">No translation languages defined.</div>
                                <table v-else class="w-full text-xs font-title border border-grey-light rounded-lg overflow-hidden">
                                    <thead class="bg-grey-lighter text-orange-darker font-semibold">
                                        <tr>
                                            <th class="text-left px-3 py-2">Translation</th>
                                            <th class="text-left px-3 py-2">Code</th>
                                            <th class="px-2 py-2"></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="t in displayedLanguages" :key="t.id" class="border-t border-grey-lighter hover:bg-grey-lighter" :class="{ 'opacity-60': t.pendingRemoval }">
                                            <td class="px-3 py-2" :class="{ 'line-through': t.pendingRemoval }">
                                                {{ t.translationLanguageName }}
                                                <span v-if="t.isNew" class="ml-1 text-[10px] font-normal text-icon-button uppercase tracking-wide">(unsaved)</span>
                                                <span v-if="t.pendingRemoval" class="ml-1 text-[10px] font-normal text-red-dark uppercase tracking-wide">(pending deletion)</span>
                                            </td>
                                            <td class="px-3 py-2 font-mono text-orange-darker" :class="{ 'line-through': t.pendingRemoval }">{{ t.translationLanguageCode }}</td>
                                            <td class="px-2 py-2 text-right">
                                                <button
                                                    v-if="t.pendingRemoval"
                                                    class="text-grey-dark hover:text-orange-darker cursor-pointer"
                                                    title="Undo removal"
                                                    @click="onUndoRemoveTranslationLanguage(t)"
                                                >
                                                    <FontAwesomeIcon icon="fa-solid fa-rotate-left" />
                                                </button>
                                                <button
                                                    v-else
                                                    class="text-red-dark hover:text-red-darker disabled:opacity-40 disabled:cursor-not-allowed"
                                                    :class="{ 'cursor-pointer': !checkingLanguageUsage }"
                                                    :disabled="checkingLanguageUsage"
                                                    title="Remove translation language"
                                                    @click="onRemoveTranslationLanguage(t)"
                                                >
                                                    <FontAwesomeIcon icon="fa-solid fa-trash" />
                                                </button>
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>

                            <!-- Add new translation language -->
                            <div class="border border-dashed border-orange-medium rounded-lg px-3 py-3 flex flex-col gap-2">
                                <span class="font-title font-semibold text-xs text-orange-darker">Add Translation Language</span>
                                <div class="grid grid-cols-2 gap-2">
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Language name</label>
                                        <TextInput class="w-full" v-model="newTranslationName" placeholder="e.g. Dutch" />
                                    </div>
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Language code</label>
                                        <TextInput class="w-full" v-model="newTranslationCode" placeholder="e.g. nl" />
                                    </div>
                                </div>
                                <div v-if="addTranslationLanguageError" class="text-xs text-red-dark font-title">{{ addTranslationLanguageError }}</div>
                                <div>
                                    <PushButton text="Add Language" @click="onAddTranslationLanguage" />
                                </div>
                            </div>
                        </template>
                    </div>
                </template>

                <!-- Footer -->
                <div v-if="!loading" class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <button
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                        :class="{ 'cursor-pointer': !saving }"
                        :disabled="saving"
                        @click="closeModal"
                    >Cancel</button>
                    <PushButton text="Save Draft" variant="green" :loading="saving" :disabled="!hasUnsavedChanges" @click="onSave" />
                </div>
            </div>
        </div>

        <!-- Language removal reference-check confirmation -->
        <div v-if="removeLanguagePrompt" class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/40">
            <div class="bg-white rounded-xl shadow-2xl p-5 font-title text-sm w-96">
                <div class="font-bold text-orange-darker mb-2 flex items-center gap-2">
                    <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="text-orange-medium" />
                    Remove Translation Language
                </div>
                <p class="text-grey-dark mb-2">
                    <strong>{{ removeLanguagePrompt.translationLanguage.translationLanguageName }}</strong>
                    ({{ removeLanguagePrompt.translationLanguage.translationLanguageCode }}) has content in
                    {{ removeLanguagePrompt.dialogueNames.length }} dialogue{{ removeLanguagePrompt.dialogueNames.length === 1 ? '' : 's' }}:
                </p>
                <ul class="text-xs font-mono text-grey-dark bg-grey-lighter rounded-lg px-3 py-2 mb-4 max-h-32 overflow-y-auto list-disc list-inside">
                    <li v-for="name in removeLanguagePrompt.dialogueNames" :key="name">{{ name }}</li>
                </ul>
                <p class="text-grey-dark mb-4">Removing this language will delete that content once you press Save (and the project is next published). Continue?</p>
                <div class="flex gap-2 justify-end">
                    <button
                        type="button"
                        class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer"
                        @click="removeLanguagePrompt = null"
                    >Cancel</button>
                    <button
                        type="button"
                        class="px-3 py-1.5 rounded bg-red-dark text-white hover:opacity-90 text-xs font-semibold cursor-pointer"
                        @click="confirmRemoveTranslationLanguage"
                    >Remove Anyway</button>
                </div>
            </div>
        </div>
    </Teleport>
</template>
