<script>
export default { inheritAttrs: false };
</script>

<script setup>
import { computed, inject, onMounted, ref, useAttrs } from 'vue';
const attrs = useAttrs();
const state = inject('state');
import { useClient } from '@/composables/client.js';
import { logEvent } from '@/composables/debug-log.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { useLatestRequest } from '@/composables/latest-request.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import IconButton from '../widgets/IconButton.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';

function formatUpdatedTime(variable) {
    if (!variable.updatedTime) return 'Unknown';
    const date = new Date(variable.updatedTime);
    const tz = variable.updatedTimeZone || Intl.DateTimeFormat().resolvedOptions().timeZone;
    return date.toLocaleString(undefined, { timeZone: tz, dateStyle: 'medium', timeStyle: 'medium' });
}

const sourceLabels = {
    UNKNOWN:      { label: 'Unknown',          italic: true },
    DLB_SCRIPT:   { label: 'Script',           italic: false },
    INPUT_REPLY:  { label: 'User Input',       italic: false },
    WEB_SERVICE:  { label: 'Web Service',      italic: false },
    EXTERNAL:     { label: 'External Service', italic: false },
};

function formatUpdatedSource(variable) {
    return sourceLabels[variable.updatedSource] ?? { label: 'Unknown', italic: true };
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

// Two distinct things, one panel, one at a time (see `mode`):
//   'values'  — this logged-in user's stored variable values for the current project; mutable
//               runtime state. Variables are project-scoped (#86).
//   'project' — every variable name the project's dialogues reference (read/written);
//               static authoring info, read-only (GET /variables/list-project).
const mode = ref('values');
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

const emptyForFilter = computed(() => isSearching.value && (mode.value === 'values'
    ? displayedVariables.value.length === 0
    : displayedProjectVariables.value.length === 0));

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
    client.getVariables(slug)
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
};

// The project's variable set doesn't change during a test session, so this stays out of
// loadVariables() (which the workspace calls every step). Best-effort: a failure just leaves the
// "Used in project" list empty.
const loadProjectVariables = () => {
    const slug = state.value.selectedProject?.slug;
    if (!slug) return;
    client.listProjectVariables(slug)
        .then((vars) => { projectVariables.value = vars; })
        .catch(() => { projectVariables.value = []; });
};

// Best-effort, same as loadProjectVariables(): most deployments won't have an EVS configured at
// all, which surfaces as a rejected request here, not a special "empty" response — that's expected,
// not an error, so it's swallowed the same way rather than shown to the user.
const loadSupportedVariables = () => {
    const slug = state.value.selectedProject?.slug;
    if (!slug) return;
    client.listSupportedVariables(slug)
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

function deleteVariable(name) {
    confirmingDelete.value = null;
    dismissError();
    logEvent('variable', 'Variable $1 deleted', name);
    const next = new Set(deletingVariables.value);
    next.add(name);
    deletingVariables.value = next;
    client.setVariable(state.value.selectedProject?.slug, name, null)
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
    client.setVariable(state.value.selectedProject?.slug, variable.name, variable.value)
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
        <MainPagePanelHeader title="Variable Browser" class="sm:mr-1">
            <template #buttons>
                <div class="flex shrink-0 rounded-lg overflow-hidden border border-grey-light font-title text-[10px] font-semibold">
                    <button
                        type="button"
                        class="px-2 h-7.5 cursor-pointer"
                        :class="mode === 'values' ? 'bg-orange-dark text-white' : 'bg-white text-grey-dark hover:bg-grey-lighter'"
                        title="This user's stored variable values for the current project"
                        @click="mode = 'values'"
                    >Values</button>
                    <button
                        type="button"
                        class="px-2 h-7.5 cursor-pointer border-l border-grey-light"
                        :class="mode === 'project' ? 'bg-orange-dark text-white' : 'bg-white text-grey-dark hover:bg-grey-lighter'"
                        title="Every variable used anywhere in this project, plus any the configured External Variable Service supports (read-only)"
                        @click="mode = 'project'"
                    >Used</button>
                </div>
                <IconButton icon="fa-solid fa-arrows-rotate" title="Refresh variables" @click="refresh" />
                <IconButton icon="fa-solid fa-angles-right" title="Collapse Variable Browser" @click="emit('collapse')" />
            </template>
        </MainPagePanelHeader>

        <!-- Filter + sort strip -->
        <div class="flex items-center gap-1.5 px-1 sm:mr-1">
            <div class="relative flex-1 min-w-0">
                <FontAwesomeIcon icon="fa-solid fa-magnifying-glass" class="absolute left-2 top-1/2 -translate-y-1/2 text-grey-dark text-[10px] pointer-events-none" />
                <input
                    v-model="searchQuery"
                    type="text"
                    placeholder="Filter variables…"
                    class="w-full h-7.5 pl-6 pr-6 text-xs font-title border border-grey-light rounded-lg bg-white focus:outline-none focus:border-orange-dark"
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
            <select
                v-model="sortMode"
                title="Sort variables"
                class="h-7.5 px-2 shrink-0 border border-grey-light rounded-lg text-xs font-title focus:outline-none focus:border-orange-dark bg-white cursor-pointer"
            >
                <option value="name-asc">Name (A–Z)</option>
                <option value="name-desc">Name (Z–A)</option>
            </select>
        </div>

        <MainPagePanelContainer class="sm:mr-1">
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
