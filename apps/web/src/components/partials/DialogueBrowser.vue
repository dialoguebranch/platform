<script>
export default { inheritAttrs: false };
</script>

<script setup>
import { computed, inject, ref, useAttrs } from 'vue';
const attrs = useAttrs();
import { useClient } from '../../composables/client.js';
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
    'selectDialogue',
    'resumeDialogue',
    'activateTab',
]);

const client = useClient();

const tree = ref([]);
const openFolders = ref({});
const ongoingConfirm = ref(null); // { dialogueName, loggedDialogueId, secondsSinceLastEngagement, alreadyOpenTabId? }
const cancelConfirm = ref(false);

function buildTree(names) {
    const root = {};
    for (const name of names) {
        const parts = name.split('/');
        let node = root;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!node[parts[i]]) node[parts[i]] = { _children: {} };
            node = node[parts[i]]._children;
        }
        const leaf = parts[parts.length - 1];
        node[leaf] = { _file: name };
    }
    return root;
}

function listDialogues() {
    client.listDialogues(state.value.selectedProject?.slug)
    .then((json) => {
        const root = buildTree(json.dialogueNames);
        tree.value = Object.entries(root).sort(([, a], [, b]) => {
            const aIsFolder = !a._file;
            const bIsFolder = !b._file;
            if (aIsFolder !== bIsFolder) return aIsFolder ? -1 : 1;
            return 0;
        });
        openFolders.value = {};
    })
    .catch((error) => {
        console.log(error);
    });
}

function toggleFolder(path) {
    openFolders.value[path] = !openFolders.value[path];
}

const hasActiveDialogue = computed(() =>
    props.openTabs.some(t => t.dialogueName && !t.dialogueEnded && !t.dialogueCancelled));

function checkOngoingDialogue() {
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
    client.cancelDialogue(id);
}

listDialogues();
</script>

<template>
    <div class="flex flex-col gap-1" v-bind="attrs">
        <MainPagePanelHeader title="Dialogue Browser" class="sm:ml-2">
            <template #buttons>
                <IconButton icon="fa-solid fa-rotate-left" title="Retrieve most recent ongoing (server-side) dialogue" :disabled="hasActiveDialogue" @click="checkOngoingDialogue" />
                <IconButton icon="fa-solid fa-arrows-rotate" @click="listDialogues" />
            </template>
        </MainPagePanelHeader>
        <MainPagePanelContainer class="p-1 gap-1 flex flex-col sm:ml-1">
            <DialogueTreeNode
                v-for="[name, node] in tree"
                :key="name"
                :name="name"
                :node="node"
                :path="name"
                :openFolders="openFolders"
                @toggleFolder="toggleFolder"
                @selectDialogue="$emit('selectDialogue', $event)"
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
