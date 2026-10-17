<script setup>
import { computed, inject, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import PageHeader from '../widgets/PageHeader.vue';
import PageGreeting from '../widgets/PageGreeting.vue';
import ManagementMenu from '../widgets/ManagementMenu.vue';
import { useAuthoringClient } from '../../composables/client.js';
import CreateProjectWizardModal from '../partials/CreateProjectWizardModal.vue';
import ImportProjectModal from '../partials/ImportProjectModal.vue';

const state = inject('state');
const client = useAuthoringClient();

const projects = ref([]);
// The search field above the list: shows only the projects whose name, slug or description
// contains the text, case-insensitively.
const projectQuery = ref('');
const visibleProjects = computed(() => {
    const query = projectQuery.value.trim().toLowerCase();
    if (!query) return projects.value;
    return projects.value.filter((project) => [
        project.latestVersion?.displayName ?? project.draftDisplayName,
        project.slug,
        project.latestVersion?.description ?? project.draftDescription,
    ].some((text) => text?.toLowerCase().includes(query)));
});
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
const showImportModal = ref(false);

onMounted(() => {
    loadProjects();
    loadDeletedProjects();
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
    state.value.selectedProject = { slug: project.slug, displayName: project.latestVersion?.displayName ?? project.draftDisplayName ?? project.slug, latestVersion: project.latestVersion ?? null };
}


const isAdmin = state.value.user?.roles?.includes('admin');

function onProjectCreated(project) {
    projects.value.push(project);
}

// Deletion is deliberately buried behind a confirmation modal that requires typing the
// project's slug back — its dialogues (drafts and published versions) are gone for good, so it
// should never be a single misclick. Its users' data is kept unless ticked otherwise (#315).
const deleteConfirm = ref(null); // { slug, displayName }
const deleteConfirmInput = ref('');
// Destructive options, off by default (#315): deleting a project removes its dialogues, not its
// users' data, unless these are ticked.
const deleteVariables = ref(false);
const deleteLogs = ref(false);
const deleting = ref(false);
const deleteError = ref('');

function openDeleteConfirm(project) {
    deleteConfirm.value = { slug: project.slug, displayName: project.latestVersion?.displayName ?? project.draftDisplayName ?? project.slug };
    deleteConfirmInput.value = '';
    deleteVariables.value = false;
    deleteLogs.value = false;
    deleteError.value = '';
}

function closeDeleteConfirm() {
    if (deleting.value) return;
    deleteConfirm.value = null;
}

function confirmDelete() {
    if (deleteConfirmInput.value.trim() !== deleteConfirm.value.slug) return;
    deleting.value = true;
    deleteError.value = '';
    client.deleteProject(deleteConfirm.value.slug,
        { deleteVariables: deleteVariables.value, deleteLogs: deleteLogs.value })
        .then(() => {
            projects.value = projects.value.filter((p) => p.slug !== deleteConfirm.value.slug);
            deleteConfirm.value = null;
            loadDeletedProjects();
        })
        .catch(() => {
            deleteError.value = 'Failed to delete project. Please try again.';
        })
        .finally(() => {
            deleting.value = false;
        });
}

// ---- Deleted projects (#403) ----
// Projects whose dialogues were deleted but whose users' data was kept, listed for admins in a
// separate, collapsed section, each with a way to delete it for good, user data and all.

const deletedProjects = ref([]);
const showDeleted = ref(false);
const purgeConfirm = ref(null); // the deleted project being confirmed, or null
const purgeConfirmInput = ref('');
const purging = ref(false);
const purgeError = ref('');

function loadDeletedProjects() {
    if (!isAdmin) return;
    client.listDeletedProjects()
        .then((data) => {
            deletedProjects.value = data ?? [];
        })
        .catch(() => {
            deletedProjects.value = [];
        });
}

function formatDeletedAt(deletedAt) {
    return deletedAt ? new Date(deletedAt).toLocaleDateString() : '';
}

function userDataSummary(project) {
    const parts = [];
    if (project.usersWithVariables > 0) {
        parts.push(`${project.usersWithVariables} user${project.usersWithVariables === 1 ? '' : 's'} with variables`);
    }
    if (project.dialogueLogs > 0) {
        parts.push(`${project.dialogueLogs} dialogue log${project.dialogueLogs === 1 ? '' : 's'}`);
    }
    return parts.length ? parts.join(', ') : 'no user data left';
}

function openPurgeConfirm(project) {
    purgeConfirm.value = project;
    purgeConfirmInput.value = '';
    purgeError.value = '';
}

function closePurgeConfirm() {
    if (purging.value) return;
    purgeConfirm.value = null;
}

function confirmPurge() {
    if (purgeConfirmInput.value.trim() !== purgeConfirm.value.slug) return;
    purging.value = true;
    purgeError.value = '';
    client.purgeDeletedProject(purgeConfirm.value.slug)
        .then(() => {
            deletedProjects.value = deletedProjects.value.filter((p) => p.slug !== purgeConfirm.value.slug);
            purgeConfirm.value = null;
        })
        .catch(() => {
            purgeError.value = 'Failed to delete the project permanently. Please try again.';
        })
        .finally(() => {
            purging.value = false;
        });
}
</script>

<template>
    <div class="min-h-screen bg-background flex flex-col items-center">
        <PageHeader>
            <PageGreeting>Hey <span class="font-semibold text-orange-darker">{{ state.user?.name }}</span>, welcome to Dialogue Branch Studio.</PageGreeting>
            <template #menu><ManagementMenu active="projects" /></template>
        </PageHeader>

        <div class="w-full px-4 pb-12 max-w-3xl mx-auto">
            <!-- The page itself, framed in a white box like the participant view's dialogue -->
            <div class="bg-white rounded-2xl shadow-md p-4 sm:p-6" data-test="page-frame">
                <div class="flex flex-wrap items-center justify-between gap-3 mb-2">
                    <h2 class="font-title font-bold text-lg">Select a Project</h2>
                    <input
                        v-model="projectQuery"
                        type="search"
                        class="w-full sm:w-72 bg-white px-3 py-1.5 rounded-md border-[2px] border-solid border-gray-300 font-title text-sm focus:outline-none focus:border-orange-medium"
                        placeholder="Search by name, slug or description…"
                        data-test="project-search"
                    />
                </div>
                <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
                    <p class="flex-1 min-w-48 text-xs font-title text-grey-dark">
                        The Dialogue Branch projects on this Web Service. Open one to write, test and publish its dialogues.
                    </p>
                    <div class="flex items-center gap-2 shrink-0 ml-auto">
                        <button
                            type="button"
                            :class="['flex items-center gap-1.5 px-3 py-1.5 rounded text-white text-xs font-title font-semibold transition-colors', isAdmin ? 'bg-orange-darker hover:bg-orange-dark cursor-pointer' : 'bg-orange-medium cursor-not-allowed opacity-60']"
                            :disabled="!isAdmin"
                            :title="isAdmin ? '' : `You don't have permission to import projects`"
                            @click="isAdmin && (showImportModal = true)"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-file-import" />
                            Import Project
                        </button>
                        <button
                            type="button"
                            :class="['flex items-center gap-1.5 px-3 py-1.5 rounded text-white text-xs font-title font-semibold transition-colors', isAdmin ? 'bg-orange-darker hover:bg-orange-dark cursor-pointer' : 'bg-orange-medium cursor-not-allowed opacity-60']"
                            :disabled="!isAdmin"
                            :title="isAdmin ? '' : `You don't have permission to create projects`"
                            @click="isAdmin && (showCreateWizard = true)"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-plus" />
                            New Project
                        </button>
                    </div>
                </div>

                <CreateProjectWizardModal
                    v-if="showCreateWizard"
                    @close="showCreateWizard = false"
                    @created="onProjectCreated"
                />

                <ImportProjectModal
                    v-if="showImportModal"
                    @close="showImportModal = false"
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
                    <p class="mt-1">{{ isAdmin ? 'Use the New Project button above to create one.' : "You don't have permission to create projects — ask an administrator to set one up for you." }}</p>
                </div>

                <!-- Nothing matches the search -->
                <div v-else-if="visibleProjects.length === 0" class="text-center py-8 text-grey-dark font-title text-sm" data-test="projects-no-match">
                    No projects match your search.
                </div>

                <!-- Project list -->
                <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <button
                        v-for="project in visibleProjects"
                        :key="project.slug"
                        type="button"
                        class="relative text-left bg-orange-light/30 rounded-xl px-5 py-4 hover:shadow-md hover:border-orange-darker border border-orange-light transition-all cursor-pointer group"
                        @click="selectProject(project)"
                    >
                        <span
                            v-if="isAdmin"
                            title="Delete project"
                            class="absolute bottom-2 right-2 text-orange-darker/60 opacity-0 group-hover:opacity-100 hover:!text-red-dark transition-opacity cursor-pointer p-1"
                            @click.stop="openDeleteConfirm(project)"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-trash" class="text-xs" />
                        </span>
                        <div class="flex items-start justify-between gap-2">
                            <div class="min-w-0">
                                <div class="font-title font-semibold text-sm group-hover:text-orange-darker transition-colors truncate">
                                    {{ project.latestVersion?.displayName ?? project.draftDisplayName ?? project.slug }}
                                </div>
                                <div class="font-mono text-xs text-grey-dark mt-0.5 truncate">{{ project.slug }}</div>
                            </div>
                            <FontAwesomeIcon icon="fa-solid fa-circle-arrow-right" class="text-orange-medium group-hover:text-orange-darker transition-colors shrink-0 mt-0.5" />
                        </div>
                        <div v-if="project.latestVersion?.description ?? project.draftDescription" class="mt-2">
                            <p
                                :ref="(el) => setDescriptionRef(project.slug, el)"
                                :class="['text-xs text-grey-dark', descriptionClampClass(project.slug)]"
                            >{{ project.latestVersion?.description ?? project.draftDescription }}</p>
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

                <!-- Deleted projects (#403): admins only, kept apart from the active projects -->
                <section v-if="isAdmin && deletedProjects.length" class="mt-8" data-test="deleted-projects">
                    <button
                        type="button"
                        class="flex items-center gap-2 font-title text-sm text-grey-dark hover:text-orange-darker cursor-pointer"
                        :aria-expanded="showDeleted"
                        data-test="deleted-projects-toggle"
                        @click="showDeleted = !showDeleted"
                    >
                        <FontAwesomeIcon :icon="showDeleted ? 'fa-solid fa-caret-down' : 'fa-solid fa-caret-right'" class="w-3" />
                        Deleted projects ({{ deletedProjects.length }})
                    </button>
                    <div v-if="showDeleted" class="mt-2 flex flex-col gap-2">
                        <p class="text-xs font-title text-grey-dark">
                            Their dialogues are gone, but their users' data was kept. Deleting one permanently removes that data too.
                        </p>
                        <div
                            v-for="project in deletedProjects"
                            :key="project.slug"
                            class="flex items-center gap-3 rounded-xl px-4 py-3 border border-dashed border-grey-light bg-grey-lighter/50"
                            data-test="deleted-project"
                        >
                            <div class="flex-1 min-w-0">
                                <div class="font-title text-sm text-grey-dark truncate">{{ project.displayName ?? project.slug }}</div>
                                <div class="font-mono text-xs text-grey-dark truncate">{{ project.slug }}</div>
                                <div class="text-xs text-grey-dark mt-0.5">
                                    Deleted {{ formatDeletedAt(project.deletedAt) }} · {{ userDataSummary(project) }}
                                </div>
                            </div>
                            <button
                                type="button"
                                class="shrink-0 px-3 py-1.5 rounded font-title text-xs text-red-dark border border-red-dark/40 hover:bg-red-dark/10 cursor-pointer"
                                data-test="purge-project"
                                @click="openPurgeConfirm(project)"
                            >Delete permanently…</button>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </div>

    <Teleport to="body">
        <div v-if="purgeConfirm" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" data-test="purge-confirm" @click.self="closePurgeConfirm">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                <div class="flex items-center gap-2 px-5 py-4 bg-red-dark rounded-t-xl text-white font-title font-bold">
                    <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" />
                    Delete Permanently
                </div>
                <div class="px-5 py-4 flex flex-col gap-3">
                    <p class="text-sm font-title">
                        You are about to permanently delete the deleted project
                        <span class="font-bold text-orange-darker">{{ purgeConfirm.displayName ?? purgeConfirm.slug }}</span>
                        (<span class="font-mono">{{ purgeConfirm.slug }}</span>).
                    </p>
                    <div class="flex items-start gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-lg px-3 py-2.5 text-xs font-title">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                        <span>
                            This deletes <strong>all of its users' variables, variable history and dialogue logs</strong>
                            ({{ userDataSummary(purgeConfirm) }}), and the project itself.
                            <strong>This action cannot be undone.</strong>
                        </span>
                    </div>
                    <div>
                        <label class="block font-title text-xs text-grey-dark mb-1">
                            Type <span class="font-mono font-semibold text-orange-darker">{{ purgeConfirm.slug }}</span> to confirm:
                        </label>
                        <input
                            v-model="purgeConfirmInput"
                            type="text"
                            class="w-full font-mono bg-white p-2 rounded-md border-[2px] border-solid border-gray-300 text-sm focus:outline-none focus:border-red-dark"
                            :placeholder="purgeConfirm.slug"
                            data-test="purge-confirm-input"
                            @keyup.enter="confirmPurge"
                        />
                    </div>
                    <div v-if="purgeError" class="text-xs text-red-dark font-title">{{ purgeError }}</div>
                </div>
                <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button
                        type="button"
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer"
                        :disabled="purging"
                        @click="closePurgeConfirm"
                    >Cancel</button>
                    <button
                        type="button"
                        :class="['px-4 py-2 rounded font-title text-sm text-white flex items-center gap-2', (purging || purgeConfirmInput.trim() !== purgeConfirm.slug) ? 'bg-red-dark/50 cursor-not-allowed' : 'bg-red-dark hover:bg-red-900 cursor-pointer']"
                        :disabled="purging || purgeConfirmInput.trim() !== purgeConfirm.slug"
                        data-test="purge-confirm-button"
                        @click="confirmPurge"
                    >
                        <FontAwesomeIcon v-if="purging" icon="fa-solid fa-circle-notch" class="animate-spin" />
                        Delete Permanently
                    </button>
                </div>
            </div>
        </div>

        <div v-if="deleteConfirm" class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="closeDeleteConfirm">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                <div class="flex items-center gap-2 px-5 py-4 bg-red-dark rounded-t-xl text-white font-title font-bold">
                    <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" />
                    Delete Project
                </div>
                <div class="px-5 py-4 flex flex-col gap-3">
                    <p class="text-sm font-title">
                        You are about to delete the Dialogue Branch project
                        <span class="font-bold text-orange-darker">{{ deleteConfirm.displayName }}</span>
                        (<span class="font-mono">{{ deleteConfirm.slug }}</span>).
                    </p>
                    <div class="flex items-start gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-lg px-3 py-2.5 text-xs font-title">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                        <span>
                            This will permanently delete all of this project's dialogues (draft
                            and published), translations and version history. <strong>This action
                            cannot be undone.</strong>
                        </span>
                    </div>
                    <p class="text-xs font-title text-grey-dark" data-test="delete-keeps-user-data">
                        Users' data in this project (their variables and dialogue logs) is kept, unless you
                        choose to delete it too. The project's name becomes free for a new project.
                    </p>
                    <div class="flex flex-col gap-1.5 text-xs font-title text-red-dark">
                        <label class="flex items-start gap-2 cursor-pointer">
                            <input v-model="deleteVariables" type="checkbox" class="mt-0.5" data-test="delete-variables" />
                            <span>Also delete <strong>all users' variables</strong> (and their history) in this project</span>
                        </label>
                        <label class="flex items-start gap-2 cursor-pointer">
                            <input v-model="deleteLogs" type="checkbox" class="mt-0.5" data-test="delete-logs" />
                            <span>Also delete <strong>all users' dialogue logs</strong> of this project</span>
                        </label>
                    </div>
                    <div>
                        <label class="block font-title text-xs text-grey-dark mb-1">
                            Type <span class="font-mono font-semibold text-orange-darker">{{ deleteConfirm.slug }}</span> to confirm:
                        </label>
                        <input
                            v-model="deleteConfirmInput"
                            type="text"
                            class="w-full font-mono bg-white p-2 rounded-md border-[2px] border-solid border-gray-300 text-sm focus:outline-none focus:border-red-dark"
                            :placeholder="deleteConfirm.slug"
                            @keyup.enter="confirmDelete"
                        />
                    </div>
                    <div v-if="deleteError" class="text-xs text-red-dark font-title">{{ deleteError }}</div>
                </div>
                <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button
                        type="button"
                        class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer"
                        :disabled="deleting"
                        @click="closeDeleteConfirm"
                    >Cancel</button>
                    <button
                        type="button"
                        :class="['px-4 py-2 rounded font-title text-sm text-white flex items-center gap-2', (deleting || deleteConfirmInput.trim() !== deleteConfirm.slug) ? 'bg-red-dark/50 cursor-not-allowed' : 'bg-red-dark hover:bg-red-900 cursor-pointer']"
                        :disabled="deleting || deleteConfirmInput.trim() !== deleteConfirm.slug"
                        @click="confirmDelete"
                    >
                        <FontAwesomeIcon v-if="deleting" icon="fa-solid fa-circle-notch" class="animate-spin" />
                        Delete Project
                    </button>
                </div>
            </div>
        </div>
    </Teleport>
</template>
