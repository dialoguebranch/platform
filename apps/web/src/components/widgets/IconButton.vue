<script setup>
import { computed } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

/**
 * Properties:
 * - icon (String): fontawesome classes like: "fa-solid fa-arrows-rotate"
 * - type (String): one of: default, list-item
 * - color (String): one of: default, warning
 * - disabled (boolean)
 */
const props = defineProps({
    'icon': String,
    'type': {
        type: String,
        default: 'default',
    },
    'color': {
        type: String,
        default: 'default',
    },
    'disabled': Boolean,
});

const classes = computed(() => {
    const result = [];
    if (props.disabled) {
        result.push('cursor-not-allowed');
    } else {
        result.push('cursor-pointer');
    }
    if (props.type === 'list-item') {
        if (props.disabled) {
            result.push('text-icon-button-disabled');
        } else if (props.color == 'warning') {
            result.push('text-icon-button-warning');
            result.push('hover:text-icon-button-warning-hover');
        } else {
            result.push('text-orange-darker');
            result.push('hover:text-orange-dark');
        }
    } else {
        if (props.disabled) {
            result.push('bg-icon-button-disabled');
        } else if (props.color == 'warning') {
            result.push('bg-icon-button-warning');
            result.push('hover:bg-icon-button-warning-hover');
        } else {
            result.push('bg-icon-button');
            result.push('hover:bg-icon-button-hover');
        }
    }
    return result;
});
</script>

<template>
    <button v-if="type === 'list-item'" class="w-5 h-5 flex items-center justify-center" :class="classes"><FontAwesomeIcon :icon="icon" /></button>
    <button v-else class="rounded-full w-7.5 h-7.5" :class="classes"><FontAwesomeIcon class="text-white" :icon="icon" /></button>
</template>
