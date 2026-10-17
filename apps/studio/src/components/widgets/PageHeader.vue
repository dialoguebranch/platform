<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useStateManagement } from '../../composables/state-management.js';
import StudioWordmark from './StudioWordmark.vue';
import OrnamentDivider from './OrnamentDivider.vue';

// The top of Studio's full-page screens (participant view, project selection, Users): the logo
// and "Studio", a divider, and a line with whatever the page puts in the default slot (a
// greeting). A page that fills the `menu` slot also gets a menu bar under it, with its items on
// the left and Log out on the right; otherwise Log out is a small button next to the greeting.
// Everything is as wide as the page's content column.

const stateManagement = useStateManagement();
</script>

<template>
    <header class="w-full shrink-0">
        <img class="block pt-6 mx-auto w-[200px] sm:w-[320px]" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <StudioWordmark class="mt-2" />
        <div class="w-full max-w-3xl mx-auto px-4 mt-4">
            <div>
                <OrnamentDivider class="mt-3" />
                <div class="flex items-center justify-between gap-3 mt-6" :class="$slots.menu ? 'mb-4' : 'mb-2'">
                    <div class="min-w-0 font-title text-xs text-grey-dark">
                        <slot />
                    </div>
                    <button
                        v-if="!$slots.menu"
                        type="button"
                        class="shrink-0 flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-orange-darker/40 bg-white/60 font-title text-xs text-orange-darker hover:bg-white cursor-pointer"
                        data-test="log-out"
                        @click="stateManagement.logout()"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-right-from-bracket" />
                        Log out
                    </button>
                </div>
                <!-- A menu bar, for the pages that have one: their menu items on the left, and Log out
                     on the right (in the bar instead of next to the greeting) -->
                <nav
                    v-if="$slots.menu"
                    class="flex items-stretch h-10 mb-4 rounded-lg overflow-hidden bg-orange-darker shadow-md"
                    data-test="menu-bar"
                >
                    <div class="flex items-stretch min-w-0 overflow-x-auto">
                        <slot name="menu" />
                    </div>
                    <button
                        type="button"
                        class="ml-auto shrink-0 flex items-center gap-2 px-3 sm:px-4 font-title text-sm text-orange-light hover:text-white hover:bg-orange-dark border-l border-orange-dark cursor-pointer whitespace-nowrap transition-colors"
                        data-test="log-out"
                        @click="stateManagement.logout()"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-right-from-bracket" class="opacity-80" />
                        Log out
                    </button>
                </nav>
            </div>
        </div>
    </header>
</template>
