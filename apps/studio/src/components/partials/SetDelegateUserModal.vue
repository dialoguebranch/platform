<script setup>
import { onMounted, ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '@/composables/client.js';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';

defineProps({
    // The label of the currently active delegate (a username, or a raw subject), or null.
    currentDelegateUser: { type: String, default: null },
});

// 'apply' carries { subject, label }: subject is sent to the API, label is shown in the UI.
const emit = defineEmits(['close', 'apply', 'clear']);

const client = useClient();

const mode = ref('search'); // 'search' | 'raw'

const query = ref('');
const results = ref([]);          // [{ username, subject }]
const selected = ref(null);       // { username, subject }
const loading = ref(false);
const loadError = ref(false);
const rawSubject = ref('');

let searchTimer = null;
let searchSeq = 0;
// Set when pick() writes the chosen username back into `query`, so the watcher below
// doesn't treat that programmatic write as the user editing the field (which would clear
// the selection and re-disable Apply).
let suppressQueryWatch = false;

function runSearch() {
    const seq = ++searchSeq;
    loading.value = true;
    loadError.value = false;
    client.listUsers(query.value.trim())
        .then((rows) => {
            if (seq !== searchSeq) return;
            results.value = rows;
        })
        .catch(() => {
            if (seq !== searchSeq) return;
            results.value = [];
            loadError.value = true;
        })
        .finally(() => {
            if (seq === searchSeq) loading.value = false;
        });
}

watch(query, () => {
    if (suppressQueryWatch) {
        suppressQueryWatch = false;
        return;
    }
    selected.value = null;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(runSearch, 250);
});

onMounted(runSearch);

function pick(row) {
    selected.value = row;
    if (query.value !== row.username) {
        suppressQueryWatch = true;
        query.value = row.username;
    }
}

function onApply() {
    if (mode.value === 'raw') {
        const subject = rawSubject.value.trim();
        if (subject) emit('apply', { subject, label: subject });
        return;
    }
    if (selected.value) {
        emit('apply', { subject: selected.value.subject, label: selected.value.username });
    }
}

function onClear() {
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

                    <div class="flex gap-1 text-xs font-title">
                        <button type="button" class="px-2 py-1 rounded"
                            :class="mode === 'search' ? 'bg-orange-dark text-white' : 'bg-grey-lighter text-grey-dark hover:bg-grey-light'"
                            @click="mode = 'search'">Search by username</button>
                        <button type="button" class="px-2 py-1 rounded"
                            :class="mode === 'raw' ? 'bg-orange-dark text-white' : 'bg-grey-lighter text-grey-dark hover:bg-grey-light'"
                            @click="mode = 'raw'">Enter subject (UUID)</button>
                    </div>

                    <!-- Search mode -->
                    <div v-if="mode === 'search'">
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Delegate Username</label>
                        <TextInput
                            class="w-full"
                            v-model="query"
                            placeholder="Type a username..."
                            @keyup.enter="onApply"
                        />
                        <div class="mt-2 border border-grey-light rounded-lg max-h-52 overflow-y-auto divide-y divide-grey-lighter">
                            <div v-if="loading" class="px-3 py-2 text-xs text-grey-dark font-title">Searching…</div>
                            <div v-else-if="loadError" class="px-3 py-2 text-xs text-red-dark font-title">Could not load the user list.</div>
                            <div v-else-if="results.length === 0" class="px-3 py-2 text-xs text-grey-dark font-title">
                                No known users match. Only users who have run at least one dialogue on this service appear here — use "Enter subject" for anyone else.
                            </div>
                            <button
                                v-for="row in results"
                                :key="row.subject"
                                type="button"
                                class="w-full text-left px-3 py-2 text-sm font-title hover:bg-grey-lighter cursor-pointer flex items-baseline gap-2"
                                :class="selected && selected.subject === row.subject ? 'bg-orange-light/25' : ''"
                                @click="pick(row)"
                            >
                                <span class="font-semibold text-orange-darker">{{ row.username }}</span>
                                <span class="font-mono text-[10px] text-grey-medium truncate">{{ row.subject }}</span>
                            </button>
                        </div>
                        <p class="mt-1 text-[11px] text-grey-medium font-title">
                            The username shown is the last name this service saw; it may be stale after a rename in Keycloak, but the delegated run still targets the right person.
                        </p>
                    </div>

                    <!-- Raw subject mode -->
                    <div v-else>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Delegate Subject (OIDC sub)</label>
                        <TextInput
                            class="w-full font-mono"
                            v-model="rawSubject"
                            placeholder="e.g. 8f0b…-…"
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
                        <PushButton
                            text="Apply"
                            variant="green"
                            :disabled="mode === 'search' ? !selected : !rawSubject.trim()"
                            @click="onApply"
                        />
                    </div>
                </div>
            </div>
        </div>
    </Teleport>
</template>
