<script setup>
import { computed } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { DLB_APP_MODE_DRAFT } from '../../StudioClientState.js';

// The bar under the header that says which mode Studio is in: green in Authoring Mode, amber in
// Live Mode. While acting for another user it also says so, with a button to stop: simulating them
// (#411) keeps the mode's colour, since nothing of theirs changes; acting as them for real (#330)
// turns the whole bar red. Stopping needs no confirmation.

const props = defineProps({
    // DLB_APP_MODE_LIVE or DLB_APP_MODE_DRAFT
    mode: { type: String, required: true },
    // { subject, label, mode: 'simulate' | 'act' }, or null
    delegate: { type: Object, default: null },
});

const emit = defineEmits(['stop']);

const authoring = computed(() => props.mode === DLB_APP_MODE_DRAFT);
const acting = computed(() => props.delegate?.mode === 'act');

const barClass = computed(() => {
    if (acting.value) return 'bg-red-dark text-white';
    return authoring.value ? 'bg-mode-authoring text-white' : 'bg-mode-live text-orange-darker';
});
const stopClass = computed(() => !acting.value && !authoring.value
    ? 'hover:bg-black/10' : 'hover:bg-white/15');
</script>

<template>
    <div :class="['relative flex items-center gap-3 px-4 min-h-8 py-1 font-title text-xs shadow-[0_2px_4px_rgba(0,0,0,0.35)] z-[90]', barClass]" data-test="mode-bar">
        <span class="flex items-center gap-2 shrink-0" data-test="mode-bar-mode">
            <FontAwesomeIcon :icon="authoring ? 'fa-solid fa-pen' : 'fa-solid fa-play'" />
            <strong>{{ authoring ? 'Authoring Mode' : 'Live Mode' }}</strong>
            <span class="hidden sm:block w-[5px] h-[5px] rounded-full bg-current" aria-hidden="true"></span>
            <span class="hidden sm:inline">{{ authoring ? 'Editing and testing the draft' : 'Running the published dialogues' }}</span>
        </span>

        <span
            v-if="delegate"
            class="flex items-center gap-2 ml-auto min-w-0"
            :data-test="acting ? 'acting-banner' : 'simulating-banner'"
        >
            <FontAwesomeIcon :icon="acting ? 'fa-solid fa-triangle-exclamation' : 'fa-solid fa-flask'" class="shrink-0" />
            <span class="truncate">{{ acting ? 'Acting as' : 'Simulating' }} <strong>{{ delegate.label }}</strong></span>
            <span class="hidden sm:block w-[5px] h-[5px] rounded-full bg-current shrink-0" aria-hidden="true"></span>
            <span class="hidden sm:inline shrink-0">{{ acting ? "Changes affect this user's real data" : 'Nothing is saved' }}</span>
            <button
                type="button"
                data-test="stop-delegate"
                :class="['shrink-0 px-2 py-0.5 rounded border border-current/50 cursor-pointer', stopClass]"
                @click="emit('stop')"
            >{{ acting ? `Stop acting as ${delegate.label}` : 'Stop simulating' }}</button>
        </span>
    </div>
</template>
