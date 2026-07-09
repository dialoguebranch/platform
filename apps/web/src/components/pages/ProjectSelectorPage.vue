<script setup>
import { inject, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '../../composables/client.js';
import { useStateManagement } from '../../composables/state-management.js';
import CreateProjectWizardModal from '../partials/CreateProjectWizardModal.vue';

const state = inject('state');
const client = useClient();
const stateManagement = useStateManagement();

const projects = ref([]);
const loading = ref(true);
const errorMessage = ref('');

// Tracks which project descriptions overflow a 3-line clamp, and which cards have been
// manually expanded to show the full description.
const descriptionEls = {};
const truncatedDescriptions = reactive(new Set());
const expandedDescriptions = reactive(new Set());

// A ResizeObserver fires once layout has actually settled (fonts loaded, etc.), which
// is more reliable than guessing a fixed delay before measuring scrollHeight/clientHeight.
// While a card is expanded its paragraph is unclamped, so measuring it would always read
// as "not truncated" — skip those and keep whatever truncation state was last measured
// while collapsed.
const resizeObserver = typeof ResizeObserver !== 'undefined' ? new ResizeObserver((entries) => {
    for (const entry of entries) {
        const slug = entry.target.dataset.projectSlug;
        if (expandedDescriptions.has(slug)) continue;
        if (entry.target.scrollHeight > entry.target.clientHeight + 1) {
            truncatedDescriptions.add(slug);
        } else {
            truncatedDescriptions.delete(slug);
        }
    }
}) : null;

function setDescriptionRef(slug, el) {
    const previous = descriptionEls[slug];
    if (previous && resizeObserver) resizeObserver.unobserve(previous);

    if (el) {
        el.dataset.projectSlug = slug;
        descriptionEls[slug] = el;
        if (resizeObserver) {
            resizeObserver.observe(el);
        } else if (el.scrollHeight > el.clientHeight + 1) {
            truncatedDescriptions.add(slug);
        }
    } else {
        delete descriptionEls[slug];
    }
}

// While collapsed: clamp to 3 lines normally, or to 2 lines (freeing up the third line for
// the "[Read more...]" link) once the description is known to overflow 3 lines.
function descriptionClampClass(slug) {
    if (expandedDescriptions.has(slug)) return '';
    return truncatedDescriptions.has(slug) ? 'line-clamp-2' : 'line-clamp-3';
}

function isDescriptionTruncated(slug) {
    return truncatedDescriptions.has(slug) && !expandedDescriptions.has(slug);
}

function expandDescription(slug) {
    expandedDescriptions.add(slug);
}

function collapseDescription(slug) {
    expandedDescriptions.delete(slug);
}

onBeforeUnmount(() => {
    if (resizeObserver) resizeObserver.disconnect();
});

const showCreateWizard = ref(false);

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
    state.value.selectedProject = { slug: project.slug, displayName: project.displayName ?? project.slug };
}

function onLogoutClick() {
    stateManagement.logout();
}

const isAdmin = state.value.user?.roles?.includes('admin');

function onProjectCreated(project) {
    projects.value.push(project);
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
                    type="button"
                    :class="['flex items-center gap-1.5 px-3 py-1.5 rounded text-white text-xs font-title font-semibold transition-colors', isAdmin ? 'bg-orange-darker hover:bg-orange-dark cursor-pointer' : 'bg-orange-medium cursor-not-allowed opacity-60']"
                    :disabled="!isAdmin"
                    :title="isAdmin ? '' : 'Only administrators can create new projects'"
                    @click="isAdmin && (showCreateWizard = true)"
                >
                    <FontAwesomeIcon icon="fa-solid fa-plus" />
                    New Project
                </button>
            </div>

            <CreateProjectWizardModal
                v-if="showCreateWizard"
                @close="showCreateWizard = false"
                @created="onProjectCreated"
            />

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
                <p class="mt-1">{{ isAdmin ? 'Use the New Project button above to create one.' : 'Contact an administrator to have a project created for you.' }}</p>
            </div>

            <!-- Project list -->
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                    v-for="project in projects"
                    :key="project.slug"
                    type="button"
                    class="text-left bg-box rounded-xl px-5 py-4 hover:shadow-md hover:border-orange-darker border border-transparent transition-all cursor-pointer group"
                    @click="selectProject(project)"
                >
                    <div class="flex items-start justify-between gap-2">
                        <div class="min-w-0">
                            <div class="font-title font-semibold text-sm group-hover:text-orange-darker transition-colors truncate">
                                {{ project.displayName ?? project.slug }}
                            </div>
                            <div class="font-mono text-xs text-grey-dark mt-0.5 truncate">{{ project.slug }}</div>
                        </div>
                        <FontAwesomeIcon icon="fa-solid fa-circle-arrow-right" class="text-orange-medium group-hover:text-orange-darker transition-colors shrink-0 mt-0.5" />
                    </div>
                    <div v-if="project.description" class="mt-2">
                        <p
                            :ref="(el) => setDescriptionRef(project.slug, el)"
                            :class="['text-xs text-grey-dark', descriptionClampClass(project.slug)]"
                        >{{ project.description }}</p>
                        <span
                            v-if="isDescriptionTruncated(project.slug)"
                            class="text-xs text-orange-darker hover:underline cursor-pointer font-title font-semibold"
                            @click.stop="expandDescription(project.slug)"
                        >[Read more...]</span>
                        <span
                            v-else-if="expandedDescriptions.has(project.slug)"
                            class="text-xs text-orange-darker hover:underline cursor-pointer font-title font-semibold"
                            @click.stop="collapseDescription(project.slug)"
                        >[Read less...]</span>
                    </div>
                    <div v-if="project.latestVersion" class="text-xs text-grey-dark mt-2">
                        <span class="font-bold">Version</span> {{ project.latestVersion.versionNumber }}
                    </div>
                    <div v-else class="text-xs text-orange-darker mt-2 italic">No published version</div>
                </button>
            </div>
        </div>
    </div>
</template>
