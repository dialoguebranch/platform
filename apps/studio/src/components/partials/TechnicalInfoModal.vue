<script setup>
import { computed, onMounted, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '../../composables/client.js';

const emit = defineEmits(['close']);

const client = useAuthoringClient();

const loading = ref(true);
const error = ref('');
const technicalInfo = ref(null);

// Host/port/scheme as the Web Service itself saw the incoming request. Behind the BFF proxy this
// is the address the BFF connected to (often an internal container hostname), not a browser-facing
// URL — hence it lives here in the admin-only panel rather than in the status bar.
const observedAddress = computed(() => {
    const i = technicalInfo.value;
    if (!i || !i.serverName) return null;
    return `${i.scheme}://${i.serverName}:${i.serverPort}`;
});

onMounted(() => {
    client.getTechnicalInfo()
        .then((info) => { technicalInfo.value = info; })
        .catch(() => { error.value = 'Failed to load technical information.'; })
        .finally(() => { loading.value = false; });
});
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-server" />
                        Technical Information
                    </div>
                    <button class="text-orange-light hover:text-white cursor-pointer" @click="emit('close')">
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <!-- Loading -->
                <div v-if="loading" class="flex items-center justify-center py-12 text-orange-darker">
                    <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin text-2xl" />
                </div>

                <!-- Content -->
                <div v-else class="px-5 py-5 flex flex-col gap-4">

                    <!-- Error banner -->
                    <div v-if="error" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                        {{ error }}
                    </div>

                    <dl v-else class="flex flex-col divide-y divide-grey-light text-sm">
                        <div class="flex flex-col gap-0.5 py-2">
                            <dt class="text-grey-dark font-title text-xs uppercase tracking-wide">Web Service Version</dt>
                            <dd class="font-mono text-orange-darker break-all">v{{ technicalInfo.serviceVersion }}</dd>
                        </div>
                        <div class="flex flex-col gap-0.5 py-2">
                            <dt class="text-grey-dark font-title text-xs uppercase tracking-wide">Build Time</dt>
                            <dd class="font-mono text-orange-darker break-all">{{ technicalInfo.buildTime }}</dd>
                        </div>
                        <div class="flex flex-col gap-0.5 py-2">
                            <dt class="text-grey-dark font-title text-xs uppercase tracking-wide">Configured Base URL</dt>
                            <dd class="font-mono text-orange-darker break-all">{{ technicalInfo.configuredBaseUrl }}</dd>
                        </div>
                        <div v-if="observedAddress" class="flex flex-col gap-0.5 py-2">
                            <dt class="text-grey-dark font-title text-xs uppercase tracking-wide">Request Address (as seen by the service)</dt>
                            <dd class="font-mono text-orange-darker break-all">{{ observedAddress }}</dd>
                        </div>
                        <div class="flex flex-col gap-0.5 py-2">
                            <dt class="text-grey-dark font-title text-xs uppercase tracking-wide">Keycloak</dt>
                            <dd class="font-mono text-orange-darker break-all">{{ technicalInfo.keycloakBaseUrl }} <span class="text-grey-dark">(realm: {{ technicalInfo.keycloakRealm }})</span></dd>
                        </div>
                        <div class="flex items-center justify-between py-2">
                            <dt class="text-grey-dark font-title text-xs uppercase tracking-wide">Active User Services</dt>
                            <dd class="font-mono font-semibold text-orange-darker">{{ technicalInfo.activeUserServiceCount }}</dd>
                        </div>
                    </dl>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-end px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" @click="emit('close')">Close</button>
                </div>
            </div>
        </div>
    </Teleport>
</template>
