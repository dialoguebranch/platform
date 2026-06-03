<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const props = defineProps([
    'modes',
]);

const model = defineModel();

function onModeClick(mode) {
    model.value = mode.name;
}

const onClasses = ['bg-mode-selector-on', 'hover:bg-mode-selector-on-hover'];
const offClasses = ['bg-mode-selector-off', 'hover:bg-mode-selector-off-hover'];
</script>

<template>
    <div class="rounded-full bg-icon-button-disabled flex items-center bg-mode-selector-bg p-0.5 gap-1.5">
        <button v-for="mode in modes"
            class="rounded-full w-6.5 h-6.5 cursor-pointer"
            :class="model === mode.name ? onClasses : offClasses"
            @click="onModeClick(mode)"
        >
            <FontAwesomeIcon class="text-white w-4 h-4" :icon="mode.icon" />
            {{ model.value }}
        </button>
    </div>
</template>
