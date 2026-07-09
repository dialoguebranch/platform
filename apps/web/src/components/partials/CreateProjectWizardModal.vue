<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { logEvent } from '../../composables/debug-log.js';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';

const emit = defineEmits(['close', 'created']);

const client = useClient();

const TOTAL_STEPS = 3;
const currentStep = ref(1);

const slug = ref('');
const displayName = ref('');
const description = ref('');
const stepErrors = ref({});
const creating = ref(false);
const createError = ref('');

// Step 3: default language selection, sourced from a bundled ISO 639 dataset (tag3 = ISO 639-3
// code, tag1 = BCP-47/ISO 639-1 code where one exists, name = English name, autonym = native
// name). Lazy-loaded since it's ~300KB and only needed once this step is reached.
const languageEntries = ref([]);

import('../../assets/data/iso639-autonyms.json')
    .then((mod) => { languageEntries.value = mod.default; });

// "Base" languages: entries with a bare (non-regional) BCP-47 code, e.g. "en", "nl" — these
// populate the Simplified/Extended dropdowns.
const baseLanguages = computed(() =>
    languageEntries.value
        .filter((entry) => entry.tag1 && !entry.tag1.includes('-'))
        .map((entry) => ({ code: entry.tag1, name: entry.name, autonym: entry.autonym ?? null }))
        .sort((a, b) => a.name.localeCompare(b.name))
);

const SIMPLIFIED_CODES = ['en', 'es', 'fr', 'de', 'nl', 'pt', 'it', 'zh', 'ja', 'ar'];
const simplifiedLanguages = computed(() =>
    baseLanguages.value.filter((lang) => SIMPLIFIED_CODES.includes(lang.code))
);

// Lookup maps for recognizing a typed custom code: by BCP-47 tag (covers both bare codes like
// "en" and real region-qualified variants like "en-US" that are actually present in the
// dataset), and by ISO 639-3 tag (covers 3-letter codes and languages with no BCP-47 code at
// all). A dashed code with no exact match falls back to its primary subtag — e.g. "de-US" isn't
// a real entry in the dataset, so it resolves to plain "German" rather than a fabricated
// "German (American)".
const tag1Lookup = computed(() => {
    const map = new Map();
    for (const entry of languageEntries.value) {
        if (entry.tag1) map.set(entry.tag1.toLowerCase(), entry);
    }
    return map;
});
const tag3Lookup = computed(() => {
    const map = new Map();
    for (const entry of languageEntries.value) {
        map.set(entry.tag3.toLowerCase(), entry);
    }
    return map;
});

function lookupLanguageEntry(code) {
    const lc = code.trim().toLowerCase();
    if (!lc) return null;
    return tag1Lookup.value.get(lc)
        ?? tag3Lookup.value.get(lc)
        ?? (lc.includes('-') ? tag1Lookup.value.get(lc.split('-')[0]) : null)
        ?? null;
}

function displayLanguageName(entry) {
    if (!entry) return '';
    return useAutonyms.value && entry.autonym ? entry.autonym : entry.name;
}

const languageMode = ref('simplified');
const selectedLanguageCode = ref('');
const customLanguageCode = ref('');
const customLanguageName = ref('');
const useAutonyms = ref(false);

const recognizedCustomLanguageEntry = computed(() => lookupLanguageEntry(customLanguageCode.value));
const recognizedCustomLanguageName = computed(() => displayLanguageName(recognizedCustomLanguageEntry.value));

// Auto-fill (and lock) the name field when the code is recognized. When it stops being
// recognized, clear the stale auto-filled value — but only in that transition, so the user's
// own typing into an already-unrecognized code doesn't get wiped on every keystroke.
watch(recognizedCustomLanguageName, (newName, oldName) => {
    if (newName) {
        customLanguageName.value = newName;
    } else if (oldName) {
        customLanguageName.value = '';
    }
});

// A default language must be provided before a project can be created: a selection in
// Simplified/Extended mode, or both the code and name filled in for Custom mode.
const canCreateProject = computed(() => {
    if (languageMode.value === 'custom') {
        return customLanguageCode.value.trim().length > 0 && customLanguageName.value.trim().length > 0;
    }
    return selectedLanguageCode.value.trim().length > 0;
});

// The code/name pair that becomes the project's default LanguageSet source language, resolved
// from whichever mode is active.
const defaultLanguage = computed(() => {
    if (languageMode.value === 'custom') {
        return { code: customLanguageCode.value.trim(), name: customLanguageName.value.trim() };
    }
    const selected = baseLanguages.value.find((lang) => lang.code === selectedLanguageCode.value);
    return selected ? { code: selected.code, name: displayLanguageName(selected) } : { code: '', name: '' };
});

// Auto-derive the slug from the display name. Rather than a one-way "locked forever" flag,
// this checks whether the current slug still matches what the *previous* display name would
// have produced — if so, the user hasn't diverged from it (or has since cleared back to the
// same in-sync state), so it's safe to keep auto-updating.
function slugify(text) {
    return text.toLowerCase().replace(/\s+/g, '-');
}

watch(displayName, (newValue, oldValue) => {
    if (slug.value === slugify(oldValue ?? '')) {
        slug.value = slugify(newValue);
    }
});

// Live slug-availability check against the existing project list.
const existingProjectSlugs = ref(new Set());

onMounted(() => {
    client.listProjects()
        .then((data) => {
            existingProjectSlugs.value = new Set(data.map((p) => p.slug));
        })
        .catch(() => { /* Availability check is a convenience; ignore failures. */ });
});

const slugAvailability = computed(() => {
    const trimmed = slug.value.trim();
    if (!trimmed) return null;
    return existingProjectSlugs.value.has(trimmed) ? 'taken' : 'available';
});

const canProceedStep1 = computed(() =>
    slugAvailability.value === 'available' && displayName.value.trim().length > 0
);

const displayNameValid = computed(() => displayName.value.trim().length > 0);

function goNext() {
    if (currentStep.value === 1) {
        stepErrors.value = {};
        if (!slug.value.trim()) stepErrors.value.slug = true;
        if (slugAvailability.value === 'taken') stepErrors.value.slug = true;
        if (!displayName.value.trim()) stepErrors.value.displayName = true;
        if (Object.keys(stepErrors.value).length > 0) return;
    }
    currentStep.value += 1;
}

function goBack() {
    currentStep.value -= 1;
}

function submitCreate() {
    createError.value = '';
    creating.value = true;
    client.createProject(slug.value.trim(), displayName.value.trim(), description.value.trim(),
        defaultLanguage.value.code, defaultLanguage.value.name)
        .then((project) => {
            logEvent('project', 'Project $1 created', project.slug);
            emit('created', project);
            emit('close');
        })
        .catch(() => {
            createError.value = 'Failed to create project. The slug may already be in use.';
        })
        .finally(() => {
            creating.value = false;
        });
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="emit('close')">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-xl mx-4 flex flex-col">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-wand-magic-sparkles" />
                        Create New Project
                    </div>
                    <div class="flex items-center gap-3">
                        <span class="text-orange-light text-xs font-title">Step {{ currentStep }} of {{ TOTAL_STEPS }}</span>
                        <button class="text-orange-light hover:text-white cursor-pointer" @click="emit('close')">
                            <FontAwesomeIcon icon="fa-solid fa-xmark" />
                        </button>
                    </div>
                </div>

                <!-- Content -->
                <div class="px-5 py-4 flex flex-col gap-4">

                    <!-- Step 1: Display Name + Slug -->
                    <template v-if="currentStep === 1">
                        <div>
                            <label class="block font-title font-bold text-sm text-orange-darker mb-1">Display Name <span class="text-red-500">*</span></label>
                            <div class="relative">
                                <TextInput class="w-full pr-9" v-model="displayName" placeholder="My Project" :error="stepErrors.displayName" />
                                <FontAwesomeIcon
                                    v-if="displayNameValid"
                                    icon="fa-solid fa-circle-check"
                                    class="absolute right-3 top-1/2 -translate-y-1/2 text-icon-button pointer-events-none"
                                />
                                <FontAwesomeIcon
                                    v-else
                                    icon="fa-solid fa-circle-xmark"
                                    class="absolute right-3 top-1/2 -translate-y-1/2 text-red-dark pointer-events-none"
                                />
                            </div>
                            <p v-if="!displayNameValid" class="text-xs text-red-dark mt-1">Display Name is required.</p>
                        </div>
                        <div>
                            <label class="block font-title font-bold text-sm text-orange-darker mb-1">Slug <span class="text-red-500">*</span></label>
                            <div class="relative">
                                <TextInput class="w-full pr-9" v-model="slug" placeholder="unique-slug" :error="stepErrors.slug" />
                                <FontAwesomeIcon
                                    v-if="slugAvailability === 'available'"
                                    icon="fa-solid fa-circle-check"
                                    class="absolute right-3 top-1/2 -translate-y-1/2 text-icon-button pointer-events-none"
                                />
                                <FontAwesomeIcon
                                    v-else-if="slugAvailability === 'taken'"
                                    icon="fa-solid fa-circle-xmark"
                                    class="absolute right-3 top-1/2 -translate-y-1/2 text-red-dark pointer-events-none"
                                />
                            </div>
                            <p v-if="slugAvailability === 'taken'" class="text-xs text-red-dark mt-1">This slug is already taken.</p>
                            <p v-else class="text-xs text-grey-dark mt-1">Unique identifier, lowercase, no spaces (e.g. my-project)</p>
                        </div>
                    </template>

                    <!-- Step 2: Description -->
                    <template v-else-if="currentStep === 2">
                        <div>
                            <label class="block font-title font-bold text-sm text-orange-darker mb-1">Description</label>
                            <textarea
                                v-model="description"
                                rows="4"
                                placeholder="Optional description..."
                                class="w-full px-3 py-2 border border-grey-light rounded-lg text-sm font-title focus:outline-none focus:border-orange-dark resize-none"
                            ></textarea>
                        </div>
                    </template>

                    <!-- Step 3: Default Language -->
                    <template v-else-if="currentStep === 3">
                        <h3 class="font-title font-bold text-base text-orange-darker">Define Default Language</h3>
                        <div v-if="createError" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                            {{ createError }}
                        </div>
                        <p class="text-sm text-grey-dark">
                            Next, you can indicate which language will be used by default in this
                            project. Select &ldquo;simplified&rdquo; for a short list of common
                            languages. Choose &ldquo;extended&rdquo; for a more extensive list.
                            Alternatively, choose &ldquo;Custom&rdquo; to fill in your own language
                            ISO code.
                        </p>
                        <div class="flex items-center justify-between">
                            <div class="flex gap-5">
                                <label class="flex items-center gap-1.5 text-sm font-title cursor-pointer">
                                    <input type="radio" value="simplified" v-model="languageMode" />
                                    Simplified
                                </label>
                                <label class="flex items-center gap-1.5 text-sm font-title cursor-pointer">
                                    <input type="radio" value="extended" v-model="languageMode" />
                                    Extended
                                </label>
                                <label class="flex items-center gap-1.5 text-sm font-title cursor-pointer">
                                    <input type="radio" value="custom" v-model="languageMode" />
                                    Custom
                                </label>
                            </div>
                            <label class="flex items-center gap-1.5 text-sm font-title cursor-pointer text-grey-dark">
                                <input type="checkbox" v-model="useAutonyms" />
                                Use autonyms
                            </label>
                        </div>
                        <div v-if="languageMode !== 'custom'">
                            <label class="block font-title font-bold text-sm text-orange-darker mb-1">Default Language:</label>
                            <select
                                v-model="selectedLanguageCode"
                                class="w-full px-3 py-2 border border-grey-light rounded-lg text-sm font-title focus:outline-none focus:border-orange-dark bg-white"
                            >
                                <option value="" disabled>Select a language...</option>
                                <option
                                    v-for="lang in languageMode === 'simplified' ? simplifiedLanguages : baseLanguages"
                                    :key="lang.code"
                                    :value="lang.code"
                                >{{ useAutonyms && lang.autonym ? lang.autonym : lang.name }} ({{ lang.code }})</option>
                            </select>
                        </div>
                        <template v-else>
                            <div>
                                <label class="block font-title font-bold text-sm text-orange-darker mb-1">Language Code <span class="text-red-500">*</span></label>
                                <TextInput class="w-full" v-model="customLanguageCode" placeholder="Enter an ISO language code, e.g. en-US" />
                            </div>
                            <div>
                                <label class="block font-title font-bold text-sm text-orange-darker mb-1">Language Name <span class="text-red-500">*</span></label>
                                <TextInput class="w-full disabled:bg-grey-lighter disabled:text-grey-dark disabled:cursor-not-allowed" v-model="customLanguageName" placeholder="e.g. Orcish (Mordor)" :disabled="!customLanguageCode.trim() || !!recognizedCustomLanguageName" />
                                <p class="text-xs text-grey-dark mt-1">
                                    <template v-if="!customLanguageCode.trim()">Enter a language code first.</template>
                                    <template v-else-if="recognizedCustomLanguageName">Filled in automatically from the recognized language code.</template>
                                    <template v-else>This code wasn't recognized as an official language code — please provide a name for it.</template>
                                </p>
                            </div>
                        </template>
                    </template>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-between gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <button
                        v-if="currentStep === 1"
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer"
                        @click="emit('close')"
                    >Cancel</button>
                    <button
                        v-else
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer"
                        @click="goBack"
                    >Back</button>

                    <PushButton v-if="currentStep < TOTAL_STEPS" text="Next" :disabled="currentStep === 1 && !canProceedStep1" @click="goNext" />
                    <PushButton v-else text="Create Project" variant="green" :disabled="!canCreateProject" :loading="creating" @click="submitCreate" />
                </div>
            </div>
        </div>
    </Teleport>
</template>
