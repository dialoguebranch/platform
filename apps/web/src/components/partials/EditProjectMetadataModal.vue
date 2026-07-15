<script setup>
import { ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { logEvent } from '../../composables/debug-log.js';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';

const props = defineProps({
    projectSlug: { type: String, required: true },
});

const emit = defineEmits(['close', 'saved']);

const client = useClient();

const loading = ref(true);
const saving = ref(false);
const error = ref('');

const displayName = ref('');
const description = ref('');
const latestVersion = ref(null);
const sourceLanguageCode = ref('');
const sourceLanguageName = ref('');
const translationLanguages = ref([]);

const translationLanguagesOpen = ref(false);
const newTranslationName = ref('');
const newTranslationCode = ref('');
const addingTranslationLanguage = ref(false);
const addTranslationLanguageError = ref('');

watch(() => props.projectSlug, load, { immediate: true });

function load() {
    loading.value = true;
    error.value = '';
    client.getProject(props.projectSlug)
        .then((project) => {
            displayName.value = project.displayName ?? '';
            description.value = project.description ?? '';
            latestVersion.value = project.latestVersion ?? null;
            sourceLanguageCode.value = project.sourceLanguageCode ?? '';
            sourceLanguageName.value = project.sourceLanguageName ?? '';
            translationLanguages.value = [...(project.translationLanguages ?? [])].sort((a, b) =>
                a.translationLanguageCode.localeCompare(b.translationLanguageCode)
            );
        })
        .catch(() => { error.value = 'Failed to load project data.'; })
        .finally(() => { loading.value = false; });
}

// A pending save's `.then()` still fires `emit('saved', ...)` after the modal closes if the user
// closes it mid-save — guard the close paths (header X, Cancel, backdrop click) so the user can't
// dismiss the modal while that request is still in flight.
function closeModal() {
    if (saving.value) return;
    emit('close');
}

function onSave() {
    saving.value = true;
    error.value = '';
    client.updateProject(props.projectSlug, displayName.value, description.value)
        .then((updated) => {
            logEvent('project', 'Metadata saved for project $1', props.projectSlug);
            emit('saved', { slug: updated.slug, displayName: updated.displayName });
        })
        .catch(() => { error.value = 'Failed to save project metadata.'; })
        .finally(() => { saving.value = false; });
}

function onAddTranslationLanguage() {
    addTranslationLanguageError.value = '';
    if (!newTranslationName.value.trim() || !newTranslationCode.value.trim()) {
        addTranslationLanguageError.value = 'Both fields are required.';
        return;
    }
    addingTranslationLanguage.value = true;
    client.addTranslationLanguage(
        props.projectSlug,
        newTranslationName.value.trim(), newTranslationCode.value.trim()
    ).then((translationLanguage) => {
        translationLanguages.value.push(translationLanguage);
        newTranslationName.value = '';
        newTranslationCode.value = '';
        logEvent('project', 'Translation language added to $1', props.projectSlug);
    })
    .catch(() => { addTranslationLanguageError.value = 'Failed to add translation language.'; })
    .finally(() => { addingTranslationLanguage.value = false; });
}

function onRemoveTranslationLanguage(translationLanguage) {
    client.removeTranslationLanguage(props.projectSlug, translationLanguage.id)
        .then(() => {
            translationLanguages.value = translationLanguages.value.filter(t => t.id !== translationLanguage.id);
            logEvent('project', 'Translation language removed from $1', props.projectSlug);
        })
        .catch(() => { error.value = 'Failed to remove translation language.'; });
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="closeModal">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-pen" />
                        Edit Project Metadata
                    </div>
                    <button
                        class="text-orange-light hover:text-white disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:text-orange-light"
                        :class="{ 'cursor-pointer': !saving }"
                        :disabled="saving"
                        @click="closeModal"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <!-- Loading -->
                <div v-if="loading" class="flex items-center justify-center py-12 text-orange-darker">
                    <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin text-2xl" />
                </div>

                <!-- Content -->
                <div v-else class="overflow-y-auto px-5 py-4 flex flex-col gap-5" style="max-height: calc(100vh - 10rem)">

                    <!-- Error banner -->
                    <div v-if="error" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                        {{ error }}
                    </div>

                    <!-- Display name -->
                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Display Name</label>
                        <TextInput class="w-full" v-model="displayName" placeholder="Project display name..." />
                    </div>

                    <!-- Description -->
                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Description</label>
                        <textarea
                            v-model="description"
                            rows="3"
                            placeholder="Project description..."
                            class="w-full px-3 py-2 border border-grey-light rounded-lg text-sm font-title focus:outline-none focus:border-orange-dark resize-none"
                        ></textarea>
                    </div>

                    <!-- Source language (read-only — fixed at project creation) -->
                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Source Language</label>
                        <div class="px-3 py-2 border border-grey-light rounded-lg text-sm font-title bg-grey-lighter text-grey-dark">
                            {{ sourceLanguageName }} <span class="font-mono text-xs">({{ sourceLanguageCode }})</span>
                        </div>
                    </div>

                    <!-- Published version info (read-only) -->
                    <div v-if="latestVersion" class="bg-grey-lighter border border-grey-light rounded-lg px-4 py-3">
                        <div class="flex items-center gap-1.5 font-title text-xs font-semibold text-orange-darker mb-2 uppercase tracking-wide">
                            <FontAwesomeIcon icon="fa-solid fa-lock" class="text-[10px]" />
                            Latest Published Version
                        </div>
                        <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 font-mono text-xs text-grey-dark">
                            <dt class="font-title font-semibold text-orange-darker">Version</dt>
                            <dd>{{ latestVersion.versionNumber }}</dd>
                            <dt class="font-title font-semibold text-orange-darker">Published At</dt>
                            <dd>{{ latestVersion.publishedAt }}</dd>
                            <dt class="font-title font-semibold text-orange-darker">ID</dt>
                            <dd class="truncate">{{ latestVersion.id }}</dd>
                        </dl>
                    </div>

                    <!-- Translation languages (collapsible) -->
                    <div class="border border-grey-light rounded-lg">
                        <button
                            type="button"
                            :class="['flex items-center gap-2 w-full px-4 py-3 bg-grey-lighter hover:bg-grey-light font-title font-bold text-sm text-orange-darker cursor-pointer select-none transition-colors', translationLanguagesOpen ? 'rounded-t-lg' : 'rounded-lg']"
                            @click="translationLanguagesOpen = !translationLanguagesOpen"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-language" class="text-base" />
                            Translation Languages
                            <span class="ml-1 text-xs font-normal text-grey-dark">({{ translationLanguages.length }})</span>
                            <FontAwesomeIcon :icon="translationLanguagesOpen ? 'fa-solid fa-caret-up' : 'fa-solid fa-caret-down'" class="ml-auto text-orange-medium" />
                        </button>

                        <div v-if="translationLanguagesOpen" class="px-4 py-3 flex flex-col gap-3">
                            <!-- Existing translation languages -->
                            <div v-if="translationLanguages.length === 0" class="text-sm text-grey-dark italic">No translation languages defined.</div>
                            <table v-else class="w-full text-xs font-title border border-grey-light rounded-lg overflow-hidden">
                                <thead class="bg-grey-lighter text-orange-darker font-semibold">
                                    <tr>
                                        <th class="text-left px-3 py-2">Translation</th>
                                        <th class="text-left px-3 py-2">Code</th>
                                        <th class="px-2 py-2"></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="t in translationLanguages" :key="t.id" class="border-t border-grey-lighter hover:bg-grey-lighter">
                                        <td class="px-3 py-2">{{ t.translationLanguageName }}</td>
                                        <td class="px-3 py-2 font-mono text-orange-darker">{{ t.translationLanguageCode }}</td>
                                        <td class="px-2 py-2 text-right">
                                            <button class="text-red-dark hover:text-red-darker cursor-pointer" title="Remove translation language" @click="onRemoveTranslationLanguage(t)">
                                                <FontAwesomeIcon icon="fa-solid fa-trash" />
                                            </button>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>

                            <!-- Add new translation language -->
                            <div class="border border-dashed border-orange-medium rounded-lg px-3 py-3 flex flex-col gap-2">
                                <span class="font-title font-semibold text-xs text-orange-darker">Add Translation Language</span>
                                <div class="grid grid-cols-2 gap-2">
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Language name</label>
                                        <TextInput class="w-full" v-model="newTranslationName" placeholder="e.g. Dutch" />
                                    </div>
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Language code</label>
                                        <TextInput class="w-full" v-model="newTranslationCode" placeholder="e.g. nl" />
                                    </div>
                                </div>
                                <div v-if="addTranslationLanguageError" class="text-xs text-red-dark font-title">{{ addTranslationLanguageError }}</div>
                                <div>
                                    <PushButton text="Add Language" :loading="addingTranslationLanguage" @click="onAddTranslationLanguage" />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Footer -->
                <div v-if="!loading" class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <button
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                        :class="{ 'cursor-pointer': !saving }"
                        :disabled="saving"
                        @click="closeModal"
                    >Cancel</button>
                    <PushButton text="Save" variant="green" :loading="saving" @click="onSave" />
                </div>
            </div>
        </div>
    </Teleport>
</template>
