<script setup>
import { inject, nextTick, ref, computed, useTemplateRef, watch, onMounted } from 'vue';
import { useClient } from '@/composables/client.js';

const state = inject('state');
import { logEvent } from '@/composables/debug-log.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import IconButton from '../widgets/IconButton.vue';
import BalloonDialogueComponent from './BalloonDialogueComponent.vue';
import TextDialogueComponent from './TextDialogueComponent.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';
import ModeSelector from '../widgets/ModeSelector.vue';

const emit = defineEmits([
    'newDialogueStep',
]);

const modes = [
    {
        name: 'balloon',
        icon: 'fa-regular fa-comments',
        title: 'Balloon style — shows dialogue as speech bubbles with an avatar',
    },
    {
        name: 'text',
        icon: 'fa-solid fa-paragraph',
        title: 'Text style — shows dialogue as a plain scrollable transcript',
    },
];

const selectedMode = ref('balloon');

// ---- Tab state ----

let nextTabId = 1;

function createTab() {
    return {
        id: nextTabId++,
        dialogueName: null,
        loggedDialogueId: null,
        dialogueSteps: [],
        dialogueEnded: false,
        dialogueCancelled: false,
        isDraftTest: false,
        draftSessionId: null,
    };
}

const tabs = ref([createTab()]);
const activeTabId = ref(tabs.value[0].id);

const activeTab = computed(() => tabs.value.find(t => t.id === activeTabId.value) ?? tabs.value[0]);

function getOrCreateEmptyTab() {
    const lastTab = tabs.value[tabs.value.length - 1];
    if (lastTab && !lastTab.dialogueName) return lastTab;
    tabs.value.push(createTab());
    return tabs.value[tabs.value.length - 1];
}

function addTab() {
    const tab = createTab();
    tabs.value.push(tab);
    activeTabId.value = tab.id;
    nextTick(updateScrollState);
}

const closeConfirm = ref(null); // { id }

function closeTab(id) {
    const tab = tabs.value.find(t => t.id === id);
    if (tab && tab.dialogueName && !tab.dialogueEnded) {
        closeConfirm.value = { id };
        return;
    }
    doCloseTab(id);
}

function doCloseTab(id) {
    closeConfirm.value = null;
    const index = tabs.value.findIndex(t => t.id === id);
    tabs.value.splice(index, 1);
    if (tabs.value.length === 0) {
        tabs.value.push(createTab());
    }
    if (activeTabId.value === id) {
        activeTabId.value = tabs.value[Math.min(index, tabs.value.length - 1)].id;
    }
    nextTick(updateScrollState);
}

function cancelAndCloseTab(id) {
    const tab = tabs.value.find(t => t.id === id);
    if (tab?.isDraftTest && tab.draftSessionId) {
        client.cancelDraftDialogue(tab.draftSessionId).finally(() => doCloseTab(id));
        return;
    }
    const lastStep = tab?.dialogueSteps[tab.dialogueSteps.length - 1];
    if (lastStep) {
        client.cancelDialogue(lastStep.loggedDialogueId).finally(() => doCloseTab(id));
    } else {
        doCloseTab(id);
    }
}

// ---- Client / dialogue logic ----

const client = useClient();

const balloons = useTemplateRef('balloons');
const textComponent = useTemplateRef('text-component');

watch(selectedMode, (mode) => {
    if (mode === 'text') nextTick(() => scrollTextToBottom());
});

const scrollTextToBottom = () => {
    if (textComponent.value) textComponent.value.scrollToBottom();
};

const loadDialogue = (name) => {
    const tab = getOrCreateEmptyTab();
    activeTabId.value = tab.id;
    tab.dialogueName = name;
    tab.dialogueSteps = [];
    tab.dialogueEnded = false;
    tab.dialogueCancelled = false;
    tab.isDraftTest = false;
    tab.draftSessionId = null;
    scrollActiveTabIntoView();
    logEvent('dialogue', 'Dialogue started: $1', name);
    client.startDialogue(state.value.selectedProject?.slug, name, 'en')
    .then((dialogueStep) => {
        tab.dialogueName = dialogueStep.dialogueName;
        tab.loggedDialogueId = dialogueStep.loggedDialogueId;
        tab.dialogueSteps.push(dialogueStep);
        tab.dialogueEnded = dialogueStep.replies.length === 0;
        if (tab.dialogueEnded) logEvent('dialogue', 'Dialogue ended immediately: $1', name);
        emit('newDialogueStep');
        scrollTextToBottom();
    });
};

// Starts an ephemeral draft test session (see /draft/* end-points) — not saved to dialogue
// history, and reads/writes the tester's real variables (revertible via revertVariables()).
const loadDraftDialogue = (name) => {
    const tab = getOrCreateEmptyTab();
    activeTabId.value = tab.id;
    tab.dialogueName = name;
    tab.dialogueSteps = [];
    tab.dialogueEnded = false;
    tab.dialogueCancelled = false;
    tab.isDraftTest = true;
    tab.draftSessionId = null;
    scrollActiveTabIntoView();
    logEvent('dialogue', 'Draft test started: $1', name);
    client.startDraftDialogue(state.value.selectedProject?.slug, name, 'en')
    .then(({ draftSessionId, dialogueStep }) => {
        tab.draftSessionId = draftSessionId;
        tab.dialogueName = dialogueStep.dialogueName;
        tab.dialogueSteps.push(dialogueStep);
        tab.dialogueEnded = dialogueStep.replies.length === 0;
        if (tab.dialogueEnded) logEvent('dialogue', 'Draft test ended immediately: $1', name);
        emit('newDialogueStep');
        scrollTextToBottom();
    });
};

function restartActiveTab() {
    const tab = activeTab.value;
    if (tab.isDraftTest) {
        loadDraftDialogue(tab.dialogueName);
    } else {
        loadDialogue(tab.dialogueName);
    }
}

function onRevertVariablesClick() {
    const tab = activeTab.value;
    if (!tab.isDraftTest || !tab.draftSessionId) return;
    logEvent('dialogue', 'Draft test variables reverted: $1', tab.dialogueName);
    client.revertDraftVariables(tab.draftSessionId)
    .then(() => {
        tab.draftSessionId = null;
        tab.dialogueEnded = true;
        emit('newDialogueStep');
    });
}

const reloading = ref(false);

const reloadStep = () => {
    const tab = activeTab.value;
    if (tab.dialogueName && !tab.isDraftTest) {
        reloading.value = true;
        client.continueDialogue(state.value.selectedProject?.slug, tab.dialogueName)
        .then((dialogueStep) => {
            tab.dialogueSteps.pop();
            tab.dialogueSteps.push(dialogueStep);
            emit('newDialogueStep');
            scrollTextToBottom();
        })
        .finally(() => {
            setTimeout(() => { reloading.value = false; }, 1000);
        });
    }
};

const resize = () => {
    if (balloons.value) balloons.value.resize();
};

const resumeDialogue = (name) => {
    const newTab = getOrCreateEmptyTab();
    newTab.dialogueName = name;
    activeTabId.value = newTab.id;
    nextTick(updateScrollState);
    client.continueDialogue(state.value.selectedProject?.slug, name)
    .then((dialogueStep) => {
        const tab = tabs.value.find(t => t.id === newTab.id);
        if (!tab) return;
        if (dialogueStep) {
            tab.loggedDialogueId = dialogueStep.loggedDialogueId;
            tab.dialogueSteps.push(dialogueStep);
            tab.dialogueEnded = dialogueStep.replies.length === 0;
        } else {
            tab.dialogueEnded = true;
        }
        logEvent('dialogue', 'Dialogue resumed: $1', name);
        emit('newDialogueStep');
        scrollTextToBottom();
    });
};

function clearAllTabs() {
    tabs.value = [createTab()];
    activeTabId.value = tabs.value[0].id;
    nextTick(updateScrollState);
}

function activateTab(tabId) {
    activeTabId.value = tabId;
    nextTick(scrollActiveTabIntoView);
}

defineExpose({
    tabs,
    loadDialogue,
    loadDraftDialogue,
    resumeDialogue,
    reloadStep,
    resize,
    clearAllTabs,
    activateTab,
});

// ---- Tab bar scroll ----

const tabBar = useTemplateRef('tab-bar');
const canScrollLeft = ref(false);
const canScrollRight = ref(false);

function updateScrollState() {
    const el = tabBar.value;
    if (!el) return;
    canScrollLeft.value = el.scrollLeft > 0;
    canScrollRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 1;
}

function scrollTabs(dir) {
    const el = tabBar.value;
    if (!el) return;
    el.scrollBy({ left: dir * 120, behavior: 'smooth' });
}

function scrollActiveTabIntoView() {
    nextTick(() => {
        const bar = tabBar.value;
        if (!bar) return;
        const active = bar.querySelector('[data-active="true"]');
        if (!active) return;
        const barLeft = bar.scrollLeft;
        const barRight = barLeft + bar.clientWidth;
        const tabLeft = active.offsetLeft;
        const tabRight = tabLeft + active.offsetWidth;
        if (tabLeft < barLeft) {
            bar.scrollTo({ left: tabLeft, behavior: 'smooth' });
        } else if (tabRight > barRight) {
            bar.scrollTo({ left: tabRight - bar.clientWidth, behavior: 'smooth' });
        }
        updateScrollState();
    });
}

watch(activeTabId, scrollActiveTabIntoView);

onMounted(() => {
    updateScrollState();
    tabBar.value?.addEventListener('scroll', updateScrollState);
    new ResizeObserver(updateScrollState).observe(tabBar.value);
});

function onCancelClick() {
    const tab = activeTab.value;
    if (tab.isDraftTest) {
        if (!tab.draftSessionId) return;
        logEvent('dialogue', 'Draft test cancelled: $1', tab.dialogueName);
        client.cancelDraftDialogue(tab.draftSessionId)
        .then(() => {
            tab.draftSessionId = null;
            tab.dialogueCancelled = true;
            tab.dialogueEnded = true;
        });
        return;
    }
    const lastStep = tab.dialogueSteps[tab.dialogueSteps.length - 1];
    if (!lastStep) return;
    logEvent('dialogue', 'Dialogue cancelled: $1', tab.dialogueName);
    client.cancelDialogue(lastStep.loggedDialogueId)
    .then(() => {
        tab.dialogueCancelled = true;
        tab.dialogueEnded = true;
    });
}

function onSelectReply(dialogueStep, reply) {
    const tab = activeTab.value;
    const replyText = reply.statement?.segments?.map(s => s.text).join('') ?? String(reply.replyId);

    if (tab.isDraftTest) {
        logEvent('dialogue', 'Draft test reply selected: $1', replyText);
        client.progressDraftDialogue(tab.draftSessionId, reply.replyId)
        .then((nextStep) => {
            if (nextStep) {
                tab.dialogueName = nextStep.dialogueName;
                tab.dialogueSteps.push(nextStep);
                tab.dialogueEnded = nextStep.replies.length === 0;
                if (tab.dialogueEnded) logEvent('dialogue', 'Draft test ended: $1', tab.dialogueName);
            } else {
                tab.dialogueEnded = true;
                logEvent('dialogue', 'Draft test ended: $1', tab.dialogueName);
            }
            emit('newDialogueStep');
            scrollTextToBottom();
        });
        return;
    }

    logEvent('dialogue', 'Reply selected: $1', replyText);
    client.progressDialogue(dialogueStep.loggedDialogueId, dialogueStep.loggedInteractionIndex,
        reply.replyId)
    .then((nextStep) => {
        if (nextStep) {
            tab.dialogueName = nextStep.dialogueName;
            tab.dialogueSteps.push(nextStep);
            tab.dialogueEnded = nextStep.replies.length === 0;
            if (tab.dialogueEnded) logEvent('dialogue', 'Dialogue ended: $1', tab.dialogueName);
        } else {
            tab.dialogueEnded = true;
            logEvent('dialogue', 'Dialogue ended: $1', tab.dialogueName);
        }
        emit('newDialogueStep');
        scrollTextToBottom();
    });
}
</script>

<template>
    <div class="flex flex-col gap-1">
        <MainPagePanelHeader title="Interaction Tester">
            <template #buttons>
                <ModeSelector :modes="modes" v-model="selectedMode" />
                <IconButton
                    icon="fa-solid fa-arrows-rotate"
                    :class="{ 'animate-spin': reloading }"
                    :title="activeTab.dialogueName && !activeTab.dialogueEnded && !activeTab.isDraftTest ? 'Refresh current dialogue step' : 'Refresh current dialogue step (not available for draft tests)'"
                    :disabled="reloading || !activeTab.dialogueName || activeTab.dialogueEnded || activeTab.isDraftTest"
                    @click="reloadStep"
                />
                <IconButton
                    icon="fa-solid fa-circle-xmark"
                    color="warning"
                    :disabled="activeTab.dialogueName === null || activeTab.dialogueEnded"
                    :title="activeTab.dialogueName && !activeTab.dialogueEnded ? 'Cancel the current dialogue' : 'Cancel the current dialogue (no dialogue active)'"
                    @click="onCancelClick"
                />
                <IconButton
                    v-if="activeTab.isDraftTest"
                    icon="fa-solid fa-clock-rotate-left"
                    color="warning"
                    :disabled="!activeTab.draftSessionId"
                    title="Revert any variable changes made during this draft test back to their original values"
                    @click="onRevertVariablesClick"
                />
            </template>
        </MainPagePanelHeader>

        <!-- Tab bar + content (no gap between them) -->
        <div class="flex flex-col grow min-h-0">

        <!-- Tab bar -->
        <div class="flex items-end bg-white pt-2 relative z-[100]">
            <div class="absolute bottom-0 left-0 right-0 h-px bg-grey-light pointer-events-none z-0"></div>
            <button
                type="button"
                class="flex items-center justify-center w-5 h-6 shrink-0 mx-1.5"
                :class="canScrollLeft ? 'text-orange-darker hover:text-orange-dark cursor-pointer' : 'text-grey-light cursor-not-allowed'"
                :disabled="!canScrollLeft"
                title="Scroll tabs left"
                @click="scrollTabs(-1)"
            >
                <FontAwesomeIcon icon="fa-solid fa-circle-chevron-left" />
            </button>
            <div ref="tab-bar" class="flex items-end gap-0.5 px-1 overflow-x-auto flex-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                <button
                    v-for="tab in tabs"
                    :key="tab.id"
                    type="button"
                    :data-active="tab.id === activeTabId"
                    class="flex items-center gap-1 px-2 py-0.5 font-title text-xs rounded-t border shrink-0 cursor-pointer"
                    :class="tab.id === activeTabId
                        ? 'bg-white border-grey-light border-b-2 border-b-white text-orange-darker font-semibold relative z-[100] -mb-px'
                        : 'bg-grey-lighter border-grey-light border-b-0 text-grey-dark hover:bg-white'"
                    @click="activeTabId = tab.id"
                >
                    <FontAwesomeIcon v-if="tab.isDraftTest" icon="fa-solid fa-flask" class="text-[10px]" title="Draft test" />
                    <span>{{ tab.dialogueName ?? 'New' }}</span>
                    <span
                        class="w-3.5 h-3.5 flex items-center justify-center hover:text-icon-button-warning-hover"
                        @click.stop="closeTab(tab.id)"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </span>
                </button>
                <button
                    type="button"
                    class="flex items-center justify-center w-5 h-5 mb-0.5 font-title text-xs text-orange-darker hover:text-orange-dark cursor-pointer shrink-0"
                    title="Open new tab"
                    @click="addTab"
                >
                    <FontAwesomeIcon icon="fa-solid fa-plus" />
                </button>
            </div>
            <button
                type="button"
                class="flex items-center justify-center w-5 h-6 shrink-0 mx-1.5"
                :class="canScrollRight ? 'text-orange-darker hover:text-orange-dark cursor-pointer' : 'text-grey-light cursor-not-allowed'"
                :disabled="!canScrollRight"
                title="Scroll tabs right"
                @click="scrollTabs(1)"
            >
                <FontAwesomeIcon icon="fa-solid fa-circle-chevron-right" />
            </button>
        </div><!-- end tab bar -->

        <!-- Close tab confirmation modal -->
        <Teleport to="body">
            <div v-if="closeConfirm" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40">
                <div class="bg-white rounded shadow-lg p-4 font-title text-sm w-80">
                    <div class="font-semibold text-orange-darker mb-2">Close Tab</div>
                    <p class="text-grey-dark mb-4">This dialogue is still ongoing. What would you like to do?</p>
                    <div class="flex flex-col gap-2">
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="cancelAndCloseTab(closeConfirm.id)">Explicitly cancel dialogue and close</button>
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="doCloseTab(closeConfirm.id)">Just close the tab</button>
                        <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="closeConfirm = null">Cancel</button>
                    </div>
                </div>
            </div>
        </Teleport>

        <!-- Active tab content -->
        <div class="relative grow min-h-0 flex flex-col">
            <MainPagePanelContainer class="-mt-px">
                <BalloonDialogueComponent
                    v-if="selectedMode === 'balloon'"
                    ref="balloons"
                    :dialogueSteps="activeTab.dialogueSteps"
                    :dialogueEnded="activeTab.dialogueEnded"
                    :dialogueCancelled="activeTab.dialogueCancelled"
                    @selectReply="onSelectReply"
                    @restartDialogue="restartActiveTab"
                />
                <TextDialogueComponent
                    v-if="selectedMode === 'text'"
                    ref="text-component"
                    :dialogueSteps="activeTab.dialogueSteps"
                    :dialogueEnded="activeTab.dialogueEnded"
                    :dialogueCancelled="activeTab.dialogueCancelled"
                    @selectReply="onSelectReply"
                    @restartDialogue="restartActiveTab"
                />
            </MainPagePanelContainer>
            <div v-if="activeTab.loggedDialogueId" class="absolute bottom-3 left-3 font-mono text-[10px] text-gray-400 pointer-events-none">
                <span class="font-semibold">Logged Dialogue ID:</span> {{ activeTab.loggedDialogueId }}
            </div>
        </div>

        </div><!-- end tab bar + content wrapper -->
    </div>
</template>
