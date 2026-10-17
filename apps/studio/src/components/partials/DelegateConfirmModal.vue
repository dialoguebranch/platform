<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// Confirms a change of the user being acted for (#330, #411). Choosing to act as someone for real
// warns what that changes; simulating them changes nothing of theirs; clearing just ends either.
// Every change closes the open tabs.

defineProps({
    // { mode: 'simulate' | 'act' | null, label } — mode null means clearing the delegate.
    change: { type: Object, required: true },
});

const emit = defineEmits(['confirm', 'cancel']);
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40" data-test="delegate-confirm">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col font-title text-sm">
                <div
                    class="flex items-center gap-2 px-5 py-4 rounded-t-xl text-white font-bold"
                    :class="change.mode === 'act' ? 'bg-red-dark' : 'bg-orange-darker'"
                >
                    <FontAwesomeIcon :icon="change.mode === 'act' ? 'fa-solid fa-triangle-exclamation' : change.mode === 'simulate' ? 'fa-solid fa-flask' : 'fa-solid fa-user-xmark'" />
                    <template v-if="change.mode === 'act'">Act as {{ change.label }}</template>
                    <template v-else-if="change.mode === 'simulate'">Simulate {{ change.label }}</template>
                    <template v-else>Stop Acting for Another User</template>
                </div>

                <div class="px-5 py-4 flex flex-col gap-3">
                    <template v-if="change.mode === 'act'">
                        <p>
                            You are about to act on behalf of <span class="font-bold text-orange-darker">{{ change.label }}</span>, for real.
                        </p>
                        <div class="flex items-start gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-lg px-3 py-2.5 text-xs" data-test="act-warning">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                            <ul class="list-disc ml-3 flex flex-col gap-1">
                                <li>Dialogues you run, and variables you change, become part of <strong>their own</strong> dialogue logs, variables and variable history, as if they did it themselves.</li>
                                <li>A connected External Variable Service is told about those changes as if they made them.</li>
                                <li>It can interfere with a dialogue they have ongoing.</li>
                            </ul>
                        </div>
                        <p class="text-xs text-grey-dark">
                            To investigate a problem without changing anything of theirs, choose <strong>Simulate as</strong> instead.
                        </p>
                    </template>
                    <p v-else-if="change.mode === 'simulate'">
                        Dialogues will run for <span class="font-bold text-orange-darker">{{ change.label }}</span> against a copy of their variables. Nothing of theirs is changed.
                    </p>
                    <p v-else>You'll act as yourself again.</p>
                    <p class="text-xs text-grey-dark">Open tabs are closed.</p>
                </div>

                <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button type="button" class="px-4 py-2 rounded text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" @click="emit('cancel')">Cancel</button>
                    <button
                        type="button"
                        class="px-4 py-2 rounded text-sm text-white cursor-pointer"
                        :class="change.mode === 'act' ? 'bg-red-dark hover:bg-red-900' : 'bg-orange-darker hover:bg-orange-dark'"
                        data-test="delegate-confirm-button"
                        @click="emit('confirm')"
                    >
                        <template v-if="change.mode === 'act'">Act as {{ change.label }}</template>
                        <template v-else-if="change.mode === 'simulate'">Simulate as {{ change.label }}</template>
                        <template v-else>Act as myself</template>
                    </button>
                </div>
            </div>
        </div>
    </Teleport>
</template>
