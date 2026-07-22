<script setup>
import { inject, onMounted, onUnmounted, ref, computed, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { useStateManagement } from '../../composables/state-management.js';
import DialogueBrowser from '../partials/DialogueBrowser.vue';
import ConfigureProjectModal from '../partials/ConfigureProjectModal.vue';
import PublishProjectWizardModal from '../partials/PublishProjectWizardModal.vue';
import SetDelegateUserModal from '../partials/SetDelegateUserModal.vue';
import TechnicalInfoModal from '../partials/TechnicalInfoModal.vue';
import HeaderMenuItem from '../widgets/HeaderMenuItem.vue';
import DialogueWorkspace from '../partials/DialogueWorkspace.vue';
import ResizablePanels from '../widgets/ResizablePanels.vue';
import VariableBrowser from '../partials/VariableBrowser.vue';
import { DLB_APP_MODE_LIVE, DLB_APP_MODE_DRAFT } from '../../dlb-lib/WCTAClientState.js';
import { showError } from '../../composables/error-toast.js';
import { describeError } from '../../composables/error-message.js';

const config = inject('config');
const state = inject('state');
const client = useClient();
const stateManagement = useStateManagement();

const panels = useTemplateRef('panels');
const dialogueWorkspace = useTemplateRef('dialogue-workspace');
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
const showTechnicalInfo = ref(false);

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

    // MainPage only ever mounts once a project is selected (see App.vue), so this always runs
    // exactly once per project — fetch it both to backfill the displayName (only the slug is
    // persisted in the state.selectedProject cookie, so a project restored from a page reload
    // starts out without one) and to seed the unpublished-metadata-changes indicator.
    if (state.value.selectedProject) {
        const slug = state.value.selectedProject.slug;
        client.getProject(slug)
            .then((project) => {
                state.value.selectedProject = { slug: project.slug, displayName: project.latestVersion?.displayName ?? project.draftDisplayName ?? project.slug, latestVersion: project.latestVersion ?? null };
                refreshProjectMetadataChanged(project);
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
    if (modeMenuRef.value && !modeMenuRef.value.contains(e.target)) {
        modeMenuOpen.value = false;
    }
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

// The global Live/Draft mode toggle. Switching modes clears all open tabs (same as switching
// project or delegate user, below) since a tab's running test session is tied to whichever
// execution path (published vs. draft) was active when it started, and refreshes the Dialogue
// Browser so it lists the right set of dialogues for the new mode.
const modeMenuOpen = ref(false);
const modeMenuRef = ref(null);
const modeMenuPos = ref({ top: 0, left: 0 });

const modeLabel = computed(() => state.value.mode === DLB_APP_MODE_DRAFT ? 'Authoring Mode' : 'Live Mode');
const modeDescription = computed(() => state.value.mode === DLB_APP_MODE_DRAFT
    ? 'Edit and test draft dialogues'
    : 'Test published dialogues');

function toggleModeMenu() {
    if (!modeMenuOpen.value && modeMenuRef.value) {
        const rect = modeMenuRef.value.getBoundingClientRect();
        modeMenuPos.value = { top: rect.bottom, left: rect.left };
    }
    modeMenuOpen.value = !modeMenuOpen.value;
}

function closeModeMenu() {
    modeMenuOpen.value = false;
}

function selectMode(mode) {
    closeModeMenu();
    if (state.value.mode === mode) return;
    state.value.mode = mode;
    dialogueWorkspace.value?.clearAllTabs();
    dialogueBrowser.value?.listDialogues();
    // The "Test dialogues in:" selector offers draft languages in Authoring Mode and published
    // languages in Live Mode — refresh it so switching modes doesn't leave it showing the other
    // mode's list.
    dialogueWorkspace.value?.loadAvailableLanguages();
}

function onSwitchProjectClick() {
    closeProjectMenu();
    dialogueWorkspace.value?.clearAllTabs();
    state.value.selectedProject = null;
}

const showConfigureProject = ref(false);
const isAdmin = computed(() => !!state.value.user?.roles?.includes('admin'));

const canConfigureProject = computed(() =>
    isAdmin.value && state.value.mode === DLB_APP_MODE_DRAFT);
const configureProjectDisabledReason = computed(() => {
    if (!isAdmin.value) return 'Only administrators can configure projects.';
    if (state.value.mode !== DLB_APP_MODE_DRAFT) return 'Switch to Authoring Mode to configure the project.';
    return '';
});

function onConfigureProjectClick() {
    if (!canConfigureProject.value) return;
    closeProjectMenu();
    showConfigureProject.value = true;
}

function onProjectConfigured(updated) {
    showConfigureProject.value = false;
    state.value.selectedProject = { ...state.value.selectedProject, slug: updated.slug, displayName: updated.latestVersion?.displayName ?? updated.draftDisplayName ?? updated.slug };
    // The modal only reports the fields it itself changed — re-fetch to also pick up any
    // translation-language additions/removals when recomputing the unpublished-changes state.
    client.getProject(updated.slug)
        .then((project) => refreshProjectMetadataChanged(project))
        .catch(() => { /* leave the previous unpublished-changes state as-is */ });
    // Configure Project may have added/removed a translation language — refresh the "Test
    // dialogues in:" selector so it reflects the latest list without needing a page reload.
    dialogueWorkspace.value?.loadAvailableLanguages();
}

// Whether the currently selected project's draft metadata (display name, description, or
// translation-language registry) differs from what's currently published — seeded on mount (see
// onMounted above) and refreshed after every Configure Project save.
const projectMetadataChanged = ref(false);

function refreshProjectMetadataChanged(project) {
    projectMetadataChanged.value = project.draftDisplayName !== (project.latestVersion?.displayName ?? null)
        || project.draftDescription !== (project.latestVersion?.description ?? null)
        || (project.draftTranslationLanguages ?? []).some((l) => l.isNew || l.isDeleted);
}

// Whether the currently selected project has any dialogue that's new, changed, or pending
// deletion — reported by DialogueBrowser (which already fetches this list) whenever it
// (re)loads its tree.
const hasUnpublishedDialogueChanges = ref(false);
const hasUnpublishedChanges = computed(() =>
    hasUnpublishedDialogueChanges.value || projectMetadataChanged.value);
const canPublish = computed(() =>
    isAdmin.value && hasUnpublishedChanges.value && state.value.mode === DLB_APP_MODE_DRAFT);
const publishDisabledReason = computed(() => {
    if (!isAdmin.value) return 'Only administrators can publish projects.';
    if (state.value.mode !== DLB_APP_MODE_DRAFT) return 'Switch to Authoring Mode to publish.';
    if (!hasUnpublishedChanges.value) return 'There are no unpublished changes to publish.';
    return '';
});

const showPublishWizard = ref(false);

function onPublishProjectClick() {
    if (!canPublish.value) return;
    closeProjectMenu();
    showPublishWizard.value = true;
}

// Export downloads the project's *published* content — deliberately available only in Live Mode
// (unlike Configure/Publish, which are Authoring-Mode-only) since it's a snapshot of what's
// actually live, not the in-progress draft.
const exporting = ref(false);
const canExportProject = computed(() =>
    isAdmin.value && state.value.mode === DLB_APP_MODE_LIVE
    && !!state.value.selectedProject?.latestVersion);
const exportProjectDisabledReason = computed(() => {
    if (!isAdmin.value) return 'Only administrators can export projects.';
    if (state.value.mode !== DLB_APP_MODE_LIVE) return 'Switch to Live Mode to export the project.';
    if (!state.value.selectedProject?.latestVersion) return 'This project has not been published yet.';
    return '';
});

function onExportProjectClick() {
    if (!canExportProject.value || exporting.value) return;
    closeProjectMenu();
    const slug = state.value.selectedProject.slug;
    exporting.value = true;
    client.exportProject(slug)
        .then((blob) => {
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = slug + '.zip';
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            URL.revokeObjectURL(url);
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            exporting.value = false;
        });
}

function onProjectPublished(version) {
    showPublishWizard.value = false;
    if (state.value.selectedProject) {
        state.value.selectedProject = { ...state.value.selectedProject, latestVersion: version ?? state.value.selectedProject.latestVersion };
    }
    // A successful publish reconciles metadata/language changes too — not just dialogue content.
    projectMetadataChanged.value = false;
    // Refresh the tree so newly-published dialogues show their "Published" badge.
    dialogueBrowser.value?.listDialogues();
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
    dialogueWorkspace.value?.clearAllTabs();
    delegateConfirmAction.value?.();
    delegateConfirmAction.value = null;
}

function onResumeDialogue(dialogueName) {
    panels.value.selectMobileTab(1);
    dialogueWorkspace.value.resumeDialogue(dialogueName);
}

function onOpenDialogue(dialogueName) {
    panels.value.selectMobileTab(1);
    // Which execution path (published vs. draft) to test against is now decided by the global
    // Live/Draft mode toggle, which DialogueWorkspace reads directly from state.
    dialogueWorkspace.value.openDialogue(dialogueName);
}

function onNewDialogueStep() {
    variableBrowser.value?.loadVariables();
}

function onDialogueSaved() {
    dialogueBrowser.value?.listDialogues();
}

function onChangeVariable() {
    dialogueWorkspace.value.reloadStep();
}

function onActivateTab(tabId) {
    panels.value.selectMobileTab(1);
    dialogueWorkspace.value.activateTab(tabId);
}

function onResizePanels() {
    dialogueWorkspace.value.resize();
}

function onWorkspaceModeChanged(mode) {
    if (mode === 'edit') {
        panels.value?.collapseRightPanel();
    }
}
</script>

<template>
    <div class="w-screen h-screen flex flex-col">
        <header class="flex items-stretch bg-orange-darker shadow-md shadow-gray-400 z-[100]">
            <a class="shrink-0 border-r border-orange-dark" href="/"><img class="box-content h-[60px] pl-4 pr-4 py-3" src="../../assets/img/dlb-logo-medium-bright.png"></a>

            <!-- Mode dropdown -->
            <div ref="modeMenuRef" class="hidden sm:flex items-stretch relative border-r border-orange-dark">
                <button
                    type="button"
                    class="flex items-center gap-3 px-4 hover:bg-orange-dark cursor-pointer transition-colors"
                    @click="toggleModeMenu"
                >
                    <div class="flex flex-col justify-center leading-tight text-left">
                        <span class="flex items-center gap-1 font-title text-[10px] text-orange-light uppercase tracking-wide">
                            <FontAwesomeIcon icon="fa-solid fa-sliders" class="text-[9px]" />
                            Mode
                        </span>
                        <span class="font-title text-sm font-bold text-white">{{ modeLabel }}</span>
                        <span class="font-mono text-[10px] text-orange-light">{{ modeDescription }}</span>
                    </div>
                    <FontAwesomeIcon :icon="modeMenuOpen ? 'fa-solid fa-caret-up' : 'fa-solid fa-caret-down'" class="text-orange-light text-xs" />
                </button>
            </div>

            <Teleport to="body">
                <div v-if="modeMenuOpen"
                    class="fixed z-[9999] min-w-[200px] bg-white shadow-lg border border-grey-light rounded-b overflow-hidden"
                    :style="{ top: modeMenuPos.top + 'px', left: modeMenuPos.left + 'px' }"
                >
                    <button
                        type="button"
                        :class="['flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm transition-colors', state.mode === DLB_APP_MODE_LIVE ? 'text-orange-darker bg-grey-lighter cursor-default' : 'text-orange-darker hover:bg-grey-lighter cursor-pointer']"
                        @click="selectMode(DLB_APP_MODE_LIVE)"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-play" class="w-4 text-orange-medium" />
                        Live Mode
                    </button>
                    <button
                        type="button"
                        :class="['flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm transition-colors', state.mode === DLB_APP_MODE_DRAFT ? 'text-orange-darker bg-grey-lighter cursor-default' : 'text-orange-darker hover:bg-grey-lighter cursor-pointer']"
                        @click="selectMode(DLB_APP_MODE_DRAFT)"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-pen" class="w-4 text-orange-medium" />
                        Authoring Mode
                    </button>
                </div>
            </Teleport>

            <!-- Project dropdown -->
            <div ref="projectMenuRef" class="hidden sm:flex items-stretch relative border-r border-orange-dark">
                <button
                    type="button"
                    class="flex items-center gap-3 px-4 hover:bg-orange-dark cursor-pointer transition-colors"
                    @click="toggleProjectMenu"
                >
                    <div class="flex flex-col justify-center leading-tight text-left">
                        <span class="flex items-center gap-1 font-title text-[10px] text-orange-light uppercase tracking-wide">
                            <FontAwesomeIcon icon="fa-solid fa-folder" class="text-[9px]" />
                            Project
                        </span>
                        <span class="font-title text-sm font-bold text-white">{{ state.selectedProject?.displayName }}</span>
                        <span class="font-mono text-[10px] text-orange-light">{{ state.selectedProject?.slug }}<template v-if="state.selectedProject?.latestVersion"> (v{{ state.selectedProject.latestVersion.versionNumber }})</template></span>
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
                        :class="['flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm transition-colors', canConfigureProject ? 'text-orange-darker hover:bg-grey-lighter cursor-pointer' : 'text-grey-light cursor-not-allowed']"
                        :disabled="!canConfigureProject"
                        :title="configureProjectDisabledReason"
                        @click="onConfigureProjectClick"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-sliders" class="w-4" :class="canConfigureProject ? 'text-orange-medium' : 'text-grey-light'" />
                        Configure Project
                    </button>
                    <button
                        type="button"
                        :class="['flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm transition-colors', canPublish ? 'text-orange-darker hover:bg-grey-lighter cursor-pointer' : 'text-grey-light cursor-not-allowed']"
                        :disabled="!canPublish"
                        :title="publishDisabledReason"
                        @click="onPublishProjectClick"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-rocket" class="w-4" :class="canPublish ? 'text-orange-medium' : 'text-grey-light'" />
                        Publish Project
                    </button>
                    <button
                        type="button"
                        :class="['flex items-center gap-3 w-full px-4 py-2.5 font-title text-sm transition-colors', (canExportProject && !exporting) ? 'text-orange-darker hover:bg-grey-lighter cursor-pointer' : 'text-grey-light cursor-not-allowed']"
                        :disabled="!canExportProject || exporting"
                        :title="exportProjectDisabledReason"
                        @click="onExportProjectClick"
                    >
                        <FontAwesomeIcon :icon="exporting ? 'fa-solid fa-circle-notch' : 'fa-solid fa-file-export'" :class="['w-4', canExportProject ? 'text-orange-medium' : 'text-grey-light', { 'animate-spin': exporting }]" />
                        Export Project
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
                        <span class="flex items-center gap-1 font-title text-[10px] text-orange-light uppercase tracking-wide">
                            <FontAwesomeIcon icon="fa-solid fa-user" class="text-[9px]" />
                            User
                        </span>
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

        <PublishProjectWizardModal
            v-if="showPublishWizard"
            :projectSlug="state.selectedProject?.slug"
            :projectDisplayName="state.selectedProject?.displayName ?? state.selectedProject?.slug"
            @close="showPublishWizard = false"
            @published="onProjectPublished"
        />

        <ResizablePanels
            ref="panels"
            class="grow"
            cookiePrefix="mainPage"
            :mobileTabNames="['Dialogues', 'Interactions', 'Variables']"
            rightPanelLabel="Variables"
            @resize="onResizePanels()"
        >
            <template #left>
                <DialogueBrowser
                    ref="dialogue-browser"
                    class="grow"
                    :openTabs="dialogueWorkspace?.tabs ?? []"
                    @resumeDialogue="onResumeDialogue"
                    @activateTab="onActivateTab"
                    @hasUnpublishedChanges="hasUnpublishedDialogueChanges = $event"
                    @openDialogue="onOpenDialogue"
                />
            </template>
            <template #main>
                <DialogueWorkspace ref="dialogue-workspace" class="grow" @newDialogueStep="onNewDialogueStep" @dialogueSaved="onDialogueSaved" @modeChanged="onWorkspaceModeChanged" />
            </template>
            <template #right>
                <VariableBrowser ref="variable-browser" class="grow" @changeVariable="onChangeVariable" @collapse="panels?.collapseRightPanel()" />
            </template>
        </ResizablePanels>

        <ConfigureProjectModal
            v-if="showConfigureProject"
            :projectSlug="state.selectedProject?.slug"
            @close="showConfigureProject = false"
            @saved="onProjectConfigured"
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
            <button
                v-if="isAdmin"
                class="flex items-center text-gray-400 hover:text-orange-darker cursor-pointer"
                title="Technical Information"
                @click="showTechnicalInfo = true"
            >
                <FontAwesomeIcon icon="fa-solid fa-circle-info" />
            </button>
        </footer>

        <TechnicalInfoModal
            v-if="showTechnicalInfo"
            @close="showTechnicalInfo = false"
        />
    </div>
</template>
