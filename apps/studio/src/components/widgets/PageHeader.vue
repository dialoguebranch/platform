<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useStateManagement } from '../../composables/state-management.js';
import StudioWordmark from './StudioWordmark.vue';
import OrnamentDivider from './OrnamentDivider.vue';

// The top of Studio's full-page screens (participant view, project selection, Users): the logo
// and "Studio", a divider, and a bar right above the page's content with whatever the page puts
// in the default slot on the left (a greeting, a way back) and a small Log out button on the
// right. The bar is as wide as the page's content column.

const stateManagement = useStateManagement();
</script>

<template>
    <header class="w-full shrink-0">
        <img class="block pt-6 mx-auto w-[200px] sm:w-[320px]" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <StudioWordmark class="mt-2" />
        <div class="w-full max-w-3xl mx-auto px-4 mt-4">
            <div>
                <OrnamentDivider class="mt-3" />
                <div class="flex items-center justify-between gap-3 mt-6 mb-2">
                    <div class="min-w-0 font-title text-xs text-grey-dark">
                        <slot />
                    </div>
                    <button
                        type="button"
                        class="shrink-0 flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-orange-darker/40 bg-white/60 font-title text-xs text-orange-darker hover:bg-white cursor-pointer"
                        data-test="log-out"
                        @click="stateManagement.logout()"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-right-from-bracket" />
                        Log out
                    </button>
                </div>
            </div>
        </div>
    </header>
</template>
