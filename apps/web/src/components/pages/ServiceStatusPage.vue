<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

defineProps({
    apiUp: { type: Boolean, default: false },
    keycloakUp: { type: Boolean, default: false },
});

function retry() {
    window.location.reload();
}
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col items-center justify-center font-title px-4">
        <img class="w-[240px] sm:w-[320px] mb-6" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <div class="bg-box rounded-xl shadow-md max-w-md w-full px-6 py-8 text-center">
            <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="text-4xl text-orange-darker mb-4" />
            <h1 class="font-bold text-lg mb-2">Service Unavailable</h1>
            <p class="text-sm text-grey-dark mb-4">
                We can't reach the Dialogue Branch services right now, so we can't sign you in.
                Please try again in a few minutes.
            </p>
            <ul class="text-xs text-left text-grey-dark mb-6 space-y-1">
                <li class="flex items-center gap-2">
                    <FontAwesomeIcon
                        :icon="apiUp ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle-xmark'"
                        :class="apiUp ? 'text-green-600' : 'text-red-500'"
                    />
                    Dialogue Branch API
                </li>
                <li class="flex items-center gap-2">
                    <FontAwesomeIcon
                        :icon="keycloakUp ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle-xmark'"
                        :class="keycloakUp ? 'text-green-600' : 'text-red-500'"
                    />
                    Authentication service
                </li>
            </ul>
            <button
                type="button"
                class="px-4 py-2 rounded bg-orange-darker text-white text-sm font-semibold hover:bg-orange-dark cursor-pointer transition-colors"
                @click="retry"
            >Retry</button>
        </div>
    </div>
</template>
