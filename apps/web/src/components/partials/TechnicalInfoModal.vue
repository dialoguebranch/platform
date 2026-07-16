<script setup>
import { onMounted, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';

const emit = defineEmits(['close']);

const client = useClient();

const loading = ref(true);
const error = ref('');
const technicalInfo = ref(null);

onMounted(() => {
    client.getTechnicalInfo()
        .then((info) => { technicalInfo.value = info; })
        .catch(() => { error.value = 'Failed to load technical information.'; })
        .finally(() => { loading.value = false; });
});
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="emit('close')">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">

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

                    <dl v-else class="flex flex-col divide-y divide-grey-light font-mono text-sm">
                        <div class="flex items-center justify-between py-2">
                            <dt class="text-grey-dark">Active User Services</dt>
                            <dd class="font-semibold text-orange-darker">{{ technicalInfo.activeUserServiceCount }}</dd>
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
