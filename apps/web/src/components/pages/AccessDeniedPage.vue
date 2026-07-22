<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { redirectToLogout } from '../../auth.js';

defineProps({
    username: { type: String, required: true },
});
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col items-center justify-center font-title px-4">
        <img class="w-[240px] sm:w-[320px] mb-6" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <div class="bg-box rounded-xl shadow-md max-w-md w-full px-6 py-8 text-center">
            <FontAwesomeIcon icon="fa-solid fa-user-lock" class="text-4xl text-orange-darker mb-4" />
            <h1 class="font-bold text-lg mb-2">Insufficient Privileges</h1>
            <p class="text-sm text-grey-dark mb-4">
                You're signed in as <span class="font-semibold">{{ username }}</span>, but this
                account doesn't have access to Dialogue Branch. Contact an administrator if you
                believe this is a mistake, or log out and try a different account.
            </p>
            <button
                type="button"
                class="px-4 py-2 rounded bg-orange-darker text-white text-sm font-semibold hover:bg-orange-dark cursor-pointer transition-colors"
                @click="redirectToLogout"
            >Log Out</button>
        </div>
    </div>
</template>
