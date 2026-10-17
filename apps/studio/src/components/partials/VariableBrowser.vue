<script>
export default { inheritAttrs: false };
</script>

<script setup>
import { computed, inject, onMounted, ref, useAttrs, useTemplateRef, watch } from 'vue';
const attrs = useAttrs();
const state = inject('state');
import { useClient, useAuthoringClient, getBrowserTimeZone } from '@/composables/client.js';
import { DLB_APP_MODE_DRAFT } from '@/StudioClientState.js';
import { logEvent } from '@/composables/debug-log.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { useLatestRequest } from '@/composables/latest-request.js';
import {
    HISTORY_PAGE_SIZE, sourceLabel, formatTimestamp, formatHistoryValue, mergeHistoryPage,
    mayHaveMore, resolveExactName, filterSortHistory, needsLoadedOnlyHint,
    HISTORY_PRESETS, presetRange, resolveDateRange,
} from '@/composables/variable-history.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';
import IconButton from '../widgets/IconButton.vue';
import ToolbarButton from '../widgets/ToolbarButton.vue';
import ContextMenu from '../widgets/ContextMenu.vue';

// Times are always shown in the time zone of the person using Studio (#323) — the same one sent to
// the API — not the zone a value was recorded in. The long time style includes the zone's name,
// so the tooltip makes that explicit.
function formatUpdatedTime(variable) {
    return formatTimestamp(variable.updatedTime || null, getBrowserTimeZone(),
        { dateStyle: 'medium', timeStyle: 'long' }) ?? 'Unknown';
}

function formatUpdatedSource(variable) {
    return sourceLabel(variable.updatedSource);
}

function formatHistoryTime(entry) {
    return formatTimestamp(entry.recordedTime, getBrowserTimeZone());
}

const tooltip = ref(null);   // { variable, x, y }

function showTooltip(event, variable) {
    const rect = event.currentTarget.getBoundingClientRect();
    tooltip.value = {
        variable,
        x: rect.left - 8,
        y: rect.top + rect.height / 2,
    };
}

function hideTooltip() {
    tooltip.value = null;
}

const emit = defineEmits([
    'changeVariable',
    'collapse',
]);

const client = useClient();
const authoringClient = useAuthoringClient();

// Three distinct things, one panel, one at a time (see `mode`):
//   'values'  — this logged-in user's stored variable values for the current project; mutable
//               runtime state. Variables are project-scoped (#86). In Authoring Mode, the copy
//               of them that test sessions run against instead (#319): see `testing`.
//   'project' — every variable name the project's dialogues reference (read/written);
//               static authoring info, read-only (GET /variables/list-project).
//   'history' — every recorded change to this user's variables in the project, newest first and
//               paged (GET /variables/history, #323). Only loaded while this mode is showing.
const mode = ref('values');
// In Authoring Mode, and while simulating a user (#411), dialogues run as test sessions against a
// copy of the variables (#319), so "Values" shows and edits that copy; the stored values (and
// their history) stay untouched.
const testing = computed(() => state.value.mode === DLB_APP_MODE_DRAFT
    || state.value.delegate?.mode === 'simulate');
const variables = ref([]);
const projectVariables = ref([]); // [{ name, read, written }]
const supportedVariables = ref([]); // [{ name, description }] — from the configured EVS, if any

const searchQuery = ref('');
const sortMode = ref('name-asc');
const isSearching = computed(() => searchQuery.value.trim().length > 0);

function filterSort(list) {
    const needle = searchQuery.value.trim().toLowerCase();
    const filtered = needle ? list.filter((v) => v.name.toLowerCase().includes(needle)) : list;
    const dir = sortMode.value === 'name-desc' ? -1 : 1;
    return [...filtered].sort((a, b) => dir * a.name.localeCompare(b.name));
}

// 'values' mode. Rows keep their original variable objects, so editing/deleting is unaffected.
const displayedVariables = computed(() => filterSort(variables.value));
// 'project' mode — every variable the project's dialogues reference, merged with every variable
// the configured EVS reports as supported (a variable may be either, both, or (transiently, while
// typed but not yet used) neither). A name present only on the EVS side gets read: false,
// written: false — it's still a real row, just not yet referenced by any dialogue.
const mergedProjectVariables = computed(() => {
    const byName = new Map();
    for (const v of projectVariables.value)
        byName.set(v.name, { name: v.name, read: v.read, written: v.written, evsSupported: false });
    for (const v of supportedVariables.value) {
        const existing = byName.get(v.name);
        if (existing) existing.evsSupported = true;
        else byName.set(v.name, { name: v.name, read: false, written: false, evsSupported: true });
    }
    return [...byName.values()];
});
const displayedProjectVariables = computed(() => filterSort(mergedProjectVariables.value));
// Names the current user has a stored value for — used to subtly dim the not-yet-set entries in
// the 'project' list.
const storedNames = computed(() => new Set(variables.value.map((v) => v.name)));

// -------------------- 'history' mode -------------------- //

const historyEntries = ref([]);      // loaded so far, newest first, de-duplicated by id
const historyPagesLoaded = ref(0);
const historyHasMore = ref(false);
const historyLoading = ref(false);
const historyLoadingMore = ref(false);
const historyOrder = ref('newest');  // 'newest' | 'oldest' — History has its own sort (#323)

// Sorting is a small menu behind a toolbar button: History's order, or the other modes' name sort.
const sortButton = useTemplateRef('sortButton');
const sortMenuOpen = ref(false);
const SORT_CHOICES = {
    history: [{ value: 'newest', label: 'Newest first' }, { value: 'oldest', label: 'Oldest first' }],
    names: [{ value: 'name-asc', label: 'Name (A–Z)' }, { value: 'name-desc', label: 'Name (Z–A)' }],
};
const sortChoices = computed(() => mode.value === 'history' ? SORT_CHOICES.history : SORT_CHOICES.names);
const currentSort = computed(() => mode.value === 'history' ? historyOrder.value : sortMode.value);
const sortLabel = computed(() => sortChoices.value.find((c) => c.value === currentSort.value)?.label ?? '');
const sortMenuItems = computed(() => sortChoices.value.map((c) => ({
    label: c.label, value: c.value, checked: c.value === currentSort.value, test: 'sort-' + c.value,
})));
function chooseSort(item) {
    if (mode.value === 'history') historyOrder.value = item.value;
    else sortMode.value = item.value;
}

// The start / end date range (#371): <input type="date"> values, whole days in the Studio user's
// time zone — the zone the entries are displayed in. The end date is included in full; the request
// carries epoch milliseconds (start's midnight up to the midnight after the end date). An invalid
// range (start after end) is reported in the panel and never sent.
const historyStartDate = ref('');
const historyEndDate = ref('');
const historyRange = computed(() =>
    resolveDateRange(historyStartDate.value, historyEndDate.value, getBrowserTimeZone()));
const historyRangeSet = computed(() => !!(historyStartDate.value || historyEndDate.value));
// A 400 from the server is shown in the panel too, rather than as a generic error toast.
const historyServerError = ref(null);
const historyRangeError = computed(() => historyRange.value.error ?? historyServerError.value);

function applyHistoryPreset(presetId) {
    const range = presetRange(presetId, Date.now(), getBrowserTimeZone());
    historyStartDate.value = range.start;
    historyEndDate.value = range.end;
}

function clearHistoryRange() {
    historyStartDate.value = '';
    historyEndDate.value = '';
}

// Every variable name Studio knows of for this project — what the search box's text is matched
// against to decide whether it names one variable exactly (asked of the server) or not (filtered
// in the browser). "Used" names need an editor role; for others that list is simply empty.
const knownNames = computed(() => new Set([
    ...variables.value.map((v) => v.name),
    ...mergedProjectVariables.value.map((v) => v.name),
    ...historyEntries.value.map((e) => e.name),
]));
const historyExactName = computed(() =>
    mode.value === 'history' ? resolveExactName(searchQuery.value, knownNames.value) : null);

const displayedHistory = computed(() => historyRange.value.error ? [] : filterSortHistory(historyEntries.value, {
    query: searchQuery.value, exactName: historyExactName.value, order: historyOrder.value,
}));
const showLoadedOnlyHint = computed(() => needsLoadedOnlyHint({
    query: searchQuery.value, exactName: historyExactName.value, order: historyOrder.value,
    hasMore: historyHasMore.value,
}));

// Bumped by every fresh (page 0) load. Each request remembers the generation it belongs to and
// drops its result if a newer load has started since — so neither a slow refresh nor a slow "Load
// more" can land on top of newer data. (A plain useLatestRequest() isn't enough here: a "Load more"
// must not cancel the refresh it builds on, only be cancelled by the next one.)
let historyGeneration = 0;

function historyRequest(page) {
    return client.getVariableHistory({
        projectSlug: state.value.selectedProject?.slug,
        variableName: historyExactName.value ?? undefined,
        from: historyRange.value.from,
        to: historyRange.value.to,
        page,
        pageSize: HISTORY_PAGE_SIZE,
    });
}

// Always starts over from the first page (#323): new entries appear at the top anyway, and
// re-requesting every page already shown would mean merging pages that have since shifted.
const loadHistory = () => {
    const generation = ++historyGeneration;
    historyLoadingMore.value = false;
    historyServerError.value = null;
    if (!state.value.selectedProject?.slug || historyRange.value.error) {
        historyEntries.value = [];
        historyPagesLoaded.value = 0;
        historyHasMore.value = false;
        return;
    }
    historyLoading.value = true;
    historyRequest(0)
    .then((page) => {
        if (generation !== historyGeneration) return;
        historyEntries.value = mergeHistoryPage([], page);
        historyPagesLoaded.value = 1;
        historyHasMore.value = mayHaveMore(page, HISTORY_PAGE_SIZE);
    })
    .catch((error) => {
        if (generation !== historyGeneration) return;
        if (error?.status === 400) historyServerError.value = describeError(error);
        else showError(describeError(error));
    })
    .finally(() => {
        if (generation === historyGeneration) historyLoading.value = false;
    });
};

function loadMoreHistory() {
    if (historyLoading.value || historyLoadingMore.value || !historyHasMore.value) return;
    const generation = historyGeneration;
    historyLoadingMore.value = true;
    historyRequest(historyPagesLoaded.value)
    .then((page) => {
        if (generation !== historyGeneration) return;
        historyEntries.value = mergeHistoryPage(historyEntries.value, page);
        historyPagesLoaded.value += 1;
        historyHasMore.value = mayHaveMore(page, HISTORY_PAGE_SIZE);
    })
    .catch((error) => {
        if (generation !== historyGeneration) return;
        showError(describeError(error));
    })
    .finally(() => {
        if (generation === historyGeneration) historyLoadingMore.value = false;
    });
}

// Clicking a variable's name in the history narrows it to that variable — which, being an exact
// known name, is then asked of the server.
function showOnlyVariable(name) {
    searchQuery.value = name;
}

// Load when History is opened, and start over whenever a server-side filter changes (typing a
// name in full, or editing it back to a partial one; or changing the time range). One watcher for
// all of them, since several can change at once (opening History with an exact name already in the
// search box; a preset setting From and clearing To) — one request, not several.
watch([mode, historyExactName, () => historyRange.value.from, () => historyRange.value.to,
    () => historyRange.value.error], ([newMode]) => {
    if (newMode === 'history') loadHistory();
});

// Live Mode shows the stored values, Authoring Mode the test copy: switching shows the other.
watch(testing, () => loadVariables());

const emptyForFilter = computed(() => {
    if (!isSearching.value) return false;
    if (mode.value === 'values') return displayedVariables.value.length === 0;
    if (mode.value === 'project') return displayedProjectVariables.value.length === 0;
    // History: only once nothing more could match — otherwise the loaded-only hint says it all.
    return displayedHistory.value.length === 0 && !historyHasMore.value && !historyLoading.value;
});

// Guards against out-of-order responses: loadVariables() can fire in quick succession (e.g. once
// per dialogue step while testing) with no guarantee the requests resolve in the order they were
// sent — only the response matching the most recently issued request is allowed to update state.
const { next: nextLoadRequest, isCurrent: isCurrentLoadRequest } = useLatestRequest();

const loadVariables = () => {
    dismissError();
    // Variables are project-scoped (#86): the "Values" list is this project's stored values only.
    const slug = state.value.selectedProject?.slug;
    if (!slug) {
        variables.value = [];
        return;
    }
    const requestId = nextLoadRequest();
    (testing.value
        ? authoringClient.getTestVariables({ projectSlug: slug, timeZone: getBrowserTimeZone() })
        : client.getVariables({ projectSlug: slug, timeZone: getBrowserTimeZone() }))
    .then((vars) => {
        if (!isCurrentLoadRequest(requestId)) return;
        variables.value = vars;
        dirtyVariables.value = new Set();
        deletingVariables.value = new Set();
        tooltip.value = null;
    })
    .catch((error) => {
        if (!isCurrentLoadRequest(requestId)) return;
        showError(describeError(error));
    });
    // MainPage calls loadVariables() after every dialogue step, variable change and delegate-user
    // change — so hooking in here keeps the history current in all of those cases, without asking
    // for it at all while History isn't showing.
    if (mode.value === 'history') loadHistory();
};

// The project's variable set doesn't change during a test session, so this stays out of
// loadVariables() (which the workspace calls every step). Best-effort: a failure just leaves the
// "Used in project" list empty.
const loadProjectVariables = () => {
    const slug = state.value.selectedProject?.slug;
    if (!slug) return;
    authoringClient.listProjectVariables(slug)
        .then((vars) => { projectVariables.value = vars; })
        .catch(() => { projectVariables.value = []; });
};

// Best-effort, same as loadProjectVariables(): most deployments won't have an EVS configured at
// all, which surfaces as a rejected request here, not a special "empty" response — that's expected,
// not an error, so it's swallowed the same way rather than shown to the user.
const loadSupportedVariables = () => {
    const slug = state.value.selectedProject?.slug;
    if (!slug) return;
    authoringClient.listSupportedVariables(slug)
        .then((vars) => { supportedVariables.value = vars; })
        .catch(() => { supportedVariables.value = []; });
};

function refresh() {
    loadVariables();
    loadProjectVariables();
    loadSupportedVariables();
}

function copyName(name) {
    navigator.clipboard?.writeText('$' + name).catch(() => {});
}

defineExpose({
    loadVariables,
});

// Sets (or, with a null value, removes) a variable: the stored one, or in Authoring Mode the test
// copy's.
function storeVariable(name, value) {
    const options = {
        variableName: name,
        variableValue: value,
        projectSlug: state.value.selectedProject?.slug,
        timeZone: getBrowserTimeZone(),
    };
    return testing.value ? authoringClient.setTestVariable(options) : client.setVariable(options);
}

function resetTestVariables() {
    dismissError();
    logEvent('variable', 'Test variables reset');
    authoringClient.resetTestVariables({ projectSlug: state.value.selectedProject?.slug })
        .then(() => loadVariables())
        .catch((error) => showError(describeError(error)));
}

function deleteVariable(name) {
    confirmingDelete.value = null;
    dismissError();
    logEvent('variable', 'Variable $1 deleted', name);
    const next = new Set(deletingVariables.value);
    next.add(name);
    deletingVariables.value = next;
    storeVariable(name, null)
    .then(() => {
        emit('changeVariable');
        return loadVariables();
    })
    .catch((error) => {
        const reverted = new Set(deletingVariables.value);
        reverted.delete(name);
        deletingVariables.value = reverted;
        showError(describeError(error));
    });
}

const dirtyVariables = ref(new Set());
const confirmingDelete = ref(null);
const deletingVariables = ref(new Set());

function onVariableInput(variable) {
    dirtyVariables.value = new Set([...dirtyVariables.value, variable.name]);
}

function submitVariable(variable) {
    if (!dirtyVariables.value.has(variable.name)) return;
    dismissError();
    logEvent('variable', 'Variable $1 updated to $2', variable.name, variable.value);
    storeVariable(variable.name, variable.value)
    .then(() => {
        const next = new Set(dirtyVariables.value);
        next.delete(variable.name);
        dirtyVariables.value = next;
        emit('changeVariable');
    })
    .catch((error) => {
        showError(describeError(error));
    });
}

onMounted(() => {
    refresh();
});
</script>

<template>
<div class="flex flex-col gap-1" v-bind="attrs">
        <MainPagePanelHeader title="Variable Browser" icon="fa-solid fa-sliders" class="sm:mr-1" />

        <MainPagePanelContainer class="sm:mr-1">
            <template #toolbar>
                <!-- What the panel shows, and collapsing it -->
                <div class="flex items-center gap-1.5 px-1 h-[29px]">
                    <div class="flex flex-1 min-w-0 rounded-lg overflow-hidden border border-grey-light font-title text-[10px] font-semibold">
                        <button
                            type="button"
                            class="flex-1 px-2 h-[22px] cursor-pointer"
                            :class="mode === 'values' ? 'bg-orange-dark text-white' : 'bg-white text-grey-dark hover:bg-grey-lighter'"
                            :title="testing ? 'The test variables: a copy of this user\'s stored values that tests run against' : 'This user\'s stored variable values for the current project'"
                            @click="mode = 'values'"
                        >Values</button>
                        <button
                            type="button"
                            class="flex-1 px-2 h-[22px] cursor-pointer border-l border-grey-light"
                            :class="mode === 'project' ? 'bg-orange-dark text-white' : 'bg-white text-grey-dark hover:bg-grey-lighter'"
                            title="Every variable used anywhere in this project, plus any the configured External Variable Service supports (read-only)"
                            @click="mode = 'project'"
                        >Used</button>
                        <button
                            type="button"
                            class="flex-1 px-2 h-[22px] cursor-pointer border-l border-grey-light"
                            :class="mode === 'history' ? 'bg-orange-dark text-white' : 'bg-white text-grey-dark hover:bg-grey-lighter'"
                            title="Every recorded change to this user's variables in the current project, newest first"
                            @click="mode = 'history'"
                        >History</button>
                    </div>
                    <ToolbarButton icon="fa-solid fa-angles-right" title="Collapse Variable Browser" @click="emit('collapse')" />
                </div>
                <!-- Filter + sort strip -->
                <div class="flex items-center gap-1.5 px-1 h-[29px]">
                    <div class="relative flex-1 min-w-0">
                        <FontAwesomeIcon icon="fa-solid fa-magnifying-glass" class="absolute left-2 top-1/2 -translate-y-1/2 text-grey-dark text-[10px] pointer-events-none" />
                        <input
                            v-model="searchQuery"
                            type="text"
                            placeholder="Filter variables…"
                            class="w-full h-6 pl-6 pr-6 text-xs font-title border border-grey-light rounded-lg bg-white focus:outline-none focus:border-orange-dark"
                            @keyup.esc="searchQuery = ''"
                        />
                        <button
                            v-if="searchQuery"
                            type="button"
                            title="Clear filter"
                            class="absolute right-1.5 top-1/2 -translate-y-1/2 text-grey-dark hover:text-orange-dark cursor-pointer"
                            @click="searchQuery = ''"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-xmark" class="text-[10px]" />
                        </button>
                    </div>
                    <!-- History keeps its own sort, so switching modes never carries one's order to the other -->
                    <ToolbarButton
                        ref="sortButton"
                        icon="fa-solid fa-arrow-down-wide-short"
                        :title="(mode === 'history' ? 'Sort changes: ' : 'Sort variables: ') + sortLabel"
                        data-test="sort-variables"
                        @click="sortMenuOpen = true"
                    />
                    <ContextMenu
                        v-if="sortMenuOpen"
                        :items="sortMenuItems"
                        :anchor="sortButton.$el"
                        @select="chooseSort"
                        @close="sortMenuOpen = false"
                    />
                    <ToolbarButton icon="fa-solid fa-arrows-rotate" title="Refresh variables" @click="refresh" />
                </div>

                <!-- Date range (#371) — History only. Whole days in the Studio user's time zone; the end date is included. -->
                <div v-if="mode === 'history'" data-test="history-range" class="flex flex-col gap-1 px-1 pb-1.5 font-title text-[10px] text-grey-dark">
                    <label class="flex items-center gap-1 min-w-0">
                        <span class="w-14 shrink-0">Start date</span>
                        <input v-model="historyStartDate" type="date" data-test="history-start-date"
                            title="Only changes recorded on or after this day (your time zone)"
                            class="min-w-0 flex-1 h-6 px-1 text-[10px] border border-grey-light rounded-lg bg-white focus:outline-none focus:border-orange-dark" />
                    </label>
                    <label class="flex items-center gap-1 min-w-0">
                        <span class="w-14 shrink-0">End date</span>
                        <input v-model="historyEndDate" type="date" data-test="history-end-date"
                            title="Only changes recorded on or before this day, the whole day (your time zone)"
                            class="min-w-0 flex-1 h-6 px-1 text-[10px] border border-grey-light rounded-lg bg-white focus:outline-none focus:border-orange-dark" />
                    </label>
                    <div class="flex flex-wrap items-center gap-1">
                        <button v-for="preset in HISTORY_PRESETS" :key="preset.id" type="button"
                            :data-test="'history-preset-' + preset.id"
                            class="px-1.5 h-5 rounded border border-grey-light bg-white text-grey-dark hover:bg-grey-lighter cursor-pointer"
                            @click="applyHistoryPreset(preset.id)">{{ preset.label }}</button>
                        <button v-if="historyRangeSet" type="button" data-test="history-range-clear"
                            class="px-1.5 h-5 rounded border border-grey-light bg-white text-orange-darker hover:bg-grey-lighter cursor-pointer"
                            @click="clearHistoryRange">Clear</button>
                    </div>
                    <div v-if="historyRangeSet && !historyRangeError" data-test="history-range-note" class="italic">
                        Showing changes recorded {{ historyStartDate ? 'from ' + historyStartDate : 'up' }}
                        {{ historyEndDate ? (historyStartDate ? 'through ' : 'to and including ') + historyEndDate : 'until now' }}.
                    </div>
                    <div v-if="historyRangeError" data-test="history-range-error" class="text-red-500">{{ historyRangeError }}</div>
                </div>
            </template>
            <!-- In Authoring Mode, Values is the test copy (#319) -->
            <div v-if="testing && mode !== 'project'" data-test="test-variables-note" class="flex items-start gap-1.5 px-2 py-1.5 bg-grey-lighter font-title text-[10px] text-grey-dark">
                <FontAwesomeIcon icon="fa-solid fa-flask" class="mt-0.5 shrink-0" />
                <span v-if="mode === 'values'" class="flex-1">
                    Test variables: tests run against this copy. Changes here don't affect the stored values.
                    <button type="button" data-test="reset-test-variables" class="underline hover:text-orange-dark cursor-pointer" @click="resetTestVariables">Reset to stored values</button>
                </span>
                <span v-else class="flex-1">The stored history: changes made in tests aren't recorded.</span>
            </div>
            <div v-if="emptyForFilter" class="font-title text-xs italic text-grey-dark p-2">
                No variables match “{{ searchQuery.trim() }}”.
            </div>
            <div v-else-if="mode === 'project' && displayedProjectVariables.length === 0" class="font-title text-xs italic text-grey-dark p-2">
                This project's dialogues don't reference any variables.
            </div>

            <!-- 'project' mode — read-only reference list of every variable the project uses.
                 Names the current user has a value for show solid; ones with no value yet are
                 slightly dimmed. -->
            <div v-if="mode === 'project'" class="flex flex-col gap-0.5 m-1">
                <div v-for="v in displayedProjectVariables" :key="v.name"
                    class="group flex items-center bg-grey-lighter px-1 py-0.5 gap-2"
                    :class="{ 'opacity-60': !storedNames.has(v.name) }"
                    :title="storedNames.has(v.name) ? 'You have a value for this' : 'No value set for you yet'">
                    <div class="font-title font-semibold text-xs text-orange-darker min-w-0 truncate grow">${{ v.name }}</div>
                    <FontAwesomeIcon v-if="v.evsSupported" icon="fa-solid fa-satellite-dish"
                        class="shrink-0 text-[10px] text-grey-dark"
                        title="Reported as supported by the configured External Variable Service" />
                    <button type="button" title="Copy name"
                        class="shrink-0 w-5 h-5 flex items-center justify-center text-grey-dark hover:text-orange-dark cursor-pointer opacity-0 group-hover:opacity-100"
                        @click="copyName(v.name)">
                        <FontAwesomeIcon icon="fa-regular fa-copy" />
                    </button>
                </div>
            </div>

            <!-- 'history' mode — every recorded change, newest first (or oldest first, among the
                 entries loaded so far), paged with "Load more" -->
            <div v-else-if="mode === 'history'" class="flex flex-col gap-0.5 m-1">
                <div v-if="showLoadedOnlyHint" data-test="history-loaded-only-hint"
                    class="font-title text-[10px] italic text-grey-dark px-1 pb-0.5">
                    Showing {{ isSearching ? 'matches in ' : '' }}loaded entries only — load more to {{ isSearching ? 'search' : 'look' }} further back.
                </div>
                <div v-if="!historyLoading && !historyRangeError && historyEntries.length === 0 && !isSearching"
                    class="font-title text-xs italic text-grey-dark p-1">
                    {{ historyRangeSet ? 'No variable changes recorded in this time range.' : 'No variable changes recorded yet.' }}
                </div>
                <div v-for="entry in displayedHistory" :key="entry.id" data-test="history-entry"
                    class="flex flex-col min-w-0 bg-grey-lighter px-1 py-0.5">
                    <!-- The panel is narrow: name and value share the first line (both truncating,
                         full text on hover), time and source the second. -->
                    <div class="flex items-baseline gap-1.5 min-w-0">
                        <button type="button" data-test="history-entry-name"
                            class="font-title font-semibold text-xs text-orange-darker max-w-[60%] truncate shrink-0 cursor-pointer hover:underline text-left"
                            :title="'Show only $' + entry.name + '\'s history'"
                            @click="showOnlyVariable(entry.name)">${{ entry.name }}</button>
                        <span v-if="formatHistoryValue(entry.value) === null" data-test="history-entry-value"
                            class="font-title text-xs italic text-grey-dark min-w-0 truncate">removed</span>
                        <span v-else data-test="history-entry-value"
                            class="font-title text-xs text-grey-darker min-w-0 truncate"
                            :title="formatHistoryValue(entry.value)">{{ formatHistoryValue(entry.value) }}</span>
                    </div>
                    <div class="flex items-center justify-between gap-1.5 min-w-0">
                        <span data-test="history-entry-time" class="font-title text-[10px] text-grey-dark truncate">{{ formatHistoryTime(entry) }}</span>
                        <span data-test="history-entry-source"
                            class="shrink-0 rounded px-1 font-title text-[10px] bg-white border border-grey-light text-grey-dark"
                            :class="{ italic: sourceLabel(entry.source).italic }">{{ sourceLabel(entry.source).label }}</span>
                    </div>
                </div>
                <button v-if="historyHasMore" type="button" data-test="history-load-more"
                    class="mt-1 self-center font-title text-xs px-3 h-7 rounded-lg border border-grey-light bg-white text-orange-darker hover:bg-grey-lighter cursor-pointer disabled:cursor-default disabled:text-grey-dark"
                    :disabled="historyLoading || historyLoadingMore"
                    @click="loadMoreHistory">{{ historyLoadingMore ? 'Loading…' : 'Load more' }}</button>
            </div>

            <!-- 'values' mode — this user's stored variable values, editable -->
            <TransitionGroup v-else tag="div" name="fade" class="flex flex-col gap-0.5 m-1 overflow-hidden flex flex-col">
                <div v-for="variable in displayedVariables" :key="variable.name" class="flex items-center bg-grey-lighter px-1 py-0.5 gap-1"
                    :class="{ 'opacity-0 transition-opacity duration-500': deletingVariables.has(variable.name) }">
                    <div class="font-title font-semibold text-xs text-orange-darker shrink-0">${{ variable.name }}</div>
                    <input type="text" class="font-title text-xs px-1 py-0.5 min-w-0 grow border border-grey-light rounded bg-white focus:outline-none focus:border-orange-dark" v-model="variable.value" @input="onVariableInput(variable)" @keyup.enter="submitVariable(variable)"></input>
                    <button type="button" class="w-5 h-5 flex items-center justify-center rounded"
                        :class="dirtyVariables.has(variable.name) ? 'text-orange-darker hover:text-orange-dark cursor-pointer' : 'text-grey-light cursor-not-allowed'"
                        :disabled="!dirtyVariables.has(variable.name)"
                        @click.stop="submitVariable(variable)">
                        <FontAwesomeIcon icon="fa-solid fa-cloud-arrow-up" />
                    </button>
                    <div class="flex items-center gap-1">
                        <button class="w-5 h-5 flex items-center justify-center text-orange-darker hover:text-orange-dark cursor-pointer"
                            @mouseenter="showTooltip($event, variable)"
                            @mouseleave="hideTooltip">
                            <FontAwesomeIcon icon="fa-solid fa-circle-info" />
                        </button>
                        <template v-if="confirmingDelete === variable.name">
                            <span class="font-title text-xs text-grey-dark whitespace-nowrap">Delete?</span>
                            <button type="button" class="w-5 h-5 flex items-center justify-center text-icon-button-warning hover:text-icon-button-warning-hover cursor-pointer" title="Confirm delete" @click.stop="deleteVariable(variable.name)">
                                <FontAwesomeIcon icon="fa-solid fa-check" />
                            </button>
                            <button type="button" class="w-5 h-5 flex items-center justify-center text-orange-darker hover:text-orange-dark cursor-pointer" title="Cancel" @click.stop="confirmingDelete = null">
                                <FontAwesomeIcon icon="fa-solid fa-xmark" />
                            </button>
                        </template>
                        <IconButton v-else type="list-item" icon="fa-solid fa-trash" color="warning" @click.stop="confirmingDelete = variable.name" />
                    </div>
                </div>
            </TransitionGroup>
        </MainPagePanelContainer>
</div>

<Teleport to="body">
        <div v-if="tooltip"
            class="fixed z-[9999] w-max bg-white border border-grey-light shadow-md rounded p-2 text-xs font-title text-grey-dark pointer-events-none -translate-x-full -translate-y-1/2"
            :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }">
            <div class="font-semibold text-orange-darker mb-1">Variable Info</div>
            <table class="whitespace-nowrap">
                <tbody>
                    <tr><td class="font-semibold pr-2 text-right">Updated:</td><td :class="{ italic: !tooltip.variable.updatedTime }">{{ formatUpdatedTime(tooltip.variable) }}</td></tr>
                    <tr><td class="font-semibold pr-2 text-right">Timezone:</td><td :class="{ italic: !tooltip.variable.updatedTimeZone }">{{ tooltip.variable.updatedTimeZone || 'Unknown' }}</td></tr>
                    <tr><td class="font-semibold pr-2 text-right">Source:</td><td :class="{ italic: formatUpdatedSource(tooltip.variable).italic }">{{ formatUpdatedSource(tooltip.variable).label }}</td></tr>
                </tbody>
            </table>
        </div>
</Teleport>
</template>

<style scoped>
.fade-leave-active {
    transition: opacity 0.5s ease;
}
.fade-leave-to {
    opacity: 0;
}
</style>
