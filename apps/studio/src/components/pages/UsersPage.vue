<script setup>
import { inject, onMounted, ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import PageHeader from '../widgets/PageHeader.vue';
import { useAuthoringClient } from '../../composables/client.js';
import { useUserSearch } from '../../composables/user-search.js';

// The Users page (#407): every user the Web Service knows, with how much data it holds for each,
// outside any project. Admins only. For now it erases a user's data; more user management is
// meant to land here later.

const state = inject('state');
const client = useAuthoringClient();

const { query, results, loading, loadError, hasMore, search, refresh, loadMore } = useUserSearch();

watch(query, search);
onMounted(refresh);

function backToProjects() {
    state.value.adminPage = null;
}


const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

function dataSummary(user) {
    if (!user.variables && !user.dialogueLogs) return 'No data';
    return `${plural(user.variables, 'variable', 'variables')} · ${plural(user.dialogueLogs, 'dialogue log', 'dialogue logs')}`;
}

// Erasing is behind a confirmation that requires typing the user's name back (their subject, for
// a user with no username): all of their data is gone for good, so it should never be a single
// misclick.
const eraseConfirm = ref(null); // the user row
const eraseConfirmInput = ref('');
const erasing = ref(false);
const eraseError = ref('');
const erased = ref(null); // { label, variables, variableHistory, dialogueLogs }

const confirmTextOf = (user) => user.username ?? user.subject;

function openEraseConfirm(user) {
    eraseConfirm.value = user;
    eraseConfirmInput.value = '';
    eraseError.value = '';
}

function closeEraseConfirm() {
    if (erasing.value) return;
    eraseConfirm.value = null;
}

function removeRow(subject) {
    results.value = results.value.filter((u) => u.subject !== subject);
}

function confirmErase() {
    const user = eraseConfirm.value;
    if (eraseConfirmInput.value.trim() !== confirmTextOf(user)) return;
    erasing.value = true;
    eraseError.value = '';
    client.eraseUser(user.subject)
        .then((summary) => {
            erased.value = { label: confirmTextOf(user), ...summary };
            removeRow(user.subject);
            eraseConfirm.value = null;
        })
        .catch((error) => {
            if (error?.status === 404) {
                erased.value = { label: confirmTextOf(user), variables: 0, variableHistory: 0, dialogueLogs: 0 };
                removeRow(user.subject);
                eraseConfirm.value = null;
            } else {
                eraseError.value = "Failed to erase the user's data. Please try again.";
            }
        })
        .finally(() => {
            erasing.value = false;
        });
}
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col items-center">
        <PageHeader>
            <button
                type="button"
                class="flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-orange-darker/40 bg-white/60 text-orange-darker hover:bg-white cursor-pointer"
                data-test="back-to-projects"
                @click="backToProjects"
            >
                <FontAwesomeIcon icon="fa-solid fa-arrow-left" />
                Projects
            </button>
        </PageHeader>

        <div class="w-full px-4 mt-4 pb-12 max-w-3xl mx-auto">
            <div class="flex flex-wrap items-center justify-between gap-3 mb-2">
                <h2 class="font-title font-bold text-lg">Users</h2>
                <input
                    v-model="query"
                    type="search"
                    class="w-full sm:w-72 bg-white px-3 py-1.5 rounded-md border-[2px] border-solid border-gray-300 font-title text-sm focus:outline-none focus:border-orange-medium"
                    placeholder="Search by username or subject…"
                    data-test="user-search"
                />
            </div>
            <p class="mb-4 text-xs font-title text-grey-dark">
                Everyone this Web Service has seen, in your realm, with the data it holds for them in all projects.
            </p>

            <div
                v-if="erased"
                class="flex items-start gap-2 mb-4 border border-green-700/40 bg-green-50 text-green-700 rounded-lg px-3 py-2.5 text-xs font-title"
                data-test="erased-notice"
            >
                <FontAwesomeIcon icon="fa-solid fa-circle-check" class="shrink-0 mt-0.5" />
                <span class="flex-1">
                    Erased <strong>{{ erased.label }}</strong>:
                    {{ plural(erased.variables, 'variable', 'variables') }},
                    {{ plural(erased.variableHistory, 'history entry', 'history entries') }},
                    {{ plural(erased.dialogueLogs, 'dialogue log', 'dialogue logs') }}.
                </span>
                <button type="button" class="cursor-pointer" aria-label="Dismiss" @click="erased = null">
                    <FontAwesomeIcon icon="fa-solid fa-xmark" />
                </button>
            </div>

            <div v-if="loadError" class="text-sm font-title text-red-dark" data-test="users-error">Could not load the users.</div>
            <div v-else-if="!loading && results.length === 0" class="text-sm font-title text-grey-dark" data-test="users-empty">
                No users match.
            </div>

            <div class="flex flex-col gap-2">
                <div
                    v-for="user in results"
                    :key="user.subject"
                    class="flex items-center gap-3 rounded-xl px-4 py-3 bg-orange-light/30 border border-orange-light"
                    data-test="user-row"
                >
                    <div class="flex-1 min-w-0">
                        <div v-if="user.username" class="font-title text-sm font-semibold text-orange-darker truncate">{{ user.username }}</div>
                        <div v-else class="font-title text-sm italic text-grey-dark">No username (only seen as a delegate)</div>
                        <div class="font-mono text-xs text-grey-dark truncate">{{ user.subject }}</div>
                        <div class="text-xs text-grey-dark mt-0.5" data-test="user-data">{{ dataSummary(user) }}</div>
                    </div>
                    <button
                        type="button"
                        class="shrink-0 px-3 py-1.5 rounded font-title text-xs text-red-dark border border-red-dark/40 hover:bg-red-dark/10 cursor-pointer"
                        data-test="erase-user"
                        @click="openEraseConfirm(user)"
                    >Erase data…</button>
                </div>
            </div>

            <div v-if="loading" class="mt-3 text-sm font-title text-grey-dark flex items-center gap-2">
                <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin" />
                Loading…
            </div>
            <button
                v-else-if="hasMore"
                type="button"
                class="mt-3 px-3 py-1.5 rounded font-title text-xs text-orange-darker border border-orange-medium/50 hover:bg-orange-light/30 cursor-pointer"
                data-test="load-more"
                @click="loadMore"
            >Load more</button>
        </div>
    </div>

    <Teleport to="body">
        <div v-if="eraseConfirm" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" data-test="erase-confirm" @click.self="closeEraseConfirm">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                <div class="flex items-center gap-2 px-5 py-4 bg-red-dark rounded-t-xl text-white font-title font-bold">
                    <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" />
                    Erase User Data
                </div>
                <div class="px-5 py-4 flex flex-col gap-3">
                    <p class="text-sm font-title">
                        You are about to erase everything this Web Service stores about
                        <span class="font-bold text-orange-darker">{{ confirmTextOf(eraseConfirm) }}</span>.
                    </p>
                    <div class="flex items-start gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-lg px-3 py-2.5 text-xs font-title">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                        <span>
                            This deletes <strong>their variables, variable history and dialogue logs in every project</strong>
                            ({{ dataSummary(eraseConfirm) }}), and the user itself. A connected External Variable Service is
                            told their variables were cleared. <strong>This action cannot be undone.</strong>
                        </span>
                    </div>
                    <div>
                        <label class="block font-title text-xs text-grey-dark mb-1">
                            Type <span class="font-mono font-semibold text-orange-darker">{{ confirmTextOf(eraseConfirm) }}</span> to confirm:
                        </label>
                        <input
                            v-model="eraseConfirmInput"
                            type="text"
                            class="w-full font-mono bg-white p-2 rounded-md border-[2px] border-solid border-gray-300 text-sm focus:outline-none focus:border-red-dark"
                            :placeholder="confirmTextOf(eraseConfirm)"
                            data-test="erase-confirm-input"
                            @keyup.enter="confirmErase"
                        />
                    </div>
                    <div v-if="eraseError" class="text-xs text-red-dark font-title">{{ eraseError }}</div>
                </div>
                <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button
                        type="button"
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer"
                        :disabled="erasing"
                        @click="closeEraseConfirm"
                    >Cancel</button>
                    <button
                        type="button"
                        :class="['px-4 py-2 rounded font-title text-sm text-white flex items-center gap-2', (erasing || eraseConfirmInput.trim() !== confirmTextOf(eraseConfirm)) ? 'bg-red-dark/50 cursor-not-allowed' : 'bg-red-dark hover:bg-red-900 cursor-pointer']"
                        :disabled="erasing || eraseConfirmInput.trim() !== confirmTextOf(eraseConfirm)"
                        data-test="erase-confirm-button"
                        @click="confirmErase"
                    >
                        <FontAwesomeIcon v-if="erasing" icon="fa-solid fa-circle-notch" class="animate-spin" />
                        Erase Data
                    </button>
                </div>
            </div>
        </div>
    </Teleport>
</template>
