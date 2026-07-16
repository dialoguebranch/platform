<script>
export default { inheritAttrs: false };
</script>

<script setup>
import { computed, inject, ref, useAttrs } from 'vue';
const attrs = useAttrs();
import { useClient } from '../../composables/client.js';
import { describeError } from '../../composables/error-message.js';
import { showError, dismissError } from '../../composables/error-toast.js';
import { useLatestRequest } from '../../composables/latest-request.js';
import { DLB_APP_MODE_DRAFT } from '../../dlb-lib/WCTAClientState.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const state = inject('state');
import IconButton from '../widgets/IconButton.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';
import DialogueTreeNode from './DialogueTreeNode.vue';

const props = defineProps({
    openTabs: { type: Array, default: () => [] },
});

const emit = defineEmits([
    'resumeDialogue',
    'activateTab',
    'hasUnpublishedChanges',
    'openDialogue',
]);

const client = useClient();

const isDraftMode = computed(() => state.value.mode === DLB_APP_MODE_DRAFT);

const tree = ref([]);
const openFolders = ref({});
const ongoingConfirm = ref(null); // { dialogueName, loggedDialogueId, secondsSinceLastEngagement, alreadyOpenTabId? }
const cancelConfirm = ref(false);
const reloading = ref(false);

// entries: array of { name, isPublished, isNew, isChanged, isDeleted }
function buildTree(entries) {
    const root = {};
    for (const entry of entries) {
        const parts = entry.name.split('/');
        let node = root;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!node[parts[i]]) node[parts[i]] = { _children: {} };
            node = node[parts[i]]._children;
        }
        const leaf = parts[parts.length - 1];
        node[leaf] = {
            _file: entry.name,
            _isPublished: entry.isPublished,
            _isNew: entry.isNew,
            _isChanged: entry.isChanged,
            _isDeleted: entry.isDeleted,
        };
    }
    return root;
}

// Guards against out-of-order responses: listDialogues() is triggered from several independent
// places (manual refresh, publish, initial mount) with no guarantee they resolve in the order
// they were sent — only the response matching the most recently issued request may update state.
const { next: nextListRequest, isCurrent: isCurrentListRequest } = useLatestRequest();

// A snapshot of the last-displayed entries (order-independent), so a refresh that returns the
// exact same list — the common case — can leave openFolders alone instead of collapsing
// everything. Only a genuine change to the list (a dialogue added/removed/renamed, or its
// published/new/changed/deleted status flipping) resets it.
let previousEntriesKey = null;

function entriesKey(entries) {
    return JSON.stringify(
        [...entries]
            .sort((a, b) => a.name.localeCompare(b.name))
            .map((e) => [e.name, e.isPublished, e.isNew, e.isChanged, e.isDeleted])
    );
}

function listDialogues() {
    const projectSlug = state.value.selectedProject?.slug;
    dismissError();
    reloading.value = true;
    const requestId = nextListRequest();
    // The Dialogue Browser only ever shows one source at a time — published dialogues in Live
    // Mode, draft dialogues in Draft Mode — never a merge of both, so there's nothing to reconcile
    // by name across the two lists.
    const listPromise = isDraftMode.value
        ? client.listDraftDialogues(projectSlug).catch(() => [])
        : client.listDialogues(projectSlug).catch(() => ({ dialogueNames: [] }));
    listPromise
    .then((result) => {
        if (!isCurrentListRequest(requestId)) return;
        let entries;
        if (isDraftMode.value) {
            const drafts = result ?? [];
            // "Publish Project" should only be enabled when there's actually something to publish —
            // a dialogue that's new, changed, or pending deletion — not merely because a draft row
            // exists (it may already be perfectly in sync with what's published).
            emit('hasUnpublishedChanges', drafts.some((d) => d.isNew || d.isChanged || d.isDeleted));
            entries = drafts.map((d) => ({
                name: d.name,
                isPublished: !d.isNew,
                isNew: d.isNew,
                isChanged: d.isChanged,
                isDeleted: d.isDeleted,
            }));
        } else {
            entries = (result.dialogueNames ?? []).map((name) => ({
                name,
                isPublished: true,
                isNew: false,
                isChanged: false,
                isDeleted: false,
            }));
        }
        const newKey = entriesKey(entries);
        const listUnchanged = newKey === previousEntriesKey;
        previousEntriesKey = newKey;

        const root = buildTree(entries);
        tree.value = Object.entries(root).sort(([, a], [, b]) => {
            const aIsFolder = !a._file;
            const bIsFolder = !b._file;
            if (aIsFolder !== bIsFolder) return aIsFolder ? -1 : 1;
            return 0;
        });
        if (!listUnchanged) openFolders.value = {};
    })
    .catch((error) => {
        if (!isCurrentListRequest(requestId)) return;
        showError(describeError(error));
    })
    .finally(() => {
        if (!isCurrentListRequest(requestId)) return;
        // Same minimum-visible-spin treatment as DialogueWorkspace's "Refresh current dialogue
        // step" button — a fast response would otherwise just flicker, giving no indication
        // anything happened (the whole point of this animation, now that an unchanged list no
        // longer collapses the folder tree as a side effect).
        setTimeout(() => { reloading.value = false; }, 1000);
    });
}

function toggleFolder(path) {
    openFolders.value[path] = !openFolders.value[path];
}

// ---- New dialogue creation ----

const showNewDialogueInput = ref(false);
const newDialogueName = ref('');
const creatingDialogue = ref(false);

function onNewDialogueClick() {
    newDialogueName.value = '';
    showNewDialogueInput.value = true;
}

function cancelNewDialogue() {
    showNewDialogueInput.value = false;
}

function submitNewDialogue() {
    const name = newDialogueName.value.trim();
    if (!name || creatingDialogue.value) return;
    creatingDialogue.value = true;
    dismissError();
    client.createDraftDialogue(state.value.selectedProject?.slug, name)
    .then(() => {
        showNewDialogueInput.value = false;
        listDialogues();
    })
    .catch((error) => {
        showError(describeError(error));
    })
    .finally(() => {
        creatingDialogue.value = false;
    });
}

const hasActiveDialogue = computed(() =>
    props.openTabs.some(t => t.dialogueName && !t.dialogueEnded && !t.dialogueCancelled));

function checkOngoingDialogue() {
    dismissError();
    client.getOngoingDialogue(state.value.selectedProject?.slug)
    .then((ongoing) => {
        if (ongoing) {
            const alreadyOpenTab = props.openTabs.find(t =>
                t.loggedDialogueId === ongoing.loggedDialogueId);
            ongoingConfirm.value = {
                ...ongoing,
                alreadyOpenTabId: alreadyOpenTab?.id ?? null,
            };
        } else {
            ongoingConfirm.value = { dialogueName: null };
        }
    })
    .catch((error) => {
        showError(describeError(error));
    });
}

function confirmSwitchTab() {
    const tabId = ongoingConfirm.value.alreadyOpenTabId;
    ongoingConfirm.value = null;
    emit('activateTab', tabId);
}

function formatTimeSince(seconds) {
    if (seconds < 60) return `${seconds} second${seconds !== 1 ? 's' : ''}`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes} minute${minutes !== 1 ? 's' : ''}`;
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    if (remainingMinutes === 0) return `${hours} hour${hours !== 1 ? 's' : ''}`;
    return `${hours} hour${hours !== 1 ? 's' : ''} and ${remainingMinutes} minute${remainingMinutes !== 1 ? 's' : ''}`;
}

function confirmResume() {
    const name = ongoingConfirm.value.dialogueName;
    ongoingConfirm.value = null;
    emit('resumeDialogue', name);
}

function confirmCancel() {
    cancelConfirm.value = true;
}

function doCancel() {
    const id = ongoingConfirm.value.loggedDialogueId;
    cancelConfirm.value = false;
    ongoingConfirm.value = null;
    dismissError();
    client.cancelDialogue(id)
    .catch((error) => {
        showError(describeError(error));
    });
}

listDialogues();

defineExpose({
    listDialogues,
});
</script>

<template>
    <div class="flex flex-col gap-1" v-bind="attrs">
        <MainPagePanelHeader title="Dialogue Browser" class="sm:ml-2">
            <template #buttons>
                <IconButton v-if="!isDraftMode" icon="fa-solid fa-rotate-left" title="Retrieve most recent ongoing (server-side) dialogue" :disabled="hasActiveDialogue" @click="checkOngoingDialogue" />
                <IconButton v-if="isDraftMode" icon="fa-solid fa-plus" title="New Dialogue" @click="onNewDialogueClick" />
                <IconButton
                    icon="fa-solid fa-arrows-rotate"
                    :class="{ 'animate-spin': reloading }"
                    title="Refresh dialogue list"
                    :disabled="reloading"
                    @click="listDialogues"
                />
            </template>
        </MainPagePanelHeader>
        <MainPagePanelContainer class="p-1 gap-1 flex flex-col sm:ml-1">
            <div v-if="showNewDialogueInput" class="flex items-center gap-1 p-1">
                <input
                    v-model="newDialogueName"
                    type="text"
                    placeholder="folder/dialogue-name"
                    autofocus
                    class="flex-1 min-w-0 font-mono text-xs px-1.5 py-1 border border-grey-light rounded bg-white focus:outline-none focus:border-orange-dark"
                    @keyup.enter="submitNewDialogue"
                    @keyup.esc="cancelNewDialogue"
                />
                <button type="button" title="Create" class="shrink-0 text-icon-button hover:text-icon-button-hover cursor-pointer" :disabled="creatingDialogue" @click="submitNewDialogue">
                    <FontAwesomeIcon :icon="creatingDialogue ? 'fa-solid fa-circle-notch' : 'fa-solid fa-check'" :class="{ 'animate-spin': creatingDialogue }" />
                </button>
                <button type="button" title="Cancel" class="shrink-0 text-grey-dark hover:text-orange-dark cursor-pointer" @click="cancelNewDialogue">
                    <FontAwesomeIcon icon="fa-solid fa-xmark" />
                </button>
            </div>
            <DialogueTreeNode
                v-for="[name, node] in tree"
                :key="name"
                :name="name"
                :node="node"
                :path="name"
                :openFolders="openFolders"
                @toggleFolder="toggleFolder"
                @openDialogue="(name) => $emit('openDialogue', name)"
                @dialoguesChanged="listDialogues"
            />
        </MainPagePanelContainer>
    </div>

    <Teleport to="body">
        <div v-if="cancelConfirm" class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/40">
            <div class="bg-white rounded shadow-lg p-4 font-title text-sm w-80">
                <div class="font-semibold text-orange-darker mb-2">Cancel Dialogue</div>
                <p class="text-grey-dark mb-4">This will mark the dialogue as 'cancelled' on the server. Are you sure?</p>
                <div class="flex gap-2 justify-end">
                    <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="cancelConfirm = false">No, go back</button>
                    <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="doCancel">Yes, cancel it</button>
                </div>
            </div>
        </div>

        <div v-if="ongoingConfirm" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40">
            <div class="bg-white rounded shadow-lg p-4 font-title text-sm w-96">
                <template v-if="ongoingConfirm.dialogueName && ongoingConfirm.alreadyOpenTabId">
                    <div class="font-semibold text-orange-darker mb-2">Ongoing Dialogue Found</div>
                    <p class="text-grey-dark mb-4">
                        There is an ongoing dialogue
                        <code class="font-mono font-bold text-orange-darker">{{ ongoingConfirm.dialogueName }}</code>,
                        but it is already open in the editor.
                        Would you like to switch to its tab?
                    </p>
                    <div class="flex gap-2 justify-end">
                        <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="ongoingConfirm = null">Cancel</button>
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="confirmSwitchTab">Yes, go to tab</button>
                    </div>
                </template>
                <template v-else-if="ongoingConfirm.dialogueName">
                    <div class="font-semibold text-orange-darker mb-2">Ongoing Dialogue Found</div>
                    <p class="text-grey-dark mb-4">
                        There is an ongoing dialogue
                        <code class="font-mono font-bold text-orange-darker">{{ ongoingConfirm.dialogueName }}</code>,
                        which was last active
                        <strong>{{ formatTimeSince(ongoingConfirm.secondsSinceLastEngagement) }}</strong> ago.
                        Would you like to continue this dialogue?
                    </p>
                    <div class="flex gap-2 justify-end">
                        <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="ongoingConfirm = null">Close</button>
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="confirmCancel">Cancel Dialogue</button>
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="confirmResume">Continue</button>
                    </div>
                </template>
                <template v-else>
                    <div class="font-semibold text-orange-darker mb-2">No Ongoing Dialogue</div>
                    <p class="text-grey-dark mb-4">There is no ongoing dialogue at this time.</p>
                    <div class="flex justify-end">
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="ongoingConfirm = null">OK</button>
                    </div>
                </template>
            </div>
        </div>
    </Teleport>
</template>
