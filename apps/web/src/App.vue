<script setup>
import { inject } from 'vue';

import ProjectSelectorPage from './components/pages/ProjectSelectorPage.vue';
import MainPage from './components/pages/MainPage.vue';
import DebugConsole from './components/widgets/DebugConsole.vue';
import ErrorToast from './components/widgets/ErrorToast.vue';

const state = inject('state');
</script>

<template>
    <MainPage v-if="state.user && state.selectedProject" />
    <ProjectSelectorPage v-else-if="state.user" />
    <!-- Reachable only for the brief moment between an insufficient-privileges Keycloak logout
         (see main.js) and the browser actually navigating away. -->
    <div v-else class="min-w-screen min-h-screen bg-background flex items-center justify-center font-title">
        Redirecting to login…
    </div>
    <DebugConsole />
    <ErrorToast />
</template>
