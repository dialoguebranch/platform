<script setup>
import { inject, onMounted, onUnmounted, ref, computed, useTemplateRef } from 'vue';
import { logEvent } from '../../composables/debug-log.js';
import { describeError } from '../../composables/error-message.js';
import { showError } from '../../composables/error-toast.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { useStateManagement } from '../../composables/state-management.js';
import DialogueBrowser from '../partials/DialogueBrowser.vue';
import EditProjectMetadataModal from '../partials/EditProjectMetadataModal.vue';
import SetDelegateUserModal from '../partials/SetDelegateUserModal.vue';
import HeaderMenuItem from '../widgets/HeaderMenuItem.vue';
import InteractionTester from '../partials/InteractionTester.vue';
import ResizablePanels from '../widgets/ResizablePanels.vue';
import VariableBrowser from '../partials/VariableBrowser.vue';

const config = inject('config');
const state = inject('state');
const client = useClient();
const stateManagement = useStateManagement();

const panels = useTemplateRef('panels');
const interactionTester = useTemplateRef('interaction-tester');
const variableBrowser = useTemplateRef('variable-browser');
const dialogueBrowser = useTemplateRef('dialogue-browser');

const appVersion = __APP_VERSION__;
onUnmounted(() => {
    document.removeEventListener('click', onClickOutsideMenus);
});
const serviceUrl = new URL(config.baseUrl);
const serviceHost = serviceUrl.hostname;
const servicePort = serviceUrl.port;
const connectionInfo = ref('Not connected.');

onMounted(() => {
    document.addEventListener('click', onClickOutsideMenus);
    panels.value.selectMobileTab(0);
    client.getServerInfo()
        .then((info) => {
            connectionInfo.value = `Connected to ${serviceHost} on port ${servicePort} running Web Service v${info.serviceVersion}.`;
        })
        .catch(() => {
            connectionInfo.value = `Could not connect to ${serviceHost} on port ${servicePort}.`;
        });

    // Only the slug is persisted in the state.selectedProject cookie, so a project restored
    // from a page reload starts out without a displayName — backfill it.
    if (state.value.selectedProject && !state.value.selectedProject.displayName) {
        const slug = state.value.selectedProject.slug;
        client.getProject(slug)
            .then((project) => {
                state.value.selectedProject = { slug: project.slug, displayName: project.displayName ?? project.slug };
            })
            .catch(() => { /* keep the slug-only project; header just shows the slug */ });
    }
});

const projectMenuOpen = ref(false);
const projectMenuRef = ref(null);
const projectMenuPos = ref({ top: 0, left: 0 });

function toggleProjectMenu() {
    if (!projectMenuOpen.value && projectMenuRef.value) {
        const rect = projectMenuRef.value.getBoundingClientRect();
        projectMenuPos.value = { top: rect.bottom, left: rect.left };
    }
    projectMenuOpen.value = !projectMenuOpen.value;
}

function closeProjectMenu() {
    projectMenuOpen.value = false;
}

const userMenuOpen = ref(false);
const userMenuRef = ref(null);
const userMenuPos = ref({ top: 0, left: 0 });

function toggleUserMenu() {
    if (!userMenuOpen.value && userMenuRef.value) {
        const rect = userMenuRef.value.getBoundingClientRect();
        userMenuPos.value = { top: rect.bottom, left: rect.left };
    }
    userMenuOpen.value = !userMenuOpen.value;
}

function closeUserMenu() {
    userMenuOpen.value = false;
}

function onClickOutsideMenus(e) {
    if (projectMenuRef.value && !projectMenuRef.value.contains(e.target)) {
        projectMenuOpen.value = false;
    }
    if (userMenuRef.value && !userMenuRef.value.contains(e.target)) {
        userMenuOpen.value = false;
    }
}

function onLogoutClick() {
    stateManagement.logout();
}

function onSwitchProjectClick() {
    closeProjectMenu();
    interactionTester.value?.clearAllTabs();
    state.value.selectedProject = null;
}

const showEditMetadata = ref(false);

function onEditMetadataClick() {
    closeProjectMenu();
    showEditMetadata.value = true;
}

function onMetadataSaved(updated) {
    showEditMetadata.value = false;
    state.value.selectedProject = { slug: updated.slug, displayName: updated.displayName };
}

// Whether the currently selected project has any dialogue that's new, changed, or pending
// deletion — reported by DialogueBrowser (which already fetches this list) whenever it
// (re)loads its tree.
const hasUnpublishedChanges = ref(false);
const publishing = ref(false);
const isAdmin = computed(() => !!state.value.user?.roles?.includes('admin'));
const canPublish = computed(() => isAdmin.value && hasUnpublishedChanges.value);
const publishDisabledReason = computed(() => {
    if (!isAdmin.value) return 'Only administrators can publish projects.';
    if (!hasUnpublishedChanges.value) return 'There are no unpublished changes to publish.';
    return '';
});

const publishConfirm = ref(false);

function onPublishProjectClick() {
    if (!canPublish.value || publishing.value) return;
    closeProjectMenu();
    publishConfirm.value = true;
}

function confirmPublish() {
    publishConfirm.value = false;
    const slug = state.value.selectedProject?.slug;
    publishing.value = true;
    client.publishProject(slug)
        .then((result) => {
            if (result.success) {
                logEvent('project', 'Project $1 published as version $2', slug, result.version?.versionNumber);
                // Refresh the tree so newly-published dialogues show their "Published" badge.
                dialogueBrowser.value?.listDialogues();
            } else {
                const fileCount = Object.keys(result.errors ?? {}).length;
                const errorCount = Object.values(result.errors ?? {})
                    .reduce((sum, list) => sum + list.length, 0);
                showError(`Publishing failed: ${errorCount} validation error${errorCount === 1 ? '' : 's'} ` +
                    `across ${fileCount} dialogue${fileCount === 1 ? '' : 's'}. See the Debug Console for details.`);
            }
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            publishing.value = false;
        });
}

const activeDelegateUser = ref(null);
const showDelegateModal = ref(false);
const delegateConfirmAction = ref(null);

function onSetDelegateUserClick() {
    closeUserMenu();
    showDelegateModal.value = true;
}

function onDelegateApply(username) {
    showDelegateModal.value = false;
    delegateConfirmAction.value = () => {
        client.delegateUser = username || null;
        activeDelegateUser.value = client.delegateUser;
        variableBrowser.value?.loadVariables();
    };
}

function onDelegateClear() {
    showDelegateModal.value = false;
    delegateConfirmAction.value = () => {
        client.delegateUser = null;
        activeDelegateUser.value = null;
        variableBrowser.value?.loadVariables();
    };
}

function confirmDelegateAction() {
    interactionTester.value?.clearAllTabs();
    delegateConfirmAction.value?.();
    delegateConfirmAction.value = null;
}

function onResumeDialogue(dialogueName) {
    panels.value.selectMobileTab(1);
    interactionTester.value.resumeDialogue(dialogueName);
}

function onOpenDialogue(dialogueName, isDraft) {
    panels.value.selectMobileTab(1);
    interactionTester.value.openDialogue(dialogueName, isDraft);
}

function onNewDialogueStep() {
    variableBrowser.value.loadVariables();
}

function onDialogueSaved() {
    dialogueBrowser.value?.listDialogues();
}

function onChangeVariable() {
    interactionTester.value.reloadStep();
}

function onActivateTab(tabId) {
    panels.value.selectMobileTab(1);
    interactionTester.value.activateTab(tabId);
}

function onResizePanels() {
    interactionTester.value.resize();
}
</script>

<template>
    <div class="w-screen h-screen flex flex-col">
        <header class="flex items-stretch bg-orange-darker shadow-md shadow-gray-400 z-[100]">
            <a class="shrink-0 border-r border-orange-dark" href="/"><img class="box-content h-[60px] pl-4 pr-4 py-3" src="../../assets/img/dlb-logo-medium-bright.png"></a>
            <!-- Project dropdown -->
            <div ref="projectMenuRef" class="hidden sm:flex items-stretch relative border-r border-orange-dark">
                <button
                    type="button"
                    class="flex items-center gap-3 px-4 hover:bg-orange-dark cursor-pointer transition-colors"
                    @click="toggleProjectMenu"
                >
                    <div class="flex flex-col justify-center leading-tight text-left">
                        <span class="font-title text-[10px] text-orange-light uppercase tracking-wide">Project</span>
                        <span class="font-title text-sm font-bold text-white">{{ state.selectedProject?.displayName }}</span>
                        <span class="font-mono text-[10px] text-orange-light">{{ state.selectedProject?.slug }}</span>
                    </div>
                    <FontAwesomeIcon :icon="projectMenuOpen ? 'fa-solid fa-caret-up' : 'fa-solid fa-caret-down'" class="text-orange-light text-xs" />
                </button>

            </div>

            <Teleport to="body">
                <div v-if="projectMenuOpen"
                    class="fixed z-[9999] min-w-[200px] bg-white shadow-lg border border-grey-light rounded-b overflow-hidden"
                    :style="{ top: projectMenuPos.top + 'px', left: projectMenuPos.left + 'px' }"
                >
                    <button
                        type="button"
                        :class="['flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm transition-colors', state.user?.roles?.includes('admin') ? 'text-orange-darker hover:bg-grey-lighter cursor-pointer' : 'text-grey-light cursor-not-allowed']"
                        :disabled="!state.user?.roles?.includes('admin')"
                        :title="state.user?.roles?.includes('admin') ? '' : 'Only administrators can edit project metadata'"
                        @click="onEditMetadataClick"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-pen" class="w-4" :class="state.user?.roles?.includes('admin') ? 'text-orange-medium' : 'text-grey-light'" />
                        Edit Metadata
                    </button>
                    <button
                        type="button"
                        :class="['flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm transition-colors', canPublish ? 'text-orange-darker hover:bg-grey-lighter cursor-pointer' : 'text-grey-light cursor-not-allowed']"
                        :disabled="!canPublish || publishing"
                        :title="publishDisabledReason"
                        @click="onPublishProjectClick"
                    >
                        <FontAwesomeIcon v-if="publishing" icon="fa-solid fa-circle-notch" class="w-4 animate-spin text-orange-medium" />
                        <FontAwesomeIcon v-else icon="fa-solid fa-rocket" class="w-4" :class="canPublish ? 'text-orange-medium' : 'text-grey-light'" />
                        Publish Project
                    </button>
                    <div class="border-t border-grey-light mx-3"></div>
                    <button type="button" class="flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm text-orange-darker hover:bg-grey-lighter cursor-pointer transition-colors" @click="onSwitchProjectClick">
                        <FontAwesomeIcon icon="fa-solid fa-folder-minus" class="w-4 text-orange-medium" />
                        Close Project
                    </button>
                </div>
            </Teleport>

            <!-- User dropdown -->
            <div ref="userMenuRef" class="hidden sm:flex items-stretch relative border-r border-orange-dark">
                <button
                    type="button"
                    class="flex items-center gap-3 px-4 hover:bg-orange-dark cursor-pointer transition-colors"
                    @click="toggleUserMenu"
                >
                    <div class="flex flex-col justify-center leading-tight text-left">
                        <span class="font-title text-[10px] text-orange-light uppercase tracking-wide">User</span>
                        <span class="font-title text-sm font-bold text-white">
                            {{ state.user?.name }}
                            <span v-if="activeDelegateUser" class="font-normal text-orange-light text-xs"> → {{ activeDelegateUser }}</span>
                        </span>
                        <span class="font-mono text-[10px] text-orange-light">{{ Array.isArray(state.user?.roles) ? state.user.roles.join(', ') : state.user?.roles }}</span>
                    </div>
                    <FontAwesomeIcon :icon="userMenuOpen ? 'fa-solid fa-caret-up' : 'fa-solid fa-caret-down'" class="text-orange-light text-xs" />
                </button>
            </div>

            <Teleport to="body">
                <div v-if="userMenuOpen"
                    class="fixed z-[9999] min-w-[200px] bg-white shadow-lg border border-grey-light rounded-b overflow-hidden"
                    :style="{ top: userMenuPos.top + 'px', left: userMenuPos.left + 'px' }"
                >
                    <button type="button" class="flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm text-orange-darker hover:bg-grey-lighter cursor-pointer transition-colors" @click="onSetDelegateUserClick">
                        <FontAwesomeIcon icon="fa-solid fa-user-gear" class="w-4 text-orange-medium" />
                        Set Delegate User
                        <span v-if="activeDelegateUser" class="ml-auto text-xs font-mono text-orange-darker bg-orange-light/30 px-1.5 py-0.5 rounded">{{ activeDelegateUser }}</span>
                    </button>
                    <div class="border-t border-grey-light mx-3"></div>
                    <button type="button" class="flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm text-orange-darker hover:bg-grey-lighter cursor-pointer transition-colors" @click="onLogoutClick">
                        <FontAwesomeIcon icon="fa-solid fa-right-from-bracket" class="w-4 text-orange-medium" />
                        Log out
                    </button>
                </div>
            </Teleport>

            <div class="hidden sm:flex self-stretch">
                <HeaderMenuItem text="Documentation" icon="fa-solid fa-arrow-up-right-from-square" link="https://www.dialoguebranch.com/docs/dialogue-branch/dev/index.html" />
            </div>
            <div class="grow"></div>
        </header>

        <Teleport to="body">
            <div v-if="delegateConfirmAction" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40">
                <div class="bg-white rounded-xl shadow-2xl p-5 font-title text-sm w-96">
                    <div class="font-bold text-orange-darker mb-2 flex items-center gap-2">
                        <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="text-orange-medium" />
                        Change Delegate User
                    </div>
                    <p class="text-grey-dark mb-4">All ongoing dialogues will be lost. Are you sure you want to proceed?</p>
                    <div class="flex gap-2 justify-end">
                        <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="delegateConfirmAction = null">Cancel</button>
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="confirmDelegateAction">Okay, proceed</button>
                    </div>
                </div>
            </div>
        </Teleport>

        <Teleport to="body">
            <div v-if="publishConfirm" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40">
                <div class="bg-white rounded-xl shadow-2xl p-5 font-title text-sm w-96">
                    <div class="font-bold text-orange-darker mb-2 flex items-center gap-2">
                        <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="text-orange-medium" />
                        Publish Project
                    </div>
                    <p class="text-grey-dark mb-4">
                        This publishes the current draft state of every dialogue in this project as a new version,
                        immediately replacing what's live. This cannot be undone. Continue?
                    </p>
                    <div class="flex gap-2 justify-end">
                        <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="publishConfirm = false">Cancel</button>
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="confirmPublish">Publish</button>
                    </div>
                </div>
            </div>
        </Teleport>

        <ResizablePanels
            ref="panels"
            class="grow"
            cookiePrefix="mainPage"
            :mobileTabNames="['Dialogues', 'Interactions', 'Variables']"
            @resize="onResizePanels()"
        >
            <template #left>
                <DialogueBrowser
                    ref="dialogue-browser"
                    class="grow"
                    :openTabs="interactionTester?.tabs ?? []"
                    @resumeDialogue="onResumeDialogue"
                    @activateTab="onActivateTab"
                    @hasUnpublishedChanges="hasUnpublishedChanges = $event"
                    @openDialogue="onOpenDialogue"
                />
            </template>
            <template #main>
                <InteractionTester ref="interaction-tester" class="grow" @newDialogueStep="onNewDialogueStep" @dialogueSaved="onDialogueSaved" />
            </template>
            <template #right>
                <VariableBrowser ref="variable-browser" class="grow" @changeVariable="onChangeVariable" />
            </template>
        </ResizablePanels>

        <EditProjectMetadataModal
            v-if="showEditMetadata"
            :projectSlug="state.selectedProject?.slug"
            @close="showEditMetadata = false"
            @saved="onMetadataSaved"
        />

        <SetDelegateUserModal
            v-if="showDelegateModal"
            :currentDelegateUser="activeDelegateUser"
            @close="showDelegateModal = false"
            @apply="onDelegateApply"
            @clear="onDelegateClear"
        />

        <footer class="shrink-0 hidden sm:flex items-center gap-4 px-4 py-1 bg-grey-lighter border-t border-grey-light font-mono text-[11px] text-gray-400">
            <span>Logged in as <span class="text-gray-500 font-semibold">{{ state.user.name }}</span></span>
            <span class="text-grey-light">|</span>
            <span>Dialogue Branch Web Client v{{ appVersion }}</span>
            <span class="text-grey-light">|</span>
            <span>{{ connectionInfo }}</span>
        </footer>
    </div>
</template>
