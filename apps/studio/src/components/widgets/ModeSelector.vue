<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const props = defineProps([
    'modes',
]);

const model = defineModel();

function onModeClick(mode) {
    model.value = mode.name;
}

</script>

<template>
    <div class="flex shrink-0 rounded-lg overflow-hidden border border-grey-light">
        <button v-for="(mode, index) in modes"
            type="button"
            class="h-[26px] px-2.5 flex items-center justify-center gap-1.5 text-xs font-title whitespace-nowrap cursor-pointer"
            :class="[
                model === mode.name ? 'bg-orange-dark text-white' : 'bg-white text-grey-dark hover:bg-grey-lighter',
                { 'border-l border-grey-light': index > 0 },
            ]"
            :title="mode.title"
            :data-test="'mode-' + mode.name"
            @click="onModeClick(mode)"
        >
            <FontAwesomeIcon :icon="mode.icon" />
            <span v-if="mode.label">{{ mode.label }}</span>
        </button>
    </div>
</template>
