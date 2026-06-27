<script setup>
import { ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';

const props = defineProps({
    currentDelegateUser: { type: String, default: null },
});

const emit = defineEmits(['close', 'apply', 'clear']);

const delegateUserInput = ref(props.currentDelegateUser ?? '');

function onApply() {
    emit('apply', delegateUserInput.value.trim() || null);
}

function onClear() {
    delegateUserInput.value = '';
    emit('clear');
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="emit('close')">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-user-gear" />
                        Set Delegate User
                    </div>
                    <button class="text-orange-light hover:text-white cursor-pointer" @click="emit('close')">
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <!-- Content -->
                <div class="px-5 py-5 flex flex-col gap-4">
                    <p class="font-title text-sm text-grey-dark">
                        When a delegate user is set, all dialogue interactions will be executed on behalf of that user.
                        Leave empty to act as yourself.
                    </p>

                    <div v-if="currentDelegateUser" class="flex items-center gap-2 bg-orange-light/20 border border-orange-medium/40 rounded-lg px-4 py-2 font-title text-sm text-orange-darker">
                        <FontAwesomeIcon icon="fa-solid fa-circle-info" class="shrink-0" />
                        Currently acting as <span class="font-bold font-mono ml-1">{{ currentDelegateUser }}</span>
                    </div>

                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Delegate Username</label>
                        <TextInput
                            class="w-full"
                            v-model="delegateUserInput"
                            placeholder="Enter username..."
                            @keyup.enter="onApply"
                        />
                    </div>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-between px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button
                        v-if="currentDelegateUser"
                        class="px-4 py-2 rounded font-title text-sm text-red-dark border border-red-dark/40 hover:bg-red-dark/10 cursor-pointer transition-colors"
                        @click="onClear"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-user-xmark" class="mr-1.5" />
                        Clear Delegate
                    </button>
                    <div v-else></div>
                    <div class="flex gap-3">
                        <button class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" @click="emit('close')">Cancel</button>
                        <PushButton text="Apply" variant="green" @click="onApply" />
                    </div>
                </div>
            </div>
        </div>
    </Teleport>
</template>
