<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// The title row above a main page panel: an icon and the panel's name, with the panel's view
// controls on the right. Always 30px high, so the three panels' boxes line up.
defineProps({
    title: { type: String, required: true },
    // A Font Awesome class string, e.g. 'fa-solid fa-folder-open'.
    icon: { type: String, default: null },
});
</script>

<template>
    <div class="ml-2 mr-1 sm:ml-1 sm:mr-0 flex mt-1 items-center gap-2">
        <div class="flex items-center gap-2 min-w-0 grow basis-0 font-title font-bold text-xs uppercase tracking-wider text-orange-darker">
            <FontAwesomeIcon v-if="icon" :icon="icon" class="shrink-0 text-orange-medium" />
            <span class="truncate">{{ title }}</span>
        </div>
        <div class="h-7.5 flex gap-2">
            <slot name="buttons" />
        </div>
    </div>
</template>
