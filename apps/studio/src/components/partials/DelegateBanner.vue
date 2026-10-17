<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// Shown across the workspace while acting for another user: in orange while simulating them
// (#411), nothing of theirs changes; in red while acting as them for real (#330). Stopping needs
// no confirmation.

defineProps({
    // { subject, label, mode: 'simulate' | 'act' }
    delegate: { type: Object, required: true },
});

const emit = defineEmits(['stop']);
</script>

<template>
    <div
        v-if="delegate.mode === 'simulate'"
        data-test="simulating-banner"
        class="flex items-center gap-2 mb-1 px-3 py-1.5 rounded-lg border border-orange-medium/50 bg-orange-light/30 font-title text-xs text-orange-darker"
    >
        <FontAwesomeIcon icon="fa-solid fa-flask" class="shrink-0" />
        <span class="flex-1">Simulating <strong>{{ delegate.label }}</strong>: dialogues run against a copy of their variables, and nothing is saved.</span>
        <button type="button" data-test="stop-delegate" class="shrink-0 px-2 py-0.5 rounded border border-orange-medium/60 hover:bg-orange-light/50 cursor-pointer" @click="emit('stop')">Stop simulating</button>
    </div>
    <div
        v-else
        data-test="acting-banner"
        class="flex items-center gap-2 mb-1 px-3 py-1.5 rounded-lg border border-red-dark/40 bg-red-dark/10 font-title text-xs text-red-dark"
    >
        <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="shrink-0" />
        <span class="flex-1">Acting as <strong>{{ delegate.label }}</strong>: dialogues and variable changes affect this user's real data.</span>
        <button type="button" data-test="stop-delegate" class="shrink-0 px-2 py-0.5 rounded border border-red-dark/40 hover:bg-red-dark/10 cursor-pointer" @click="emit('stop')">Stop acting as {{ delegate.label }}</button>
    </div>
</template>
