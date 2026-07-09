<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const props = defineProps({
    text: {},
    type: {
        default: 'button',
    },
    variant: {
        default: 'default',
    },
    loading: {
        default: false,
    },
    disabled: {
        default: false,
    },
});

const emit = defineEmits([
    'click',
]);

const variantClass = {
    default: 'bg-orange-dark hover:bg-orange-darker',
    green: 'bg-icon-button hover:bg-icon-button-hover',
};
</script>

<template>
    <button :class="['rounded-lg px-6 py-2 min-w-[140px] font-title text-sm text-white inline-flex items-center justify-center gap-2', (loading || disabled) ? 'opacity-75 cursor-not-allowed' : 'cursor-pointer', variantClass[variant] ?? variantClass.default]"
        :type="type"
        :disabled="loading || disabled"
        @click="emit('click')"
    >
        <FontAwesomeIcon v-if="loading" icon="fa-solid fa-circle-notch" class="animate-spin" />
        {{ text }}
    </button>
</template>
