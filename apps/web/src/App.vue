<script setup>
import { computed, inject } from 'vue';

import ProjectSelectorPage from './components/pages/ProjectSelectorPage.vue';
import MainPage from './components/pages/MainPage.vue';
import ParticipantPage from './components/pages/ParticipantPage.vue';
import DebugConsole from './components/widgets/DebugConsole.vue';
import ErrorToast from './components/widgets/ErrorToast.vue';

const state = inject('state');

// A participant-only user (no editor/admin) can't call listProjects/getProject/listDialogues —
// all editor/admin-only end-points — so they can't use ProjectSelectorPage/MainPage at all.
// Checked ahead of the other branches so that an editor/admin who also happens to carry the
// participant role (a superset assignment) still gets the normal app, per spec.
const isParticipantOnly = computed(() => {
    const roles = state.value.user?.roles ?? [];
    return roles.includes('participant') && !roles.includes('editor') && !roles.includes('admin');
});
</script>

<template>
    <ParticipantPage v-if="isParticipantOnly" />
    <MainPage v-else-if="state.user && state.selectedProject" />
    <ProjectSelectorPage v-else-if="state.user" />
    <!-- Reachable only for the brief moment between an insufficient-privileges Keycloak logout
         (see main.js) and the browser actually navigating away. -->
    <div v-else class="min-w-screen min-h-screen bg-background flex items-center justify-center font-title">
        Redirecting to login…
    </div>
    <DebugConsole />
    <ErrorToast />
</template>
