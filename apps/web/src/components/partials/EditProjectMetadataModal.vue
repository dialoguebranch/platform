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
const languageMappings = ref([]);

const languageMappingsOpen = ref(false);
const newSourceName = ref('');
const newSourceCode = ref('');
const newTranslationName = ref('');
const newTranslationCode = ref('');
const addingMapping = ref(false);
const addMappingError = ref('');

watch(() => props.projectSlug, load, { immediate: true });

function load() {
    loading.value = true;
    error.value = '';
    client.getProject(props.projectSlug)
        .then((project) => {
            displayName.value = project.displayName ?? '';
            description.value = project.description ?? '';
            latestVersion.value = project.latestVersion ?? null;
            languageMappings.value = [...(project.languageMappings ?? [])].sort((a, b) =>
                a.sourceLanguageCode.localeCompare(b.sourceLanguageCode) ||
                a.translationLanguageCode.localeCompare(b.translationLanguageCode)
            );
        })
        .catch(() => { error.value = 'Failed to load project data.'; })
        .finally(() => { loading.value = false; });
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

function onAddMapping() {
    addMappingError.value = '';
    if (!newSourceName.value.trim() || !newSourceCode.value.trim() ||
        !newTranslationName.value.trim() || !newTranslationCode.value.trim()) {
        addMappingError.value = 'All four fields are required.';
        return;
    }
    addingMapping.value = true;
    client.addLanguageMapping(
        props.projectSlug,
        newSourceName.value.trim(), newSourceCode.value.trim(),
        newTranslationName.value.trim(), newTranslationCode.value.trim()
    ).then((mapping) => {
        languageMappings.value.push(mapping);
        newSourceName.value = '';
        newSourceCode.value = '';
        newTranslationName.value = '';
        newTranslationCode.value = '';
        logEvent('project', 'Language mapping added to $1', props.projectSlug);
    })
    .catch(() => { addMappingError.value = 'Failed to add language mapping.'; })
    .finally(() => { addingMapping.value = false; });
}

function onRemoveMapping(mapping) {
    client.removeLanguageMapping(props.projectSlug, mapping.id)
        .then(() => {
            languageMappings.value = languageMappings.value.filter(m => m.id !== mapping.id);
            logEvent('project', 'Language mapping removed from $1', props.projectSlug);
        })
        .catch(() => { error.value = 'Failed to remove language mapping.'; });
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="emit('close')">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-pen" />
                        Edit Project Metadata
                    </div>
                    <button class="text-orange-light hover:text-white cursor-pointer" @click="emit('close')">
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

                    <!-- Language mappings (collapsible) -->
                    <div class="border border-grey-light rounded-lg">
                        <button
                            type="button"
                            :class="['flex items-center gap-2 w-full px-4 py-3 bg-grey-lighter hover:bg-grey-light font-title font-bold text-sm text-orange-darker cursor-pointer select-none transition-colors', languageMappingsOpen ? 'rounded-t-lg' : 'rounded-lg']"
                            @click="languageMappingsOpen = !languageMappingsOpen"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-language" class="text-base" />
                            Language Mappings
                            <span class="ml-1 text-xs font-normal text-grey-dark">({{ languageMappings.length }})</span>
                            <FontAwesomeIcon :icon="languageMappingsOpen ? 'fa-solid fa-caret-up' : 'fa-solid fa-caret-down'" class="ml-auto text-orange-medium" />
                        </button>

                        <div v-if="languageMappingsOpen" class="px-4 py-3 flex flex-col gap-3">
                            <!-- Existing mappings -->
                            <div v-if="languageMappings.length === 0" class="text-sm text-grey-dark italic">No language mappings defined.</div>
                            <table v-else class="w-full text-xs font-title border border-grey-light rounded-lg overflow-hidden">
                                <thead class="bg-grey-lighter text-orange-darker font-semibold">
                                    <tr>
                                        <th class="text-left px-3 py-2">Source</th>
                                        <th class="text-left px-3 py-2">Code</th>
                                        <th class="text-left px-3 py-2">Translation</th>
                                        <th class="text-left px-3 py-2">Code</th>
                                        <th class="px-2 py-2"></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="m in languageMappings" :key="m.id" class="border-t border-grey-lighter hover:bg-grey-lighter">
                                        <td class="px-3 py-2">{{ m.sourceLanguageName }}</td>
                                        <td class="px-3 py-2 font-mono text-orange-darker">{{ m.sourceLanguageCode }}</td>
                                        <td class="px-3 py-2">{{ m.translationLanguageName }}</td>
                                        <td class="px-3 py-2 font-mono text-orange-darker">{{ m.translationLanguageCode }}</td>
                                        <td class="px-2 py-2 text-right">
                                            <button class="text-red-dark hover:text-red-darker cursor-pointer" title="Remove mapping" @click="onRemoveMapping(m)">
                                                <FontAwesomeIcon icon="fa-solid fa-trash" />
                                            </button>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>

                            <!-- Add new mapping -->
                            <div class="border border-dashed border-orange-medium rounded-lg px-3 py-3 flex flex-col gap-2">
                                <span class="font-title font-semibold text-xs text-orange-darker">Add Language Mapping</span>
                                <div class="grid grid-cols-2 gap-2">
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Source language name</label>
                                        <TextInput class="w-full" v-model="newSourceName" placeholder="e.g. English" />
                                    </div>
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Source code</label>
                                        <TextInput class="w-full" v-model="newSourceCode" placeholder="e.g. en" />
                                    </div>
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Translation language name</label>
                                        <TextInput class="w-full" v-model="newTranslationName" placeholder="e.g. Dutch" />
                                    </div>
                                    <div>
                                        <label class="text-xs text-grey-dark mb-0.5 block">Translation code</label>
                                        <TextInput class="w-full" v-model="newTranslationCode" placeholder="e.g. nl" />
                                    </div>
                                </div>
                                <div v-if="addMappingError" class="text-xs text-red-dark font-title">{{ addMappingError }}</div>
                                <div>
                                    <PushButton text="Add Mapping" :loading="addingMapping" @click="onAddMapping" />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Footer -->
                <div v-if="!loading" class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <button class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" @click="emit('close')">Cancel</button>
                    <PushButton text="Save" variant="green" :loading="saving" @click="onSave" />
                </div>
            </div>
        </div>
    </Teleport>
</template>
