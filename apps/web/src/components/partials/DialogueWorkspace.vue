<script setup>
import { inject, nextTick, ref, computed, useTemplateRef, watch, onMounted } from 'vue';
import { useClient } from '@/composables/client.js';

const state = inject('state');
import { logEvent } from '@/composables/debug-log.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { DIALOGUE_WORKSPACE_STYLE_TEXT, DIALOGUE_WORKSPACE_STYLE_BALLOONS, DIALOGUE_WORKSPACE_STYLE_EDIT, DIALOGUE_WORKSPACE_STYLE_TRANSLATE, DLB_APP_MODE_DRAFT } from '@/dlb-lib/WCTAClientState.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import IconButton from '../widgets/IconButton.vue';
import BalloonDialogueComponent from './BalloonDialogueComponent.vue';
import TextDialogueComponent from './TextDialogueComponent.vue';
import DialogueEditor from './DialogueEditor.vue';
import TranslationEditor from './TranslationEditor.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';
import ModeSelector from '../widgets/ModeSelector.vue';

const emit = defineEmits([
    'newDialogueStep',
    'dialogueSaved',
    'modeChanged',
]);

const modes = [
    {
        name: 'balloon',
        icon: 'fa-regular fa-comments',
        title: 'Test in Speech Bubble Style',
    },
    {
        name: 'text',
        icon: 'fa-solid fa-paragraph',
        title: 'Test in RPG Text Style',
    },
    {
        name: 'edit',
        icon: 'fa-solid fa-diagram-project',
        title: 'Edit mode — edit the active tab\'s dialogue nodes',
    },
    {
        name: 'translate',
        icon: 'fa-solid fa-language',
        title: 'Edit the active tab\'s dialogue translations',
    },
];

// The node editor and translation editor are only ever reachable in Draft Mode — Live Mode is
// testing-only.
const availableModes = computed(() =>
    state.value.mode === DLB_APP_MODE_DRAFT
        ? modes
        : modes.filter((m) => m.name !== 'edit' && m.name !== 'translate'));

// Backed by the `state.dialogueWorkspaceStyle` cookie (see WCTAClientState.js) so the chosen
// mode survives a page reload.
const selectedMode = computed({
    get: () => {
        // A dialogueWorkspaceStyle cookie value of EDIT/TRANSLATE from an earlier Draft Mode
        // session must never surface those panels while in Live Mode (e.g. right after loading
        // the app) — Live Mode is testing-only, and neither is even offered in availableModes
        // above.
        if (state.value.dialogueWorkspaceStyle === DIALOGUE_WORKSPACE_STYLE_TEXT) return 'text';
        if (state.value.mode === DLB_APP_MODE_DRAFT) {
            if (state.value.dialogueWorkspaceStyle === DIALOGUE_WORKSPACE_STYLE_EDIT) return 'edit';
            if (state.value.dialogueWorkspaceStyle === DIALOGUE_WORKSPACE_STYLE_TRANSLATE) return 'translate';
        }
        return 'balloon';
    },
    set: (mode) => {
        state.value.dialogueWorkspaceStyle = mode === 'text'
            ? DIALOGUE_WORKSPACE_STYLE_TEXT
            : mode === 'edit'
                ? DIALOGUE_WORKSPACE_STYLE_EDIT
                : mode === 'translate'
                    ? DIALOGUE_WORKSPACE_STYLE_TRANSLATE
                    : DIALOGUE_WORKSPACE_STYLE_BALLOONS;
    },
});

// The last non-edit, non-translate mode selected (balloon/text) — used to jump back out of edit
// or translate mode whenever the user explicitly runs/tests/resumes a dialogue, so that action
// doesn't get hidden behind the node editor or translation editor.
const lastTestMode = ref('balloon');

function ensureTestMode() {
    if (selectedMode.value === 'edit' || selectedMode.value === 'translate') {
        selectedMode.value = lastTestMode.value;
    }
}

// ---- Language selection ----

// The language new dialogue tests are started in — a plain global setting (not persisted across
// reloads; it's reset to the project's default source language every time the project loads,
// since a language code from one project may not even be valid for another). Once a tab has
// actually started, it remembers its own language (see createTab's `language` field) so changing
// this afterward only affects subsequently-started tabs, not ones already running.
const availableLanguages = ref([]); // [{ code, name }]
const selectedLanguage = ref('');

function loadAvailableLanguages() {
    const slug = state.value.selectedProject?.slug;
    if (!slug) return;
    client.getProject(slug)
        .then((project) => {
            availableLanguages.value = [
                { code: project.sourceLanguageCode, name: project.sourceLanguageName },
                ...(project.translationLanguages ?? []).map((t) => ({
                    code: t.translationLanguageCode, name: t.translationLanguageName,
                })),
            ];
            selectedLanguage.value = project.sourceLanguageCode ?? 'en';
        })
        .catch(() => {
            // Language selection is a convenience — fall back to a single English option so
            // dialogue testing still works even if the project's language configuration can't be
            // loaded.
            availableLanguages.value = [{ code: 'en', name: 'English' }];
            selectedLanguage.value = 'en';
        });
}

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
        // The language this tab's dialogue was actually started in — set by loadDialogue()/
        // loadDraftDialogue() the first time a test starts, then reused on every subsequent
        // restart so changing the workspace's language selector never changes an already-running
        // tab out from under it.
        language: null,
        // Tracks edits made in Edit mode since this tab's test last (re)started, so that leaving
        // Edit mode again can offer to restart the test with up-to-date content (see
        // handleReturnFromEdit / onEditorNodeChanged below).
        dialogueEdited: false,
        lastEditedNodeTitle: null,
        // Set by editDialogue() for a tab that has a dialogueName but has never actually been run;
        // cleared by every function that starts/resumes a test. Distinguishes that case from a
        // load that's merely in flight (which also has a dialogueName with no steps yet) so
        // ensureActiveTabStarted only acts on the former (see its comment below).
        openedForEditOnly: false,
        // Set when loadDraftDialogue() fails to start a test session — { message, errors } (see
        // ProjectParseHttpError.java), rendered inline by BalloonDialogueComponent/
        // TextDialogueComponent instead of a transient toast, since the details list needs to
        // stick around for the user to expand and read at their own pace.
        startError: null,
        // True while a progressDialogue/progressDraftDialogue request for this tab's current step
        // is in flight — disables reply selection so a fast double-click can't fire two progress
        // requests for the same step (see onSelectReply below).
        awaitingReply: false,
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
    // Only warn about an "ongoing" dialogue if one has actually been started (has steps) — a
    // tab opened purely via editDialogue() has a dialogueName but was never run.
    if (tab && tab.dialogueSteps.length > 0 && !tab.dialogueEnded) {
        closeConfirm.value = { id };
        return;
    }
    doCloseTab(id);
}

function doCloseTab(id) {
    closeConfirm.value = null;
    const index = tabs.value.findIndex(t => t.id === id);
    // Can happen if a pending cancelAndCloseTab()/close request resolves after the tab was
    // already removed some other way — splice(-1, 1) would otherwise delete the last tab instead
    // of doing nothing.
    if (index === -1) return;
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
        client.cancelDraftDialogue(tab.draftSessionId)
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => doCloseTab(id));
        return;
    }
    const lastStep = tab?.dialogueSteps[tab.dialogueSteps.length - 1];
    if (lastStep) {
        client.cancelDialogue(lastStep.loggedDialogueId)
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => doCloseTab(id));
    } else {
        doCloseTab(id);
    }
}

// ---- Client / dialogue logic ----

const client = useClient();

const balloons = useTemplateRef('balloons');
const textComponent = useTemplateRef('text-component');
const dialogueEditor = useTemplateRef('dialogue-editor');

watch(selectedMode, (mode, oldMode) => {
    emit('modeChanged', mode);
    if (mode !== 'edit' && mode !== 'translate') lastTestMode.value = mode;
    const wasAuthoring = oldMode === 'edit' || oldMode === 'translate';
    const isAuthoring = mode === 'edit' || mode === 'translate';
    if (wasAuthoring && !isAuthoring) {
        // A tab opened via editDialogue()/openDialogueForTranslation() has a dialogueName but was
        // never run; handleReturnFromEdit only applies to tabs whose test is already running (it
        // bails out via tab.dialogueEdited/dialogueSteps checks otherwise), so that case is
        // handled separately by starting it fresh instead. handleReturnFromEdit itself is a no-op
        // unless actual node edits were made (tab.dialogueEdited) — translating alone never sets
        // that flag, so a translate-only session correctly triggers no reconciliation.
        const tab = activeTab.value;
        if (tab.openedForEditOnly) {
            restartActiveTab();
        } else {
            handleReturnFromEdit(tab);
        }
    }
    if (mode === 'text') nextTick(() => scrollTextToBottom());
});

const scrollTextToBottom = () => {
    if (textComponent.value) textComponent.value.scrollToBottom();
};

// Pass `tab` to restart a specific, already-open tab in place (e.g. from restartActiveTab)
// rather than the default of finding/creating an empty one — mirrors loadDraftDialogue.
// `startNodeId` starts the test from a particular node instead of the dialogue's default "Start"
// node (e.g. to resume at the same point after switching the test language — see the
// selectedLanguage watcher below).
const loadDialogue = (name, { tab: givenTab, startNodeId, language } = {}) => {
    if (!givenTab) ensureTestMode();
    const tab = givenTab ?? getOrCreateEmptyTab();
    activeTabId.value = tab.id;
    tab.dialogueName = name;
    tab.language = language ?? selectedLanguage.value;
    tab.dialogueSteps = [];
    tab.dialogueEnded = false;
    tab.dialogueCancelled = false;
    tab.isDraftTest = false;
    tab.draftSessionId = null;
    tab.openedForEditOnly = false;
    // A fresh test start supersedes whatever reply request (if any) was in flight for this tab —
    // don't leave reply selection stuck disabled by a stale one that hasn't resolved yet.
    tab.awaitingReply = false;
    scrollActiveTabIntoView();
    dismissError();
    logEvent('dialogue', 'Dialogue started: $1', name);
    client.startDialogue(state.value.selectedProject?.slug, name, tab.language, startNodeId)
    .then((dialogueStep) => {
        tab.dialogueName = dialogueStep.dialogueName;
        tab.loggedDialogueId = dialogueStep.loggedDialogueId;
        tab.dialogueSteps.push(dialogueStep);
        tab.dialogueEnded = dialogueStep.replies.length === 0;
        if (tab.dialogueEnded) logEvent('dialogue', 'Dialogue ended immediately: $1', name);
        emit('newDialogueStep');
        scrollTextToBottom();
    })
    .catch((error) => {
        showError(describeError(error));
    });
};

// Starts an ephemeral draft test session (see /draft/* end-points) — not saved to dialogue
// history, and reads/writes the workspace's real variables (revertible via revertVariables()).
// Pass `tab` to restart a specific, already-open tab in place (e.g. from handleReturnFromEdit)
// rather than the default of finding/creating an empty one; `startNodeId` starts the test from a
// particular node instead of the dialogue's default "Start" node.
const loadDraftDialogue = (name, { tab: givenTab, startNodeId, language } = {}) => {
    if (!givenTab) ensureTestMode();
    const tab = givenTab ?? getOrCreateEmptyTab();
    activeTabId.value = tab.id;
    tab.dialogueName = name;
    tab.language = language ?? selectedLanguage.value;
    tab.dialogueSteps = [];
    tab.dialogueEnded = false;
    tab.dialogueCancelled = false;
    tab.isDraftTest = true;
    tab.draftSessionId = null;
    tab.loggedDialogueId = null;
    tab.openedForEditOnly = false;
    tab.dialogueEdited = false;
    tab.lastEditedNodeTitle = null;
    tab.startError = null;
    // A fresh test start supersedes whatever reply request (if any) was in flight for this tab —
    // don't leave reply selection stuck disabled by a stale one that hasn't resolved yet.
    tab.awaitingReply = false;
    scrollActiveTabIntoView();
    dismissError();
    logEvent('dialogue', 'Draft test started: $1', name);
    client.startDraftDialogue(state.value.selectedProject?.slug, name, tab.language, startNodeId)
    .then(({ draftSessionId, dialogueStep }) => {
        tab.draftSessionId = draftSessionId;
        tab.dialogueName = dialogueStep.dialogueName;
        tab.dialogueSteps.push(dialogueStep);
        tab.dialogueEnded = dialogueStep.replies.length === 0;
        if (tab.dialogueEnded) logEvent('dialogue', 'Draft test ended immediately: $1', name);
        emit('newDialogueStep');
        scrollTextToBottom();
    })
    .catch((error) => {
        // A structured "errors" field (see ProjectParseHttpError.java) means the whole project
        // currently fails to parse — shown inline with an expandable details list, since it needs
        // to stick around rather than vanish with a toast. Anything else (network error, other
        // 4xx) falls back to the generic toast as usual.
        if (error?.errors) {
            tab.startError = { message: error.message, errors: error.errors };
        } else {
            showError(describeError(error));
        }
    });
};

function restartActiveTab() {
    const tab = activeTab.value;
    if (tab.isDraftTest) {
        loadDraftDialogue(tab.dialogueName, { tab, language: tab.language });
    } else {
        loadDialogue(tab.dialogueName, { tab, language: tab.language });
    }
}

// Switching the "Test dialogues in:" selector while a dialogue is actually running restarts the
// active tab's test at its current node, in the newly selected language, instead of silently
// leaving it running in whatever language it happened to be started in. Ignores tabs that
// haven't started a test yet (selectedLanguage.value's initial assignment in
// loadAvailableLanguages() would otherwise also trigger this).
watch(selectedLanguage, (newLanguage, oldLanguage) => {
    if (!oldLanguage) return;
    const tab = activeTab.value;
    if (!tab.dialogueName || tab.dialogueSteps.length === 0 || tab.openedForEditOnly) return;
    if (tab.language === newLanguage) return;
    const currentNode = tab.dialogueSteps[tab.dialogueSteps.length - 1].node;
    if (tab.isDraftTest) {
        loadDraftDialogue(tab.dialogueName, { tab, startNodeId: currentNode, language: newLanguage });
    } else {
        loadDialogue(tab.dialogueName, { tab, startNodeId: currentNode, language: newLanguage });
    }
});

// A tab opened via editDialogue()/openDialogueForTranslation() has a dialogueName but was never
// run (see its comment above). As soon as such a tab is visible in Balloon/Text mode — by
// switching mode or by switching to that tab — start it from its default "Start" node instead of
// waiting for a manual action.
function ensureActiveTabStarted() {
    if (selectedMode.value === 'edit' || selectedMode.value === 'translate') return;
    const tab = activeTab.value;
    if (!tab.openedForEditOnly) return;
    restartActiveTab();
}

// ---- Reconciling edits made in Edit mode when returning to Balloon/Text mode ----

const editSwitchNotice = ref(null); // { dialogueName }
const restartPrompt = ref(null); // { tabId, dialogueName, lastEditedNodeTitle }

function onEditorNodeChanged(nodeTitle) {
    const tab = activeTab.value;
    tab.dialogueEdited = true;
    tab.lastEditedNodeTitle = nodeTitle;
}

function onEditorNodeDeleted(nodeTitle) {
    const tab = activeTab.value;
    tab.dialogueEdited = true;
    if (tab.lastEditedNodeTitle === nodeTitle) tab.lastEditedNodeTitle = null;
}

// Called whenever the mode switches away from 'edit' back to Balloon/Text. If the dialogue in the
// tab being left was edited and already had a test running, that test's content is now stale:
// a "live" (published, logged) test is force-switched to an ephemeral draft test (with a notice,
// since that's a mode change the user didn't explicitly choose); an already-ephemeral test instead
// prompts to restart from Start or from the node that was last edited.
function handleReturnFromEdit(tab) {
    if (!tab.dialogueEdited || tab.dialogueSteps.length === 0) return;
    if (!tab.isDraftTest) {
        editSwitchNotice.value = { dialogueName: tab.dialogueName };
        loadDraftDialogue(tab.dialogueName, { tab, language: tab.language });
        return;
    }
    if (!tab.lastEditedNodeTitle) {
        loadDraftDialogue(tab.dialogueName, { tab, language: tab.language });
        return;
    }
    restartPrompt.value = {
        tabId: tab.id,
        dialogueName: tab.dialogueName,
        lastEditedNodeTitle: tab.lastEditedNodeTitle,
    };
}

function confirmRestartFromStart() {
    const tab = tabs.value.find(t => t.id === restartPrompt.value.tabId);
    restartPrompt.value = null;
    if (tab) loadDraftDialogue(tab.dialogueName, { tab, language: tab.language });
}

function confirmRestartFromLastEditedNode() {
    const tab = tabs.value.find(t => t.id === restartPrompt.value.tabId);
    const startNodeId = restartPrompt.value.lastEditedNodeTitle;
    restartPrompt.value = null;
    if (tab) loadDraftDialogue(tab.dialogueName, { tab, startNodeId, language: tab.language });
}

function onRevertVariablesClick() {
    const tab = activeTab.value;
    if (!tab.isDraftTest || !tab.draftSessionId) return;
    dismissError();
    logEvent('dialogue', 'Draft test variables reverted: $1', tab.dialogueName);
    client.revertDraftVariables(tab.draftSessionId)
    .then(() => {
        tab.draftSessionId = null;
        tab.dialogueEnded = true;
        emit('newDialogueStep');
    })
    .catch((error) => {
        showError(describeError(error));
    });
}

const reloading = ref(false);

const reloadStep = () => {
    const tab = activeTab.value;
    if (tab.dialogueName && !tab.isDraftTest) {
        reloading.value = true;
        dismissError();
        client.continueDialogue(state.value.selectedProject?.slug, tab.dialogueName)
        .then((dialogueStep) => {
            tab.dialogueSteps.pop();
            tab.dialogueSteps.push(dialogueStep);
            emit('newDialogueStep');
            scrollTextToBottom();
        })
        .catch((error) => {
            showError(describeError(error));
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
    ensureTestMode();
    const newTab = getOrCreateEmptyTab();
    newTab.dialogueName = name;
    newTab.openedForEditOnly = false;
    activeTabId.value = newTab.id;
    nextTick(updateScrollState);
    dismissError();
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
    })
    .catch((error) => {
        showError(describeError(error));
    });
};

function clearAllTabs() {
    tabs.value = [createTab()];
    activeTabId.value = tabs.value[0].id;
    nextTick(updateScrollState);
}

function activateTab(tabId) {
    ensureTestMode();
    activeTabId.value = tabId;
    nextTick(scrollActiveTabIntoView);
}

// Opens (or reuses an empty tab for) the given dialogue for authoring (Edit or Translate mode) —
// unlike loadDialogue, this never starts a running dialogue session; it just associates the tab
// with a dialogue name so the node editor / translation editor has something to load.
function prepareAuthoringTab(name) {
    const tab = getOrCreateEmptyTab();
    activeTabId.value = tab.id;
    tab.dialogueName = name;
    // A dialogue can only be opened for authoring if it already has a draft, so default a tab
    // that's never been tested to ephemeral draft testing — always possible — rather than the
    // live/published path (restartActiveTab's default), which may not exist for this dialogue at
    // all. Leave it alone if a test already ran here, so handleReturnFromEdit can still tell
    // whether that earlier test was live or ephemeral.
    if (tab.dialogueSteps.length === 0) {
        tab.isDraftTest = true;
        tab.openedForEditOnly = true;
    }
    scrollActiveTabIntoView();
    return tab;
}

function editDialogue(name) {
    prepareAuthoringTab(name);
    selectedMode.value = 'edit';
}

function openDialogueForTranslation(name) {
    prepareAuthoringTab(name);
    selectedMode.value = 'translate';
}

// Opens a dialogue from the Dialogue Browser according to whatever mode the workspace is currently
// in: if we're already in Edit or Translate mode (only reachable in Draft Mode), open the
// corresponding authoring panel (same as editDialogue/openDialogueForTranslation); otherwise start
// a running test against whichever execution path the global Live/Draft mode toggle currently
// selects.
function openDialogue(name) {
    if (selectedMode.value === 'edit') {
        editDialogue(name);
    } else if (selectedMode.value === 'translate') {
        openDialogueForTranslation(name);
    } else if (state.value.mode === DLB_APP_MODE_DRAFT) {
        loadDraftDialogue(name);
    } else {
        loadDialogue(name);
    }
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
    editDialogue,
    openDialogue,
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

watch(activeTabId, () => {
    dismissError();
    scrollActiveTabIntoView();
    ensureActiveTabStarted();
});

onMounted(() => {
    updateScrollState();
    tabBar.value?.addEventListener('scroll', updateScrollState);
    new ResizeObserver(updateScrollState).observe(tabBar.value);
    loadAvailableLanguages();
});

function onCancelClick() {
    const tab = activeTab.value;
    // A reply is already progressing this tab — cancelling now would race a stale response into
    // reviving the dialogue after it's marked cancelled (see onSelectReply's stale-cancel guard).
    if (tab.awaitingReply) return;
    dismissError();
    if (tab.isDraftTest) {
        if (!tab.draftSessionId) return;
        logEvent('dialogue', 'Draft test cancelled: $1', tab.dialogueName);
        client.cancelDraftDialogue(tab.draftSessionId)
        .then(() => {
            tab.draftSessionId = null;
            tab.dialogueCancelled = true;
            tab.dialogueEnded = true;
        })
        .catch((error) => {
            showError(describeError(error));
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
    })
    .catch((error) => {
        showError(describeError(error));
    });
}

function onSelectReply(dialogueStep, reply) {
    const tab = activeTab.value;
    // Guards against a fast double-click/tap firing two progress requests for the same step —
    // both components also disable reply selection via :awaitingReply, this is the source of truth.
    if (tab.awaitingReply) return;
    const replyText = reply.statement?.segments?.map(s => s.text).join('') ?? String(reply.replyId);
    dismissError();
    tab.awaitingReply = true;

    if (tab.isDraftTest) {
        logEvent('dialogue', 'Draft test reply selected: $1', replyText);
        client.progressDraftDialogue(tab.draftSessionId, reply.replyId)
        .then((nextStep) => {
            // The tab could have been cancelled while this request was in flight (Cancel is
            // disabled once awaitingReply is set, but this stays correct even if that ever
            // changes) — a stale response must not resurrect an already-cancelled dialogue.
            if (tab.dialogueCancelled) return;
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
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            tab.awaitingReply = false;
        });
        return;
    }

    logEvent('dialogue', 'Reply selected: $1', replyText);
    client.progressDialogue(dialogueStep.loggedDialogueId, dialogueStep.loggedInteractionIndex,
        reply.replyId)
    .then((nextStep) => {
        // See the identical guard in the draft-test branch above.
        if (tab.dialogueCancelled) return;
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
    })
    .catch((error) => {
        showError(describeError(error));
    })
    .finally(() => {
        tab.awaitingReply = false;
    });
}
</script>

<template>
    <div class="flex flex-col gap-1">
        <MainPagePanelHeader title="Dialogue Workspace">
            <template #buttons>
                <span v-if="availableLanguages.length > 0" class="self-center text-xs font-title text-grey-dark whitespace-nowrap">Test dialogues in:</span>
                <select
                    v-if="availableLanguages.length > 0"
                    v-model="selectedLanguage"
                    title="Language new dialogue tests are started in"
                    class="h-7.5 px-2 border border-grey-light rounded-lg text-xs font-title focus:outline-none focus:border-orange-dark bg-white cursor-pointer"
                >
                    <option v-for="lang in availableLanguages" :key="lang.code" :value="lang.code">{{ lang.name }}</option>
                </select>
                <ModeSelector :modes="availableModes" v-model="selectedMode" />
                <template v-if="selectedMode !== 'edit' && selectedMode !== 'translate'">
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
                        :disabled="activeTab.dialogueName === null || activeTab.dialogueEnded || activeTab.awaitingReply"
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
                <template v-else>
                    <IconButton
                        v-if="activeTab.dialogueName"
                        icon="fa-solid fa-plus"
                        title="Add Node"
                        :disabled="dialogueEditor?.isCreatingNode?.()"
                        @click="dialogueEditor?.addNode()"
                    />
                    <IconButton
                        icon="fa-solid fa-arrows-rotate"
                        :class="{ 'animate-spin': dialogueEditor?.isLoading?.() }"
                        @click="dialogueEditor?.reload()"
                    />
                </template>
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

        <!-- Informational: a "live" (published, logged) test was force-switched to Ephemeral
             draft test mode because its dialogue was edited -->
        <Teleport to="body">
            <div v-if="editSwitchNotice" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40">
                <div class="bg-white rounded shadow-lg p-4 font-title text-sm w-96">
                    <div class="font-semibold text-orange-darker mb-2">Switched to Ephemeral Draft Test</div>
                    <p class="text-grey-dark mb-4">
                        You edited <code class="font-mono font-bold text-orange-darker">{{ editSwitchNotice.dialogueName }}</code>
                        while this test was running, so it has been restarted as an ephemeral draft test to reflect your changes.
                    </p>
                    <div class="flex justify-end">
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="editSwitchNotice = null">OK</button>
                    </div>
                </div>
            </div>
        </Teleport>

        <!-- An already-Ephemeral draft test's dialogue was edited: ask where to restart from -->
        <Teleport to="body">
            <div v-if="restartPrompt" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40">
                <div class="bg-white rounded shadow-lg p-4 font-title text-sm w-96">
                    <div class="font-semibold text-orange-darker mb-2">Dialogue Edited</div>
                    <p class="text-grey-dark mb-4">
                        You edited <code class="font-mono font-bold text-orange-darker">{{ restartPrompt.dialogueName }}</code>.
                        Restart this draft test from the start, or from
                        <code class="font-mono font-bold text-orange-darker">{{ restartPrompt.lastEditedNodeTitle }}</code>,
                        the node you last edited?
                    </p>
                    <div class="flex flex-col gap-2">
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="confirmRestartFromLastEditedNode">Restart from "{{ restartPrompt.lastEditedNodeTitle }}"</button>
                        <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="confirmRestartFromStart">Restart from Start</button>
                    </div>
                </div>
            </div>
        </Teleport>

        <!-- Active tab content -->
        <div class="relative grow min-h-0 flex flex-col">
            <MainPagePanelContainer v-if="selectedMode === 'balloon' || selectedMode === 'text'" class="-mt-px">
                <BalloonDialogueComponent
                    v-if="selectedMode === 'balloon'"
                    ref="balloons"
                    :dialogueName="activeTab.dialogueName"
                    :dialogueSteps="activeTab.dialogueSteps"
                    :dialogueEnded="activeTab.dialogueEnded"
                    :dialogueCancelled="activeTab.dialogueCancelled"
                    :awaitingReply="activeTab.awaitingReply"
                    :startError="activeTab.startError"
                    :isDraftTest="activeTab.isDraftTest"
                    @selectReply="onSelectReply"
                    @restartDialogue="restartActiveTab"
                />
                <TextDialogueComponent
                    v-if="selectedMode === 'text'"
                    ref="text-component"
                    :dialogueName="activeTab.dialogueName"
                    :dialogueSteps="activeTab.dialogueSteps"
                    :dialogueEnded="activeTab.dialogueEnded"
                    :dialogueCancelled="activeTab.dialogueCancelled"
                    :awaitingReply="activeTab.awaitingReply"
                    :startError="activeTab.startError"
                    @selectReply="onSelectReply"
                    @restartDialogue="restartActiveTab"
                />
            </MainPagePanelContainer>
            <MainPagePanelContainer v-else-if="selectedMode === 'edit'" class="-mt-px !overflow-hidden relative">
                <DialogueEditor
                    ref="dialogue-editor"
                    :dialogueName="activeTab.dialogueName"
                    @nodeChanged="onEditorNodeChanged"
                    @nodeDeleted="onEditorNodeDeleted"
                    @dialogueSaved="$emit('dialogueSaved')"
                />
            </MainPagePanelContainer>
            <MainPagePanelContainer v-else-if="selectedMode === 'translate'" class="-mt-px !overflow-hidden relative">
                <TranslationEditor :dialogueName="activeTab.dialogueName" />
            </MainPagePanelContainer>
            <div v-if="activeTab.isDraftTest && selectedMode !== 'edit' && selectedMode !== 'translate'" class="absolute bottom-3 left-3 font-mono text-[10px] text-gray-400 pointer-events-none">
                <span class="font-semibold">Ephemeral Draft Test</span><template v-if="activeTab.draftSessionId"> — Session ID: {{ activeTab.draftSessionId }}</template>
            </div>
            <div v-else-if="activeTab.loggedDialogueId && selectedMode !== 'edit' && selectedMode !== 'translate'" class="absolute bottom-3 left-3 font-mono text-[10px] text-gray-400 pointer-events-none">
                <span class="font-semibold">Logged Dialogue ID:</span> {{ activeTab.loggedDialogueId }}
            </div>
        </div>

        </div><!-- end tab bar + content wrapper -->
    </div>
</template>
