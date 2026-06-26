<script setup>
import { inject } from 'vue';

import LoginPage from './components/pages/LoginPage.vue';
import ProjectSelectorPage from './components/pages/ProjectSelectorPage.vue';
import MainPage from './components/pages/MainPage.vue';
import DebugWindow from './components/widgets/DebugWindow.vue';

const state = inject('state');
</script>

<template>
    <MainPage v-if="state.user && state.selectedProject" />
    <ProjectSelectorPage v-else-if="state.user" />
    <LoginPage v-else />
    <DebugWindow />
</template>
