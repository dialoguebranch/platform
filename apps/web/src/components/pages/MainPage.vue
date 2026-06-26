<script setup>
import { inject, onMounted, onUnmounted, ref, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { useStateManagement } from '../../composables/state-management.js';
import DialogueBrowser from '../partials/DialogueBrowser.vue';
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

const appVersion = __APP_VERSION__;
const sessionSecondsToLive = ref(Math.round(state.value.user.accessTokenSecondsToLive));
let refreshing = false;
const sessionTimer = setInterval(() => {
    sessionSecondsToLive.value = Math.round(state.value.user.accessTokenSecondsToLive);
    if (sessionSecondsToLive.value < 30 && !refreshing) {
        refreshing = true;
        stateManagement.refreshSession().finally(() => { refreshing = false; });
    }
}, 1000);
onUnmounted(() => clearInterval(sessionTimer));
const serviceUrl = new URL(config.baseUrl);
const serviceHost = serviceUrl.hostname;
const servicePort = serviceUrl.port;
const connectionInfo = ref('Not connected.');

onMounted(() => {
    panels.value.selectMobileTab(0);
    client.getServerInfo()
        .then((info) => {
            connectionInfo.value = `Connected to ${serviceHost} on port ${servicePort} running Web Service v${info.serviceVersion}.`;
        })
        .catch(() => {
            connectionInfo.value = `Could not connect to ${serviceHost} on port ${servicePort}.`;
        });
});

function onLogoutClick() {
    stateManagement.logout();
}

function onSwitchProjectClick() {
    interactionTester.value?.clearAllTabs();
    state.value.selectedProject = null;
}

const advancedOpen = ref(false);
const delegateUserInput = ref('');
const activeDelegateUser = ref(null);
const delegateConfirmAction = ref(null); // pending function to run on confirm

function applyDelegateUser() {
    delegateConfirmAction.value = () => {
        const val = delegateUserInput.value.trim();
        client.delegateUser = val || null;
        activeDelegateUser.value = client.delegateUser;
        variableBrowser.value?.loadVariables();
    };
}

function clearDelegateUser() {
    delegateConfirmAction.value = () => {
        delegateUserInput.value = '';
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

function onSelectDialogue(dialogueName) {
    panels.value.selectMobileTab(1);
    interactionTester.value.loadDialogue(dialogueName);
}

function onResumeDialogue(dialogueName) {
    panels.value.selectMobileTab(1);
    interactionTester.value.resumeDialogue(dialogueName);
}

function onNewDialogueStep() {
    variableBrowser.value.loadVariables();
}

function onChangeVariable() {
    interactionTester.value.reloadStep();
}

function onResizePanels() {
    interactionTester.value.resize();
}
</script>

<template>
    <div class="w-screen h-screen flex flex-col">
        <header class="flex items-stretch bg-orange-darker shadow-md shadow-gray-400 z-1">
            <a class="shrink-0 border-r border-orange-dark" href="/"><img class="box-content h-[60px] pl-4 pr-4 py-3" src="../../assets/img/dlb-logo-medium-bright.png"></a>
            <div class="hidden sm:flex items-center px-4 border-r border-orange-dark gap-3">
                <div class="flex flex-col justify-center leading-tight">
                    <span class="font-title text-[10px] text-orange-light uppercase tracking-wide">Project</span>
                    <span class="font-title text-sm font-bold text-white">{{ state.selectedProject?.displayName }}</span>
                    <span class="font-mono text-[10px] text-orange-light">{{ state.selectedProject?.name }}</span>
                </div>
            </div>
            <div class="hidden sm:flex self-stretch">
                <HeaderMenuItem text="Documentation" icon="fa-solid fa-arrow-up-right-from-square" link="https://www.dialoguebranch.com/docs/dialogue-branch/dev/index.html" />
                <HeaderMenuItem text="Close Project" icon="fa-solid fa-folder-minus" @click="onSwitchProjectClick" />
                <HeaderMenuItem text="Log out" icon="fa-solid fa-right-from-bracket" @click="onLogoutClick" />
            </div>
            <div class="grow"></div>
        </header>

        <!-- Advanced Options bar (admin only) -->
        <div v-if="state.user?.roles?.includes('admin')" class="shrink-0 bg-grey-lighter border-b border-grey-light text-xs font-title">
            <button
                type="button"
                class="flex items-center gap-1.5 w-full px-3 py-1 text-grey-dark hover:text-orange-darker cursor-pointer select-none"
                @click="advancedOpen = !advancedOpen"
            >
                <FontAwesomeIcon :icon="advancedOpen ? 'fa-solid fa-caret-down' : 'fa-solid fa-caret-right'" class="w-3" />
                <span>Advanced Options<template v-if="activeDelegateUser"> (acting as <code class="font-mono font-bold text-orange-darker">{{ activeDelegateUser }}</code>)</template></span>
            </button>
            <div v-if="advancedOpen" class="flex items-center gap-3 px-4 pb-2">
                <label class="text-grey-dark shrink-0">Delegate User:</label>
                <input
                    v-model="delegateUserInput"
                    type="text"
                    placeholder="Username..."
                    class="px-2 py-0.5 border border-grey-light rounded bg-white text-grey-dark focus:outline-none focus:border-orange-dark w-48"
                    @keyup.enter="applyDelegateUser"
                />
                <button
                    type="button"
                    class="px-2 py-0.5 rounded bg-orange-darker text-white hover:bg-orange-dark cursor-pointer"
                    @click="applyDelegateUser"
                >Apply</button>
                <button
                    v-if="activeDelegateUser"
                    type="button"
                    class="px-2 py-0.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter cursor-pointer"
                    @click="clearDelegateUser"
                >Clear</button>
            </div>
        </div>

        <Teleport to="body">
            <div v-if="delegateConfirmAction" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40">
                <div class="bg-white rounded shadow-lg p-4 font-title text-sm w-80">
                    <div class="font-semibold text-orange-darker mb-2">Change Delegate User</div>
                    <p class="text-grey-dark mb-4">All ongoing dialogues will be lost. Are you sure you want to proceed?</p>
                    <div class="flex gap-2 justify-end">
                        <button type="button" class="px-3 py-1.5 rounded border border-grey-light text-grey-dark hover:bg-grey-lighter text-xs font-semibold cursor-pointer" @click="delegateConfirmAction = null">Cancel</button>
                        <button type="button" class="px-3 py-1.5 rounded bg-orange-darker text-white hover:bg-orange-dark text-xs font-semibold cursor-pointer" @click="confirmDelegateAction">Okay, proceed</button>
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
                <DialogueBrowser class="grow" @selectDialogue="onSelectDialogue" @resumeDialogue="onResumeDialogue" />
            </template>
            <template #main>
                <InteractionTester ref="interaction-tester" class="grow" @newDialogueStep="onNewDialogueStep" />
            </template>
            <template #right>
                <VariableBrowser ref="variable-browser" class="grow" @changeVariable="onChangeVariable" />
            </template>
        </ResizablePanels>

        <footer class="shrink-0 hidden sm:flex items-center gap-4 px-4 py-1 bg-grey-lighter border-t border-grey-light font-mono text-[11px] text-gray-400">
            <span>Logged in as <span class="text-gray-500 font-semibold">{{ state.user.name }}</span></span>
            <span class="text-grey-light">|</span>
            <span>Dialogue Branch Web Client v{{ appVersion }}</span>
            <span class="text-grey-light">|</span>
            <span>{{ connectionInfo }}</span>
        </footer>
    </div>
</template>
