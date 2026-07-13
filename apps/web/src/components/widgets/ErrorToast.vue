<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { errorToast, dismissError } from '../../composables/error-toast.js';
</script>

<template>
    <Teleport to="body">
        <div v-if="errorToast" class="fixed inset-0 z-[10000] flex items-center justify-center pointer-events-none">
            <div class="pointer-events-auto flex items-center gap-3 border border-red-dark/40 bg-white shadow-2xl rounded-xl px-4 py-3 mx-4 text-sm font-title text-red-dark max-w-md">
                <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                <span class="grow">{{ errorToast.message }}</span>
                <button type="button" class="shrink-0 cursor-pointer hover:text-red-900" title="Dismiss" @click="dismissError">
                    <FontAwesomeIcon icon="fa-solid fa-xmark" />
                </button>
            </div>
        </div>
    </Teleport>
</template>
