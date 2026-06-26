<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const props = defineProps([
    'link',
    'text',
    'icon',
]);

const emit = defineEmits([
    'click',
]);

function onclick() {
    if (props.link) {
        window.open(props.link, '_blank');
    } else {
        emit('click');
    }
}
</script>

<template>
    <button class="flex items-center gap-2 h-full px-5 font-title font-medium text-sm text-orange-light hover:text-white hover:bg-orange-dark border-r border-orange-dark cursor-pointer whitespace-nowrap transition-colors"
        @click="onclick"
    >
        <FontAwesomeIcon v-if="icon" :icon="icon" class="opacity-80" />
        {{ text }}
    </button>
</template>
