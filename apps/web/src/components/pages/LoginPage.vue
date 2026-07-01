<script setup>
import { inject } from 'vue';
import PushButton from '../widgets/PushButton.vue';

const keycloak = inject('keycloak');

function onLoginClick() {
    keycloak.login({ redirectUri: window.location.origin });
}
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col items-center">
        <img class="block pt-24 w-[300px] sm:w-[690px]" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <div class="mt-3 font-title font-bold">Web Client Test Application</div>

        <div class="w-full px-4 my-6">
            <div class="sm:w-[448px] bg-box rounded-2xl px-5 py-5 mx-auto border border-orange-medium/50 shadow-2xl text-center">
                <p class="font-title">Sign in with your Dialogue Branch account to continue.</p>
                <div class="mt-4">
                    <PushButton text="Log in" variant="green" @click="onLoginClick" />
                </div>
            </div>
        </div>
    </div>
</template>
