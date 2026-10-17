<script setup>
import { ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import PushButton from '../widgets/PushButton.vue';

// Lists a dialogue's stale translation entries per language (#375): entries that match no
// translatable term of the dialogue on speaker and source text, so they are never used. Deleting
// one, or all of a language's, asks for confirmation first; the Translation Editor does the
// actual saving.

const props = defineProps({
    // [{ language: { code, name }, entries: [{ speaker, contexts, text, translation }] }, ...],
    // only languages that have stale entries.
    sections: { type: Array, default: () => [] },
    // A deletion is being saved.
    saving: { type: Boolean, default: false },
});

const emit = defineEmits(['close', 'delete']);

// { languageCode, entries, label } awaiting confirmation, or null.
const pending = ref(null);

function askDelete(language, entries, label) {
    pending.value = { languageCode: language.code, entries, label };
}

function confirmDelete() {
    if (!pending.value) return;
    emit('delete', { languageCode: pending.value.languageCode, entries: pending.value.entries });
    pending.value = null;
}

function closeModal() {
    if (props.saving) return;
    emit('close');
}

function entryKey(entry) {
    return JSON.stringify([entry.speaker, ...entry.contexts, entry.text]);
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="closeModal">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-2xl mx-4 flex flex-col max-h-[85vh]">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-language" />
                        Stale Translation Entries
                    </div>
                    <button
                        class="text-orange-light hover:text-white disabled:opacity-40"
                        :class="{ 'cursor-pointer': !saving }"
                        :disabled="saving"
                        @click="closeModal"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <div class="px-5 py-4 flex flex-col gap-4 overflow-y-auto">
                    <p class="text-sm font-title text-grey-dark">
                        These entries match no text of this dialogue (by speaker and text), so they are never used, for example
                        because the text was changed or removed after it was translated. They are kept until you delete them.
                    </p>

                    <p v-if="sections.length === 0" class="text-sm font-title text-grey-dark" data-test="stale-none">
                        There are no stale entries left.
                    </p>

                    <section v-for="section in sections" :key="section.language.code" class="flex flex-col gap-2" :data-test="`stale-section-${section.language.code}`">
                        <div class="flex items-center justify-between gap-3">
                            <h3 class="font-title font-bold text-sm text-orange-darker">
                                {{ section.language.name }} ({{ section.entries.length }})
                            </h3>
                            <button
                                type="button"
                                class="text-xs font-title text-red-dark hover:underline disabled:opacity-40 cursor-pointer"
                                :disabled="saving"
                                data-test="stale-delete-all"
                                @click="askDelete(section.language, section.entries, section.entries.length === 1 ? `the stale ${section.language.name} entry` : `all ${section.entries.length} stale ${section.language.name} entries`)"
                            >Delete all</button>
                        </div>
                        <ul class="flex flex-col gap-1">
                            <li
                                v-for="entry in section.entries"
                                :key="entryKey(entry)"
                                class="flex items-start gap-2 bg-grey-lighter rounded px-2 py-1.5 text-xs font-title"
                                data-test="stale-entry"
                            >
                                <div class="flex-1 min-w-0">
                                    <div class="text-grey-dark">
                                        <span class="font-bold text-orange-darker">{{ entry.speaker }}</span>
                                        <span v-if="entry.contexts.length" class="ml-1">({{ entry.contexts.join(', ') }})</span>
                                    </div>
                                    <div class="break-words">{{ entry.text }}</div>
                                    <div class="break-words text-grey-dark">{{ entry.translation }}</div>
                                </div>
                                <button
                                    type="button"
                                    class="text-grey-dark hover:text-red-dark disabled:opacity-40 cursor-pointer shrink-0 pt-0.5"
                                    title="Delete this entry"
                                    :disabled="saving"
                                    data-test="stale-delete"
                                    @click="askDelete(section.language, [entry], `this ${section.language.name} entry`)"
                                >
                                    <FontAwesomeIcon icon="fa-solid fa-trash" />
                                </button>
                            </li>
                        </ul>
                    </section>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-between gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <template v-if="pending">
                        <span class="text-sm font-title text-red-dark" data-test="stale-confirm-text">Delete {{ pending.label }}?</span>
                        <div class="flex gap-2">
                            <button
                                class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer"
                                @click="pending = null"
                            >Cancel</button>
                            <PushButton text="Delete" data-test="stale-confirm" @click="confirmDelete" />
                        </div>
                    </template>
                    <template v-else>
                        <span></span>
                        <PushButton text="Close" variant="green" :loading="saving" @click="closeModal" />
                    </template>
                </div>
            </div>
        </div>
    </Teleport>
</template>
