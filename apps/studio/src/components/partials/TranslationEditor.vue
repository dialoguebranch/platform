<script setup>
import { computed, inject, ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { useLatestRequest } from '@/composables/latest-request.js';
import {
    findStaleEntries, withoutEntries, findStoredText, withTranslation, findContextVariants,
} from '@/authoring/DlbTranslations.js';
import StaleTranslationsModal from './StaleTranslationsModal.vue';

// Pure content component — no header of its own, same as DialogueEditor. It's only ever embedded
// in DialogueWorkspace.vue's "translate" mode.
const props = defineProps({
    dialogueName: { type: String, default: null },
});

const emit = defineEmits(['dialogueSaved']);

const state = inject('state');
const client = useAuthoringClient();

const loading = ref(false);
const loadError = ref(null); // { message, errors } — the dialogue's draft content doesn't parse
const translationLanguages = ref([]); // [{ code, name }]
const content = ref({}); // { [languageCode]: { [speaker]: { [term]: translation } } }
const dirtyCells = ref(new Set()); // "speaker term language" keys
const savingCells = ref(new Set());

// Showing every translation language as its own column stops scaling once a project has more
// than a couple — the user instead picks which one or two languages to display at a time.
// Column 2 is always shown once languages exist; column 3 is optional (null = hidden) to save
// space. Selections are plain component state, not persisted — they reset to sensible defaults
// whenever the language list changes (see loadTranslations), but stay put across dialogue/tab
// switches in the meantime since this component instance is reused across tabs.
const column2Language = ref(null); // language code
const column3Language = ref(null); // language code, or null to hide the third column

const column3Options = computed(() =>
    translationLanguages.value.filter((l) => l.code !== column2Language.value));

// Column 2 and 3 must never show the same language twice.
watch(column2Language, () => {
    if (column3Language.value === column2Language.value) column3Language.value = null;
});

const visibleColumns = computed(() => {
    const codes = [column2Language.value, column3Language.value].filter(Boolean);
    return codes
        .map((code) => translationLanguages.value.find((l) => l.code === code))
        .filter(Boolean);
});

// A cell is a term's translation for a speaker in one language, either the speaker's own or a
// gender-specific one in a nested context group (contexts: that group's keys, empty for none).
// Cells match stored entries on whitespace-normalised text, the way the Translator does, so an
// entry stored with a line break in its source text still shows, and editing it replaces it
// instead of adding a second entry next to it (#386).
function cellKey(speaker, term, language, contexts = []) {
    return [speaker, ...contexts, term, language].join(' ');
}

function cellValue(speaker, term, language, contexts = []) {
    const languageContent = content.value[language];
    const storedText = findStoredText(languageContent, speaker, contexts, term);
    if (storedText === null) return '';
    let group = languageContent[speaker];
    for (const context of contexts) group = group[context];
    return group[storedText];
}

function setCellValue(speaker, term, language, value, contexts = []) {
    content.value = {
        ...content.value,
        [language]: withTranslation(content.value[language], speaker, contexts, term, value),
    };
}

// The gender-specific translations of a term for a speaker, in any translation language, each as
// the keys of its context groups (#386). Each is shown as a sub-row under the term.
function contextVariants(speaker, term) {
    const seen = new Map();
    for (const language of translationLanguages.value) {
        for (const contexts of findContextVariants(content.value[language.code], speaker, term)) {
            seen.set(contexts.join(' '), contexts);
        }
    }
    return [...seen.values()];
}

function onCellInput(speaker, term, language, event, contexts = []) {
    setCellValue(speaker, term, language, event.target.value, contexts);
    dirtyCells.value = new Set([...dirtyCells.value, cellKey(speaker, term, language, contexts)]);
}

function submitCell(speaker, term, language, contexts = []) {
    const key = cellKey(speaker, term, language, contexts);
    if (!dirtyCells.value.has(key)) return;
    const projectSlug = state.value.selectedProject?.slug;
    savingCells.value = new Set([...savingCells.value, key]);
    dismissError();
    client.updateDraftTranslation(projectSlug, props.dialogueName, language,
        JSON.stringify(content.value[language] ?? {}))
    .then(() => {
        const nextDirty = new Set(dirtyCells.value);
        nextDirty.delete(key);
        dirtyCells.value = nextDirty;
        // Marks the owning draft dialogue as changed server-side (see
        // DraftDialogueService#createOrUpdateTranslation) — without this, DialogueBrowser's
        // dialogue list (the only thing that recomputes "has unpublished changes") never refetches
        // after a pure translation edit, leaving the Publish Project button disabled.
        emit('dialogueSaved');
    })
    .catch((error) => {
        showError(describeError(error));
    })
    .finally(() => {
        const nextSaving = new Set(savingCells.value);
        nextSaving.delete(key);
        savingCells.value = nextSaving;
    });
}

// Groups the flat terms list by speaker, in first-appearance order — mirrors the grouping a
// translation file's content map already uses (see TranslationFile.java in packages/core).
function groupedTerms(termList) {
    const bySpeaker = new Map();
    for (const { speaker, term } of termList) {
        if (!bySpeaker.has(speaker)) bySpeaker.set(speaker, []);
        bySpeaker.get(speaker).push(term);
    }
    return [...bySpeaker.entries()].map(([speaker, speakerTerms]) => ({ speaker, terms: speakerTerms }));
}

const groups = ref([]);
const terms = ref([]); // [{ speaker, term }], as listed by the Web Service

// ---- Stale entries (#375) ----
// Entries of a language's translation that match no term of the dialogue (on speaker and text).
// The Translator never uses them, and the rows above never show them, so they're listed here,
// for every translation language, not only the visible columns.

const staleSections = computed(() => translationLanguages.value
    .map((language) => ({
        language,
        entries: findStaleEntries(content.value[language.code] ?? {}, terms.value),
    }))
    .filter((section) => section.entries.length > 0));
const staleCount = computed(() =>
    staleSections.value.reduce((count, section) => count + section.entries.length, 0));
const showStale = ref(false);
const deletingStale = ref(false);

function deleteStaleEntries({ languageCode, entries }) {
    const projectSlug = state.value.selectedProject?.slug;
    const next = withoutEntries(content.value[languageCode] ?? {}, entries);
    deletingStale.value = true;
    dismissError();
    client.updateDraftTranslation(projectSlug, props.dialogueName, languageCode, JSON.stringify(next))
        .then(() => {
            content.value = { ...content.value, [languageCode]: next };
            // As for a saved cell: the dialogue is now changed, which the Dialogue Browser shows.
            emit('dialogueSaved');
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            deletingStale.value = false;
        });
}

// TranslationEditor is a single persistent instance reused across tabs (see DialogueWorkspace.vue),
// so switching to a different dialogue before a fetch resolves must not let a stale response
// overwrite the now-current table.
const { next: nextLoadRequest, isCurrent: isCurrentLoadRequest } = useLatestRequest();

function loadTranslations() {
    groups.value = [];
    terms.value = [];
    showStale.value = false;
    translationLanguages.value = [];
    content.value = {};
    dirtyCells.value = new Set();
    loadError.value = null;
    if (!props.dialogueName) return;

    const projectSlug = state.value.selectedProject?.slug;
    loading.value = true;
    dismissError();
    const requestId = nextLoadRequest();

    Promise.all([
        client.getProject(projectSlug),
        client.listTranslatableTerms(projectSlug, props.dialogueName),
    ])
    .then(([project, extractedTerms]) => {
        if (!isCurrentLoadRequest(requestId)) return;
        // This editor is only ever reachable in Authoring Mode (see DialogueWorkspace.vue's
        // availableModes) and always edits draft translation content — it must offer the draft
        // language registry, not the published one, so a language just added/removed via
        // Configure Project shows up here immediately rather than only after a publish.
        const languages = (project.draftTranslationLanguages ?? [])
            .filter((t) => !t.isDeleted)
            .map((t) => ({
                code: t.translationLanguageCode,
                name: t.translationLanguageName,
            }));
        translationLanguages.value = languages;
        groups.value = groupedTerms(extractedTerms);
        terms.value = extractedTerms;

        // Keep the current column selections if they're still valid (e.g. just switching tabs
        // within the same project); only fall back to defaults if a language disappeared or
        // nothing was picked yet. Column 3 defaults to hidden — the whole point is to save space.
        if (!languages.some((l) => l.code === column2Language.value)) {
            column2Language.value = languages[0]?.code ?? null;
        }
        if (column3Language.value && !languages.some((l) => l.code === column3Language.value)) {
            column3Language.value = null;
        }

        if (languages.length === 0) {
            loading.value = false;
            return;
        }

        Promise.all(languages.map((lang) =>
            client.getDraftTranslation(projectSlug, props.dialogueName, lang.code)
                .then((translation) => [lang.code, translation])))
        .then((results) => {
            if (!isCurrentLoadRequest(requestId)) return;
            const next = {};
            for (const [code, translation] of results) {
                next[code] = translation?.content ? JSON.parse(translation.content) : {};
            }
            content.value = next;
        })
        .catch((error) => {
            if (!isCurrentLoadRequest(requestId)) return;
            showError(describeError(error));
        })
        .finally(() => {
            if (!isCurrentLoadRequest(requestId)) return;
            loading.value = false;
        });
    })
    .catch((error) => {
        if (!isCurrentLoadRequest(requestId)) return;
        loading.value = false;
        // A structured "errors" field (see ProjectParseHttpError.java) means this dialogue's draft
        // content currently fails to parse — shown inline with an expandable details list, since it
        // needs to stick around for the user to read rather than vanish with a toast.
        if (error?.errors) {
            loadError.value = { message: error.message, errors: error.errors };
        } else {
            showError(describeError(error));
        }
    });
}

watch(() => props.dialogueName, loadTranslations, { immediate: true });

defineExpose({
    reload: loadTranslations,
});
</script>

<template>
    <div class="relative h-full overflow-auto">
        <StaleTranslationsModal
            v-if="showStale"
            :sections="staleSections"
            :saving="deletingStale"
            @close="showStale = false"
            @delete="deleteStaleEntries"
        />
        <div v-if="!dialogueName" class="flex items-center justify-center h-full text-grey-dark font-title text-sm p-2">
            Open a dialogue from the Dialogue Browser to start translating.
        </div>
        <div v-else-if="loadError" class="p-3 font-title text-sm">
            <div class="flex items-start gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-lg px-3 py-2.5">
                <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                <div>
                    <div class="font-semibold">{{ loadError.message }}</div>
                    <ul class="mt-1 list-disc list-inside">
                        <li v-for="(messages, dialogue) in loadError.errors" :key="dialogue">
                            <span class="font-mono">{{ dialogue }}</span>:
                            <span v-for="(m, i) in messages" :key="i">{{ m }}<template v-if="i < messages.length - 1">; </template></span>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
        <div v-else-if="loading && groups.length === 0" class="flex items-center justify-center h-full text-grey-dark font-title text-sm gap-2 p-2">
            <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin" />
            Loading translations…
        </div>
        <div v-else-if="translationLanguages.length === 0" class="flex items-center justify-center h-full text-grey-dark font-title text-sm text-center px-8">
            This project has no translation languages configured yet — add one from the project menu's "Configure Project".
        </div>
        <div v-else class="flex flex-col">
            <!-- Sits flush against the scroll container's own edges (no padding on that container,
                 see above) so this sticky header has no transparent margin for scrolled content to
                 peek through above it. -->
            <div class="sticky top-0 z-10 flex items-center gap-4 bg-white px-2 py-2 border-b border-grey-light">
                <div class="flex items-center gap-1.5">
                    <span class="font-title text-[10px] text-grey-dark uppercase tracking-wide">Column 2</span>
                    <select
                        v-model="column2Language"
                        class="h-7 px-1.5 border border-grey-light rounded text-xs font-title focus:outline-none focus:border-orange-dark bg-white cursor-pointer"
                    >
                        <option v-for="lang in translationLanguages" :key="lang.code" :value="lang.code">{{ lang.name }}</option>
                    </select>
                </div>
                <div v-if="translationLanguages.length > 1" class="flex items-center gap-1.5">
                    <span class="font-title text-[10px] text-grey-dark uppercase tracking-wide">Column 3</span>
                    <select
                        v-model="column3Language"
                        class="h-7 px-1.5 border border-grey-light rounded text-xs font-title focus:outline-none focus:border-orange-dark bg-white cursor-pointer"
                    >
                        <option :value="null">Hidden</option>
                        <option v-for="lang in column3Options" :key="lang.code" :value="lang.code">{{ lang.name }}</option>
                    </select>
                </div>
                <button
                    v-if="staleCount > 0"
                    type="button"
                    class="ml-auto flex items-center gap-1.5 h-7 px-2 rounded border border-orange-medium/60 bg-orange-light/20 text-xs font-title text-orange-darker hover:bg-orange-light/40 cursor-pointer"
                    title="Translation entries that match no text of this dialogue"
                    data-test="stale-entries-button"
                    @click="showStale = true"
                >
                    <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" />
                    Stale entries ({{ staleCount }})
                </button>
            </div>
            <div class="flex flex-col gap-4 p-2">
                <div v-for="group in groups" :key="group.speaker" class="flex flex-col gap-1">
                    <div class="font-title font-black text-xs uppercase tracking-wide text-orange-darker">{{ group.speaker }}</div>
                    <div v-for="term in group.terms" :key="term" class="flex flex-col gap-1 bg-grey-lighter rounded p-2">
                        <div v-for="contexts in [[], ...contextVariants(group.speaker, term)]" :key="contexts.join(' ')"
                            class="grid gap-2 items-start"
                            :data-test="contexts.length ? 'context-row' : 'term-row'"
                            :style="{ gridTemplateColumns: `minmax(160px, 1fr) repeat(${visibleColumns.length}, minmax(160px, 1fr))` }">
                            <div v-if="contexts.length === 0" class="font-title text-xs text-grey-dark self-center pt-1.5">{{ term }}</div>
                            <div v-else class="font-title text-[11px] text-orange-darker self-center pl-3" title="A gender-specific translation of the text above">
                                ↳ {{ contexts.join(', ') }}
                            </div>
                            <div v-for="lang in visibleColumns" :key="lang.code" class="flex items-center gap-1">
                                <input
                                    type="text"
                                    :value="cellValue(group.speaker, term, lang.code, contexts)"
                                    :title="lang.name"
                                    class="flex-1 min-w-0 font-title text-xs px-1.5 py-1 border border-grey-light rounded bg-white focus:outline-none focus:border-orange-dark"
                                    @input="onCellInput(group.speaker, term, lang.code, $event, contexts)"
                                    @keyup.enter="submitCell(group.speaker, term, lang.code, contexts)"
                                    @blur="submitCell(group.speaker, term, lang.code, contexts)"
                                />
                                <FontAwesomeIcon v-if="savingCells.has(cellKey(group.speaker, term, lang.code, contexts))"
                                    icon="fa-solid fa-circle-notch" class="animate-spin text-grey-dark w-3" />
                                <FontAwesomeIcon v-else-if="dirtyCells.has(cellKey(group.speaker, term, lang.code, contexts))"
                                    icon="fa-solid fa-circle" class="text-orange-medium w-2" title="Unsaved change" />
                            </div>
                        </div>
                    </div>
                </div>
                <div v-if="groups.length === 0" class="text-grey-dark font-title text-sm text-center py-8">
                    This dialogue has no translatable text yet.
                </div>
            </div>
        </div>
    </div>
</template>
