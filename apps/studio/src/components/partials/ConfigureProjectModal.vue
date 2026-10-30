<script setup>
import { computed, ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '../../composables/client.js';
import { logEvent } from '../../composables/debug-log.js';
import { describeError } from '../../composables/error-message.js';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';
import { SPEAKER_GENDERS, checkNewSpeakerName, speakersDiffer } from '../../composables/project-speakers.js';

const props = defineProps({
    projectSlug: { type: String, required: true },
});

const emit = defineEmits(['close', 'saved']);

const client = useAuthoringClient();

function formatPublishedAt(publishedAt) {
    if (!publishedAt) return 'Unknown';
    return new Date(publishedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
}

const loading = ref(true);
const saving = ref(false);
const error = ref('');

const activeTab = ref('general'); // 'general' | 'languages' | 'speakers'

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
// Pending renames of EXISTING languages, keyed by id -> { name, code }. Cleared for an entry as
// soon as its staged value matches the original again, so hasUnsavedChanges/the save payload
// never report a no-op rename.
const updatedLanguages = ref(new Map());
// Per-row validation messages from the server's structured fieldErrors, keyed by the same row id
// used in displayedLanguages (existing language's id, or `new-${localId}` for staged additions).
const languageErrors = ref(new Map());

// Every language the table should show, including ones pending deletion — either already
// persisted as such (t.isDeleted, from a previous session) or staged this session
// (removedLanguageIds) — kept visible with a "pending deletion" tag rather than disappearing,
// plus staged additions. Sorted by each row's ORIGINAL code (not the live-edited one) so a row
// doesn't jump around the table while its code is being typed.
const displayedLanguages = computed(() => {
    const existing = existingLanguages.value
        .map((t) => {
            const staged = updatedLanguages.value.get(t.id);
            return {
                id: t.id,
                translationLanguageName: staged?.name ?? t.translationLanguageName,
                translationLanguageCode: staged?.code ?? t.translationLanguageCode,
                isNew: false,
                pendingRemoval: t.isDeleted || removedLanguageIds.value.has(t.id),
                sortKey: t.translationLanguageCode,
            };
        })
        .sort((a, b) => a.sortKey.localeCompare(b.sortKey));
    const additions = newLanguages.value.map((t) => ({
        id: `new-${t.localId}`,
        translationLanguageName: t.name,
        translationLanguageCode: t.code,
        isNew: true,
        pendingRemoval: false,
        localId: t.localId,
    }));
    return [...existing, ...additions];
});

// What the project's translation languages would actually look like after Save (i.e. excluding
// ones staged for removal) — used for the tab count and the duplicate-code check.
const activeLanguages = computed(() => displayedLanguages.value.filter((t) => !t.pendingRemoval));

// The project's speakers (#395): staged here like everything else, and sent as the complete new
// list on Save. A rename (#398) is staged too: a speaker keeps the name it was loaded with as
// `previousName`, and saving renames it in the project's nodes and translations as well, after
// confirming what that changes.
let nextSpeakerKey = 0;
const speakers = ref([]); // [{ key, name, previousName?, gender, default }], in their order
const originalSpeakers = ref([]);
const renamingIndex = ref(-1);
const renameValue = ref('');
const renameError = ref('');
const renameConfirmation = ref(null); // [{ from, to, nodes, dialogues, translations }] to confirm
const stagedRenames = computed(() => speakers.value
    .filter((s) => s.previousName && s.previousName !== s.name)
    .map((s) => ({ from: s.previousName, to: s.name })));
const newSpeakerName = ref('');
const newSpeakerGender = ref('undefined');
const addSpeakerError = ref('');
const speakerError = ref(''); // the server's reason a save refused the speakers

const speakersChanged = computed(() => speakersDiffer(speakers.value, originalSpeakers.value));

// Whether there's anything Save would actually do — the button is disabled otherwise.
const hasUnsavedChanges = computed(() =>
    displayName.value !== originalDisplayName.value
    || description.value !== originalDescription.value
    || removedLanguageIds.value.size > 0
    || newLanguages.value.length > 0
    || updatedLanguages.value.size > 0
    || speakersChanged.value);

const newTranslationName = ref('');
const newTranslationCode = ref('');
const addTranslationLanguageError = ref('');

// Set while a removal's reference-check request is in flight, to prevent double-clicks.
const checkingLanguageUsage = ref(false);
// Set when a removal is pending a reference-check confirmation: { translationLanguage, dialogueNames }
const removeLanguagePrompt = ref(null);
// Ids currently being restored (undoing an already-persisted deletion) — disables that row's undo
// button and prevents double-clicks while the request is in flight.
const restoringLanguageIds = ref(new Set());

watch(() => props.projectSlug, load, { immediate: true });

function load() {
    loading.value = true;
    error.value = '';
    activeTab.value = 'general';
    removedLanguageIds.value = new Set();
    newLanguages.value = [];
    updatedLanguages.value = new Map();
    languageErrors.value = new Map();
    newSpeakerName.value = '';
    newSpeakerGender.value = 'undefined';
    addSpeakerError.value = '';
    speakerError.value = '';
    client.getProject(props.projectSlug)
        .then((project) => {
            displayName.value = project.draftDisplayName ?? project.latestVersion?.displayName ?? '';
            description.value = project.draftDescription ?? project.latestVersion?.description ?? '';
            originalDisplayName.value = displayName.value;
            originalDescription.value = description.value;
            latestVersion.value = project.latestVersion ?? null;
            sourceLanguageCode.value = project.sourceLanguageCode ?? '';
            sourceLanguageName.value = project.sourceLanguageName ?? '';
            // Includes languages already pending deletion (isDeleted=true) from a previous
            // session — kept visible, tagged, and undoable rather than silently dropped, since
            // they're only actually gone once the project is published.
            existingLanguages.value = [...(project.draftTranslationLanguages ?? [])];
            speakers.value = (project.draftSpeakers ?? []).map((s) => ({
                key: nextSpeakerKey++, name: s.name, previousName: s.name,
                gender: s.gender ?? 'undefined', default: !!s.default,
            }));
            originalSpeakers.value = speakers.value.map((s) => ({ ...s }));
        })
        .catch(() => { error.value = 'Failed to load project data.'; })
        .finally(() => { loading.value = false; });
}

// A pending save's `.then()` still fires `emit('saved', ...)` after the modal closes if the user
// closes it mid-save — guard the close paths (header X, Cancel) so the user can't
// dismiss the modal while that request is still in flight.
function closeModal() {
    if (saving.value) return;
    emit('close');
}

// Applies every staged change — metadata, language removals/additions/renames — in a single
// atomic request: either all of it lands, or (if the server finds any problem with the batch)
// none of it does, and the offending rows are highlighted via languageErrors below.
function onSave() {
    if (stagedRenames.value.length > 0 && !renameConfirmation.value) {
        confirmRenames();
        return;
    }
    renameConfirmation.value = null;
    saving.value = true;
    error.value = '';
    languageErrors.value = new Map();
    speakerError.value = '';

    const payload = {
        displayName: displayName.value,
        description: description.value,
        removeLanguageIds: [...removedLanguageIds.value],
        addLanguages: newLanguages.value.map((t) => ({
            translationLanguageName: t.name,
            translationLanguageCode: t.code,
        })),
        updateLanguages: [...updatedLanguages.value.entries()].map(([id, staged]) => ({
            id,
            translationLanguageName: staged.name,
            translationLanguageCode: staged.code,
        })),
    };
    // Only when they changed: leaving them out leaves the project's speakers as they are.
    if (speakersChanged.value) {
        payload.speakers = speakers.value.map((s) => ({
            name: s.name, gender: s.gender, default: s.default,
            ...(s.previousName && s.previousName !== s.name ? { previousName: s.previousName } : {}),
        }));
    }
    const renamedSpeakers = stagedRenames.value.length > 0;

    client.updateProjectDraft(props.projectSlug, payload)
        .then((updated) => {
            logEvent('project', 'Draft metadata saved for project $1', props.projectSlug);
            removedLanguageIds.value = new Set();
            newLanguages.value = [];
            updatedLanguages.value = new Map();
            originalDisplayName.value = displayName.value;
            originalDescription.value = description.value;
            speakers.value.forEach((s) => { s.previousName = s.name; });
            originalSpeakers.value = speakers.value.map((s) => ({ ...s }));
            emit('saved', { slug: updated.slug, displayName: updated.draftDisplayName, renamedSpeakers });
        })
        .catch((err) => { applySaveError(err); })
        .finally(() => { saving.value = false; });
}

// Maps the server's structured fieldErrors (see DraftProjectService#updateDraft) onto the
// specific table row they came from, so the user sees exactly which language conflicted rather
// than just a generic failure message. Falls back to the general error banner for anything that
// can't be attributed to a row (e.g. a removeLanguageIds id that no longer resolves).
function applySaveError(err) {
    const fieldErrors = err?.fieldErrors ?? [];
    if (fieldErrors.length === 0) {
        error.value = describeError(err) || 'Failed to save project changes.';
        return;
    }

    const rowErrors = new Map();
    const generalMessages = [];
    const speakerMessages = [];
    for (const fieldError of fieldErrors) {
        const field = fieldError.field ?? '';
        const message = fieldError.message ?? 'Invalid value.';
        if (field === 'speakers') {
            speakerMessages.push(message);
            continue;
        }
        const updateMatch = field.match(/^updateLanguages\[(.+)]$/);
        const addMatch = field.match(/^addLanguages\[(.+)]$/);
        if (updateMatch) {
            rowErrors.set(updateMatch[1], message);
            continue;
        }
        if (addMatch) {
            const staged = newLanguages.value.find((t) => t.code === addMatch[1]);
            if (staged) {
                rowErrors.set(`new-${staged.localId}`, message);
                continue;
            }
        }
        generalMessages.push(message);
    }

    languageErrors.value = rowErrors;
    speakerError.value = speakerMessages.join(' ');
    if (generalMessages.length > 0) {
        error.value = generalMessages.join(' ');
    } else if (rowErrors.size > 0) {
        error.value = 'Some translation language changes could not be saved — see below.';
    } else {
        error.value = 'The speakers could not be saved — see below.';
    }
    activeTab.value = rowErrors.size > 0 || speakerMessages.length === 0 ? 'languages' : 'speakers';
}

// Before a save that renames speakers, shows what each rename changes in the project's nodes and
// translations, and waits for the user to confirm (see onSave).
function confirmRenames() {
    saving.value = true;
    error.value = '';
    Promise.all(stagedRenames.value.map((rename) =>
        client.findSpeakerReferences(props.projectSlug, rename.from)
            .then((counts) => ({ ...rename, ...counts }))))
        .then((renames) => { renameConfirmation.value = renames; })
        .catch(() => { error.value = 'Could not check what renaming the speakers changes. Please try again.'; })
        .finally(() => { saving.value = false; });
}

function describeRename(rename) {
    const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;
    return `Renaming "${rename.from}" to "${rename.to}" changes ${plural(rename.nodes, 'node')} `
        + `in ${plural(rename.dialogues, 'dialogue')}, and ${plural(rename.translations, 'translation')}.`;
}

// Any change after asking for confirmation makes the counts stale: ask again on Save.
watch(speakers, () => { renameConfirmation.value = null; }, { deep: true });

function onStartRename(index) {
    renamingIndex.value = index;
    renameValue.value = speakers.value[index].name;
    renameError.value = '';
}

function onApplyRename() {
    const index = renamingIndex.value;
    const name = renameValue.value.trim();
    if (name !== speakers.value[index].name) {
        const others = speakers.value.filter((_, i) => i !== index);
        const problem = checkNewSpeakerName(name, others);
        if (problem) {
            renameError.value = problem;
            return;
        }
        speakers.value[index].name = name;
        speakerError.value = '';
    }
    renamingIndex.value = -1;
}

function onAddSpeaker() {
    const name = newSpeakerName.value.trim();
    const problem = checkNewSpeakerName(name, speakers.value);
    addSpeakerError.value = problem ?? '';
    if (problem) return;
    speakers.value.push({ name, gender: newSpeakerGender.value, default: false });
    newSpeakerName.value = '';
    newSpeakerGender.value = 'undefined';
    speakerError.value = '';
}

function onRemoveSpeaker(index) {
    speakers.value.splice(index, 1);
    speakerError.value = '';
}

// At most one default: choosing a speaker makes it the default; choosing the default again
// leaves the project without one.
function onToggleDefaultSpeaker(index) {
    const makeDefault = !speakers.value[index].default;
    speakers.value.forEach((s, i) => { s.default = makeDefault && i === index; });
    speakerError.value = '';
}

function onSpeakerGender(index, gender) {
    speakers.value[index].gender = gender;
    speakerError.value = '';
}

function clearLanguageError(id) {
    if (languageErrors.value.has(id)) languageErrors.value.delete(id);
}

// Stages a rename for an EXISTING language's name/code — nothing is sent to the server until Save
// is pressed. Drops the staging entry once the value matches the original again, so a rename that
// was typed and then undone doesn't linger as a no-op change.
function stageLanguageUpdate(translationLanguage, field, value) {
    const original = existingLanguages.value.find((t) => t.id === translationLanguage.id);
    if (!original) return;
    clearLanguageError(translationLanguage.id);
    const staged = updatedLanguages.value.get(translationLanguage.id) ?? {
        name: original.translationLanguageName,
        code: original.translationLanguageCode,
    };
    staged[field] = value;
    if (staged.name === original.translationLanguageName && staged.code === original.translationLanguageCode) {
        updatedLanguages.value.delete(translationLanguage.id);
    } else {
        updatedLanguages.value.set(translationLanguage.id, staged);
    }
}

function onLanguageNameInput(translationLanguage, value) {
    if (translationLanguage.isNew) {
        clearLanguageError(translationLanguage.id);
        const staged = newLanguages.value.find((t) => t.localId === translationLanguage.localId);
        if (staged) staged.name = value;
    } else {
        stageLanguageUpdate(translationLanguage, 'name', value);
    }
}

function onLanguageCodeInput(translationLanguage, value) {
    if (translationLanguage.isNew) {
        clearLanguageError(translationLanguage.id);
        const staged = newLanguages.value.find((t) => t.localId === translationLanguage.localId);
        if (staged) staged.code = value;
    } else {
        stageLanguageUpdate(translationLanguage, 'code', value);
    }
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
    // A row's edit inputs are hidden once it's pending removal, but clear any staged rename here
    // too — defensively keeps "removed" and "renamed" from ever being staged for the same id.
    updatedLanguages.value.delete(translationLanguage.id);
    removedLanguageIds.value.add(translationLanguage.id);
    removeLanguagePrompt.value = null;
}

function onUndoRemoveTranslationLanguage(translationLanguage) {
    // Staged for removal this session, not yet persisted — just unstage locally, no API call.
    if (removedLanguageIds.value.has(translationLanguage.id)) {
        removedLanguageIds.value.delete(translationLanguage.id);
        return;
    }
    // Already persisted as deleted server-side (e.g. from a previous session) — actually restore it.
    error.value = '';
    restoringLanguageIds.value.add(translationLanguage.id);
    client.restoreTranslationLanguage(props.projectSlug, translationLanguage.id)
        .then(() => {
            const existing = existingLanguages.value.find((t) => t.id === translationLanguage.id);
            if (existing) existing.isDeleted = false;
            logEvent('project', 'Draft translation language restored for $1', props.projectSlug);
        })
        .catch(() => { error.value = 'Failed to restore translation language.'; })
        .finally(() => { restoringLanguageIds.value.delete(translationLanguage.id); });
}

function confirmRemoveTranslationLanguage() {
    if (!removeLanguagePrompt.value) return;
    stageRemoveTranslationLanguage(removeLanguagePrompt.value.translationLanguage);
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50">
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
                        <button
                            type="button"
                            data-test="tab-speakers"
                            class="flex items-center gap-2 px-4 py-2 font-title text-sm rounded-t border shrink-0 cursor-pointer"
                            :class="activeTab === 'speakers'
                                ? 'bg-white border-grey-light border-b-2 border-b-white text-orange-darker font-semibold relative z-[100] -mb-px'
                                : 'bg-grey-lighter border-grey-light border-b-0 text-grey-dark hover:bg-white'"
                            @click="activeTab = 'speakers'"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-users" class="text-xs" />
                            Speakers
                            <span class="text-xs font-normal text-grey-dark">({{ speakers.length }})</span>
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

                        <!-- Speakers tab (#395) -->
                        <template v-else-if="activeTab === 'speakers'">
                            <p class="text-xs text-grey-dark font-title">
                                The speakers a node can have. A new node gets the default speaker.
                                A speaker's gender chooses between translations in languages where a
                                text changes with it. Renaming a speaker renames it in the project's
                                nodes and translations too. Removing a speaker doesn't change the nodes that
                                use it.
                            </p>
                            <div v-if="speakerError" data-test="speaker-error" class="text-xs text-red-dark font-title">
                                <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="mr-1" />{{ speakerError }}
                            </div>
                            <div v-if="speakers.length === 0" class="text-sm text-grey-dark italic">
                                No speakers defined. Nodes can then have any speaker.
                            </div>
                            <table v-else class="w-full text-xs font-title border border-grey-light rounded-lg overflow-hidden">
                                <thead class="bg-grey-lighter text-orange-darker font-semibold">
                                    <tr>
                                        <th class="text-left px-3 py-2">Speaker</th>
                                        <th class="text-left px-3 py-2">Gender</th>
                                        <th class="px-3 py-2">Default</th>
                                        <th class="px-2 py-2"></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="(s, i) in speakers" :key="s.key" data-test="speaker-row"
                                        class="border-t border-grey-lighter hover:bg-grey-lighter">
                                        <td v-if="renamingIndex === i" class="px-3 py-2">
                                            <form class="flex items-center gap-1" @submit.prevent="onApplyRename">
                                                <TextInput class="w-full" v-model="renameValue" data-test="speaker-rename-input" />
                                                <button type="submit" data-test="speaker-rename-apply" class="text-orange-darker cursor-pointer" title="Rename">
                                                    <FontAwesomeIcon icon="fa-solid fa-check" />
                                                </button>
                                                <button type="button" data-test="speaker-rename-cancel" class="text-grey-dark cursor-pointer" title="Cancel" @click="renamingIndex = -1">
                                                    <FontAwesomeIcon icon="fa-solid fa-xmark" />
                                                </button>
                                            </form>
                                            <div v-if="renameError" data-test="speaker-rename-error" class="mt-1 text-xs text-red-dark font-title">{{ renameError }}</div>
                                        </td>
                                        <td v-else class="px-3 py-2 text-orange-darker">
                                            <span>{{ s.name }}</span>
                                            <span v-if="s.previousName && s.previousName !== s.name" data-test="speaker-was" class="ml-1 text-xs text-grey-dark">(was {{ s.previousName }})</span>
                                            <button type="button" data-test="speaker-rename" class="ml-2 text-grey-dark hover:text-orange-darker cursor-pointer" title="Rename speaker…" @click="onStartRename(i)">
                                                <FontAwesomeIcon icon="fa-solid fa-pen" class="text-xs" />
                                            </button>
                                        </td>
                                        <td class="px-3 py-2">
                                            <select
                                                :value="s.gender"
                                                data-test="speaker-gender"
                                                class="px-2 py-1 border border-grey-light rounded text-xs font-title bg-white focus:outline-none focus:border-orange-dark"
                                                @change="onSpeakerGender(i, $event.target.value)"
                                            >
                                                <option v-for="g in SPEAKER_GENDERS" :key="g.value" :value="g.value">{{ g.label }}</option>
                                            </select>
                                        </td>
                                        <td class="px-3 py-2 text-center">
                                            <button
                                                type="button"
                                                data-test="speaker-default"
                                                class="cursor-pointer"
                                                :class="s.default ? 'text-orange-darker' : 'text-grey-light hover:text-grey-dark'"
                                                :title="s.default ? 'The default speaker for new nodes; click to have no default' : 'Make this the default speaker for new nodes'"
                                                @click="onToggleDefaultSpeaker(i)"
                                            >
                                                <FontAwesomeIcon :icon="s.default ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle'" />
                                            </button>
                                        </td>
                                        <td class="px-2 py-2 text-right">
                                            <button
                                                type="button"
                                                data-test="speaker-remove"
                                                class="text-red-dark hover:text-red-darker cursor-pointer"
                                                title="Remove speaker"
                                                @click="onRemoveSpeaker(i)"
                                            >
                                                <FontAwesomeIcon icon="fa-solid fa-trash" />
                                            </button>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                            <!-- Add a speaker -->
                            <div class="border border-dashed border-orange-medium rounded-lg px-3 py-3 flex flex-col gap-2">
                                <span class="font-title font-semibold text-xs text-orange-darker">Add Speaker</span>
                                <div class="grid grid-cols-[1fr_auto] gap-2">
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Name</label>
                                        <TextInput class="w-full" v-model="newSpeakerName" data-test="new-speaker-name" placeholder="e.g. Martin McOwl" />
                                    </div>
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Gender</label>
                                        <select
                                            v-model="newSpeakerGender"
                                            data-test="new-speaker-gender"
                                            class="h-[46px] px-2 border-[2px] border-gray-300 rounded-md text-sm font-title bg-white focus:outline-none focus:border-orange-dark"
                                        >
                                            <option v-for="g in SPEAKER_GENDERS" :key="g.value" :value="g.value">{{ g.label }}</option>
                                        </select>
                                    </div>
                                </div>
                                <div v-if="addSpeakerError" data-test="add-speaker-error" class="text-xs text-red-dark font-title">{{ addSpeakerError }}</div>
                                <div>
                                    <PushButton text="Add Speaker" data-test="add-speaker" @click="onAddSpeaker" />
                                </div>
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
                                        <template v-for="t in displayedLanguages" :key="t.id">
                                            <tr class="border-t border-grey-lighter hover:bg-grey-lighter" :class="{ 'opacity-60': t.pendingRemoval }">
                                                <td class="px-3 py-2" :class="{ 'line-through': t.pendingRemoval }">
                                                    <input
                                                        v-if="!t.pendingRemoval"
                                                        type="text"
                                                        class="w-full px-2 py-1 border rounded text-xs font-title bg-white focus:outline-none focus:border-orange-dark"
                                                        :class="languageErrors.has(t.id) ? 'border-red-dark' : 'border-grey-light'"
                                                        :value="t.translationLanguageName"
                                                        placeholder="Language name"
                                                        @input="onLanguageNameInput(t, $event.target.value)"
                                                    />
                                                    <template v-else>{{ t.translationLanguageName }}</template>
                                                    <span v-if="t.isNew" class="ml-1 text-[10px] font-normal text-icon-button uppercase tracking-wide">(unsaved)</span>
                                                    <span v-if="t.pendingRemoval" class="ml-1 text-[10px] font-normal text-red-dark uppercase tracking-wide">(pending deletion)</span>
                                                </td>
                                                <td class="px-3 py-2 font-mono text-orange-darker" :class="{ 'line-through': t.pendingRemoval }">
                                                    <input
                                                        v-if="!t.pendingRemoval"
                                                        type="text"
                                                        class="w-full px-2 py-1 border rounded text-xs font-title font-mono bg-white text-orange-darker focus:outline-none focus:border-orange-dark"
                                                        :class="languageErrors.has(t.id) ? 'border-red-dark' : 'border-grey-light'"
                                                        :value="t.translationLanguageCode"
                                                        placeholder="Code"
                                                        @input="onLanguageCodeInput(t, $event.target.value)"
                                                    />
                                                    <template v-else>{{ t.translationLanguageCode }}</template>
                                                </td>
                                                <td class="px-2 py-2 text-right">
                                                    <button
                                                        v-if="t.pendingRemoval"
                                                        class="text-grey-dark hover:text-orange-darker disabled:opacity-40 disabled:cursor-not-allowed"
                                                        :class="{ 'cursor-pointer': !restoringLanguageIds.has(t.id) }"
                                                        :disabled="restoringLanguageIds.has(t.id)"
                                                        title="Undo removal"
                                                        @click="onUndoRemoveTranslationLanguage(t)"
                                                    >
                                                        <FontAwesomeIcon
                                                            :icon="restoringLanguageIds.has(t.id) ? 'fa-solid fa-circle-notch' : 'fa-solid fa-rotate-left'"
                                                            :class="{ 'animate-spin': restoringLanguageIds.has(t.id) }"
                                                        />
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
                                            <tr v-if="languageErrors.has(t.id)" class="border-t border-grey-lighter">
                                                <td colspan="3" class="px-3 pb-2 text-[11px] text-red-dark font-title">
                                                    <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="mr-1" />{{ languageErrors.get(t.id) }}
                                                </td>
                                            </tr>
                                        </template>
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

                <!-- Confirming speaker renames (#398): they change nodes and translations too -->
                <div v-if="renameConfirmation" data-test="speaker-rename-confirmation" class="px-5 py-3 border-t border-orange-medium/50 bg-orange-light/20 text-sm font-title shrink-0">
                    <ul class="mb-3 flex flex-col gap-1 text-orange-darker">
                        <li v-for="rename in renameConfirmation" :key="rename.from">
                            <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="mr-1" />{{ describeRename(rename) }}
                        </li>
                    </ul>
                    <div class="flex items-center justify-end gap-3">
                        <button
                            class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light bg-white hover:bg-grey-light cursor-pointer"
                            data-test="speaker-rename-back"
                            @click="renameConfirmation = null"
                        >Back</button>
                        <PushButton text="Rename and Save" variant="green" :loading="saving" data-test="speaker-rename-confirm" @click="onSave" />
                    </div>
                </div>

                <!-- Footer -->
                <div v-else-if="!loading" class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
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
