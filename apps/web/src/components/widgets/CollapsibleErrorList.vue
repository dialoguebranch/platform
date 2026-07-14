<script setup>
import { computed } from 'vue';

const props = defineProps({
    errors: { type: Object, default: null },
});

const dialogueNames = computed(() => props.errors ? Object.keys(props.errors) : []);
</script>

<template>
    <details v-if="dialogueNames.length > 0" class="text-left w-full max-w-md">
        <summary class="cursor-pointer text-xs font-title text-orange-darker hover:underline select-none text-center">
            Show details ({{ dialogueNames.length }} dialogue{{ dialogueNames.length === 1 ? '' : 's' }})
        </summary>
        <div class="mt-2 max-h-48 overflow-y-auto border border-grey-light rounded-lg divide-y divide-grey-light bg-white">
            <div v-for="name in dialogueNames" :key="name" class="px-3 py-2">
                <div class="font-title font-bold text-sm text-orange-darker">{{ name }}</div>
                <ul class="list-disc list-inside text-sm text-grey-dark">
                    <li v-for="(msg, i) in errors[name]" :key="i">{{ msg }}</li>
                </ul>
            </div>
        </div>
    </details>
</template>
