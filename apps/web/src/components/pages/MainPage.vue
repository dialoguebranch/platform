<script setup>
import { inject, onMounted, onUnmounted, ref, useTemplateRef } from 'vue';
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

function onSelectDialogue(dialogueName) {
    panels.value.selectMobileTab(1);
    interactionTester.value.loadDialogue(dialogueName);
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
        <header class="flex bg-menu-bar shadow-md shadow-gray-400 z-1">
            <a class="shrink-0" href="/"><img class="box-content h-[60px] pl-4 py-3" src="../../assets/img/dlb-square.png"></a>
            <div class="hidden sm:flex flex-col justify-center pl-2 font-mono text-xs text-gray-500">
                <span>Logged in as {{ state.user.name }} (session valid for {{ sessionSecondsToLive }}s).</span>
                <span>Dialogue Branch Web Client v{{ appVersion }}.</span>
                <span>{{ connectionInfo }}</span>
            </div>
            <div class="grow"></div>
            <div class="flex basis-0">
                <HeaderMenuItem text="Documentation" link="https://www.dialoguebranch.com/docs/dialogue-branch/dev/index.html" />
                <HeaderMenuItem text="Log out" @click="onLogoutClick" />
            </div>
        </header>

        <ResizablePanels
            ref="panels"
            class="grow"
            cookiePrefix="mainPage"
            :mobileTabNames="['Dialogues', 'Interactions', 'Variables']"
            @resize="onResizePanels()"
        >
            <template #left>
                <DialogueBrowser class="grow" @selectDialogue="onSelectDialogue" />
            </template>
            <template #main>
                <InteractionTester ref="interaction-tester" class="grow" @newDialogueStep="onNewDialogueStep" />
            </template>
            <template #right>
                <VariableBrowser ref="variable-browser" class="grow" @changeVariable="onChangeVariable" />
            </template>
        </ResizablePanels>
    </div>
</template>
