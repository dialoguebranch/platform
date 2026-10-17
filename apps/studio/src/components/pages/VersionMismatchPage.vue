<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import StudioWordmark from '../widgets/StudioWordmark.vue';

defineProps({
    clientVersion: { type: String, required: true },
    serviceVersion: { type: String, required: true },
});

function retry() {
    window.location.reload();
}
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col items-center justify-center font-title px-4">
        <img class="w-[200px] sm:w-[320px]" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <StudioWordmark class="mt-2 mb-6" />
        <div class="bg-box rounded-xl shadow-md max-w-md w-full px-6 py-8 text-center">
            <FontAwesomeIcon icon="fa-solid fa-arrows-rotate" class="text-4xl text-orange-darker mb-4" />
            <h1 class="font-bold text-lg mb-2">Update Available</h1>
            <p class="text-sm text-grey-dark mb-4">
                This page is running a different version of Dialogue Branch Studio than
                the web service expects. Reloading the page usually fixes this.
            </p>
            <ul class="text-xs text-left text-grey-dark mb-6 space-y-1">
                <li>Studio: <span class="font-mono">v{{ clientVersion }}</span></li>
                <li>Web service: <span class="font-mono">v{{ serviceVersion }}</span></li>
            </ul>
            <button
                type="button"
                class="px-4 py-2 rounded bg-orange-darker text-white text-sm font-semibold hover:bg-orange-dark cursor-pointer transition-colors"
                @click="retry"
            >Reload</button>
        </div>
    </div>
</template>
