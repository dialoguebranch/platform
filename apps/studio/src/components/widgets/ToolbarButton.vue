<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// An icon button for a panel's toolbar row, styled like the filter field and sort menu next to it,
// so a toolbar's controls read as one set.
defineProps({
    icon: { type: String, required: true },
    title: { type: String, default: null },
    disabled: { type: Boolean, default: false },
    // Spins the icon, e.g. while refreshing.
    spin: { type: Boolean, default: false },
});
</script>

<template>
    <button
        type="button"
        class="shrink-0 w-6 h-6 flex items-center justify-center rounded-lg border border-grey-light bg-white text-xs text-orange-darker hover:bg-grey-lighter hover:text-orange-dark cursor-pointer disabled:text-grey-light disabled:hover:bg-white disabled:cursor-not-allowed"
        :title="title"
        :disabled="disabled"
    >
        <FontAwesomeIcon :icon="icon" :class="{ 'animate-spin': spin }" />
    </button>
</template>
