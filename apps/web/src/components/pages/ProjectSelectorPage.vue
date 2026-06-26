<script setup>
import { inject, onMounted, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { useStateManagement } from '../../composables/state-management.js';
import PushButton from '../widgets/PushButton.vue';
import TextInput from '../widgets/TextInput.vue';

const state = inject('state');
const client = useClient();
const stateManagement = useStateManagement();

const projects = ref([]);
const loading = ref(true);
const errorMessage = ref('');

// Create-project form
const showCreateForm = ref(false);
const createName = ref('');
const createDisplayName = ref('');
const createDescription = ref('');
const createErrors = ref({});
const creating = ref(false);
const createError = ref('');

onMounted(() => {
    loadProjects();
});

function loadProjects() {
    loading.value = true;
    errorMessage.value = '';
    client.listProjects()
        .then((data) => {
            projects.value = data;
        })
        .catch(() => {
            errorMessage.value = 'Failed to load projects. Please try again.';
        })
        .finally(() => {
            loading.value = false;
        });
}

function selectProject(project) {
    state.value.selectedProject = project.name;
}

function onLogoutClick() {
    stateManagement.logout();
}

const isAdmin = state.value.user?.roles?.includes('admin');

function toggleCreateForm() {
    showCreateForm.value = !showCreateForm.value;
    createName.value = '';
    createDisplayName.value = '';
    createDescription.value = '';
    createErrors.value = {};
    createError.value = '';
}

function submitCreateProject() {
    createErrors.value = {};
    createError.value = '';

    const name = createName.value.trim();
    const displayName = createDisplayName.value.trim();

    if (!name) createErrors.value.name = true;
    if (!displayName) createErrors.value.displayName = true;
    if (Object.keys(createErrors.value).length > 0) return;

    creating.value = true;
    client.createProject(name, displayName, createDescription.value.trim())
        .then((project) => {
            showCreateForm.value = false;
            projects.value.push(project);
        })
        .catch(() => {
            createError.value = 'Failed to create project. The name may already be in use.';
        })
        .finally(() => {
            creating.value = false;
        });
}
</script>

<template>
    <div class="min-w-screen min-h-screen bg-background flex flex-col items-center">
        <div class="w-full flex justify-end px-4 pt-3">
            <button
                type="button"
                class="font-title text-xs text-grey-dark hover:text-orange-darker cursor-pointer"
                @click="onLogoutClick"
            >Log out</button>
        </div>

        <img class="block pt-8 w-[240px] sm:w-[480px]" src="../../assets/img/dlb-long.png" alt="Dialogue Branch" />
        <div class="mt-2 font-title font-bold">Web Client Test Application</div>

        <div class="w-full px-4 mt-8 pb-12 max-w-3xl mx-auto">
            <div class="flex items-center justify-between mb-4">
                <h2 class="font-title font-bold text-lg">Select a Project</h2>
                <button
                    v-if="isAdmin"
                    type="button"
                    class="flex items-center gap-1.5 px-3 py-1.5 rounded bg-orange-darker text-white text-xs font-title font-semibold hover:bg-orange-dark cursor-pointer"
                    @click="toggleCreateForm"
                >
                    <FontAwesomeIcon :icon="showCreateForm ? 'fa-solid fa-xmark' : 'fa-solid fa-plus'" />
                    {{ showCreateForm ? 'Cancel' : 'New Project' }}
                </button>
            </div>

            <!-- Create project form -->
            <div v-if="showCreateForm" class="bg-box rounded-xl px-5 py-4 mb-4">
                <h3 class="font-title font-semibold text-sm text-orange-darker mb-3">Create New Project</h3>
                <div class="flex flex-col gap-3">
                    <div class="sm:flex sm:items-center gap-3">
                        <label class="font-title text-sm font-semibold shrink-0 sm:w-32 sm:text-right">Name <span class="text-red-500">*</span></label>
                        <div class="mt-1 sm:mt-0 grow">
                            <TextInput
                                v-model="createName"
                                placeholder="unique-slug"
                                :error="createErrors.name"
                                class="w-full"
                            />
                            <p class="text-xs text-grey-dark mt-0.5">Unique identifier, lowercase, no spaces (e.g. my-project)</p>
                        </div>
                    </div>
                    <div class="sm:flex sm:items-center gap-3">
                        <label class="font-title text-sm font-semibold shrink-0 sm:w-32 sm:text-right">Display Name <span class="text-red-500">*</span></label>
                        <TextInput
                            v-model="createDisplayName"
                            placeholder="My Project"
                            :error="createErrors.displayName"
                            class="mt-1 sm:mt-0 grow"
                        />
                    </div>
                    <div class="sm:flex sm:items-center gap-3">
                        <label class="font-title text-sm font-semibold shrink-0 sm:w-32 sm:text-right">Description</label>
                        <TextInput
                            v-model="createDescription"
                            placeholder="Optional description..."
                            class="mt-1 sm:mt-0 grow"
                        />
                    </div>
                    <div v-if="createError" class="text-red-500 text-xs font-title">{{ createError }}</div>
                    <div class="flex justify-end">
                        <PushButton text="Create Project" :disabled="creating" @click="submitCreateProject" />
                    </div>
                </div>
            </div>

            <!-- Loading state -->
            <div v-if="loading" class="text-center py-12 text-grey-dark font-title text-sm">
                <FontAwesomeIcon icon="fa-solid fa-circle-notch" class="animate-spin mr-2" />
                Loading projects...
            </div>

            <!-- Error state -->
            <div v-else-if="errorMessage" class="text-center py-8">
                <p class="text-red-500 font-title text-sm mb-3">{{ errorMessage }}</p>
                <button
                    type="button"
                    class="px-3 py-1.5 rounded bg-orange-darker text-white text-xs font-title font-semibold hover:bg-orange-dark cursor-pointer"
                    @click="loadProjects"
                >Try again</button>
            </div>

            <!-- Empty state -->
            <div v-else-if="projects.length === 0" class="text-center py-12 text-grey-dark font-title text-sm">
                <FontAwesomeIcon icon="fa-solid fa-folder-open" class="text-3xl mb-3 text-grey-light" />
                <p>No projects available.</p>
                <p v-if="isAdmin" class="mt-1">Use the <strong>New Project</strong> button above to create one.</p>
            </div>

            <!-- Project list -->
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                    v-for="project in projects"
                    :key="project.name"
                    type="button"
                    class="text-left bg-box rounded-xl px-5 py-4 hover:shadow-md hover:border-orange-darker border border-transparent transition-all cursor-pointer group"
                    @click="selectProject(project)"
                >
                    <div class="flex items-start justify-between gap-2">
                        <div class="min-w-0">
                            <div class="font-title font-semibold text-sm group-hover:text-orange-darker transition-colors truncate">
                                {{ project.displayName ?? project.name }}
                            </div>
                            <div class="font-mono text-xs text-grey-dark mt-0.5 truncate">{{ project.name }}</div>
                        </div>
                        <FontAwesomeIcon icon="fa-solid fa-circle-arrow-right" class="text-grey-light group-hover:text-orange-darker transition-colors shrink-0 mt-0.5" />
                    </div>
                    <p v-if="project.description" class="text-xs text-grey-dark mt-2 line-clamp-2">{{ project.description }}</p>
                    <div v-if="project.latestVersion" class="text-xs text-grey-dark mt-2">
                        Version {{ project.latestVersion.versionNumber }}
                    </div>
                    <div v-else class="text-xs text-orange-darker mt-2 italic">No published version</div>
                </button>
            </div>
        </div>
    </div>
</template>
