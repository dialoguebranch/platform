<script setup>
import { computed, inject, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';

const props = defineProps({
    name: String,
    node: Object,
    path: String,
    openFolders: Object,
    depth: {
        type: Number,
        default: 0,
    },
});

const emit = defineEmits(['toggleFolder', 'openDialogue', 'dialoguesChanged']);

const state = inject('state');
const client = useClient();

function openDialogue() {
    emit('openDialogue', props.node._file);
}

const isFile = computed(() => !!props.node._file);
const isOpen = computed(() => !!props.openFolders[props.path]);
const children = computed(() => {
    if (isFile.value) return [];
    return Object.entries(props.node._children).sort(([, a], [, b]) => {
        const aIsFolder = !a._file;
        const bIsFolder = !b._file;
        if (aIsFolder !== bIsFolder) return aIsFolder ? -1 : 1;
        return 0;
    });
});

// ---- Restore (revert a pending deletion) ----

const restoring = ref(false);

function restoreDialogue() {
    if (restoring.value) return;
    restoring.value = true;
    dismissError();
    client.restoreDraftDialogue(state.value.selectedProject?.slug, props.node._file)
        .then(() => {
            emit('dialoguesChanged');
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            restoring.value = false;
        });
}

// ---- Rename (leaf segment only — stays within the same folder) ----

const renaming = ref(false);
const renameInput = ref('');
const renamePrompt = ref(null); // { newFullName, references }

function startRename() {
    renameInput.value = props.name;
    renaming.value = true;
}

function cancelRename() {
    renaming.value = false;
}

function submitRename() {
    const newLeaf = renameInput.value.trim();
    renaming.value = false;
    if (!newLeaf || newLeaf === props.name) return;

    const parts = props.node._file.split('/');
    parts[parts.length - 1] = newLeaf;
    const newFullName = parts.join('/');

    dismissError();
    client.findDialogueReferences(state.value.selectedProject?.slug, props.node._file)
        .then((references) => {
            if (references.length > 0) {
                renamePrompt.value = { newFullName, references };
            } else {
                performRename(newFullName, false);
            }
        })
        .catch((error) => {
            showError(describeError(error));
        });
}

function performRename(newFullName, updateReferences) {
    renamePrompt.value = null;
    client.renameDraftDialogue(state.value.selectedProject?.slug, props.node._file, newFullName,
        updateReferences)
        .then(() => {
            emit('dialoguesChanged');
        })
        .catch((error) => {
            showError(describeError(error));
        });
}

function referenceDialogueCount(references) {
    return new Set((references ?? []).map((r) => r.dialogueName)).size;
}
</script>

<template>
    <div>
        <!-- Folder -->
        <div
            v-if="!isFile"
            class="cursor-pointer flex items-center gap-1 font-title font-black text-xs p-1 text-gray-600 hover:text-gray-800 select-none"
            :style="{ paddingLeft: (depth * 12 + 4) + 'px' }"
            @click="$emit('toggleFolder', path)"
        >
            <FontAwesomeIcon :icon="isOpen ? 'fa-solid fa-folder-open' : 'fa-solid fa-folder'" class="text-orange-dark w-3.5" />
            <span>{{ name }}</span>
        </div>

        <!-- Folder children -->
        <template v-if="!isFile && isOpen">
            <DialogueTreeNode
                v-for="[childName, childNode] in children"
                :key="childName"
                :name="childName"
                :node="childNode"
                :path="path + '/' + childName"
                :openFolders="openFolders"
                :depth="depth + 1"
                @toggleFolder="$emit('toggleFolder', $event)"
                @openDialogue="(name) => $emit('openDialogue', name)"
                @dialoguesChanged="$emit('dialoguesChanged')"
            />
        </template>

        <!-- File: rename input replaces the normal row while active -->
        <div
            v-if="isFile && renaming"
            class="flex items-center gap-1 p-1"
            :style="{ paddingLeft: (depth * 12 + 4) + 'px' }"
        >
            <input
                v-model="renameInput"
                type="text"
                autofocus
                class="flex-1 min-w-0 font-mono text-xs px-1.5 py-1 border border-grey-light rounded bg-white focus:outline-none focus:border-orange-dark"
                @keyup.enter="submitRename"
                @keyup.esc="cancelRename"
            />
            <button type="button" title="Save" class="shrink-0 text-icon-button hover:text-icon-button-hover cursor-pointer" @click="submitRename">
                <FontAwesomeIcon icon="fa-solid fa-check" />
            </button>
            <button type="button" title="Cancel" class="shrink-0 text-grey-dark hover:text-orange-dark cursor-pointer" @click="cancelRename">
                <FontAwesomeIcon icon="fa-solid fa-xmark" />
            </button>
        </div>

        <!-- File -->
        <div
            v-else-if="isFile"
            class="flex items-center gap-1.5 font-title font-black text-xs p-1"
            :style="{ paddingLeft: (depth * 12 + 4) + 'px' }"
        >
            <span
                class="flex-1 min-w-0 truncate"
                :class="node._isDeleted ? 'text-grey-dark line-through' : 'text-orange-darker cursor-pointer hover:text-orange-dark'"
                :title="node._isDeleted ? 'Pending deletion — restore to open' : 'Open dialogue'"
                @click="!node._isDeleted && openDialogue()"
            >{{ name }}</span>
            <span
                v-if="node._isDeleted"
                class="shrink-0 font-title text-[9px] font-semibold uppercase text-red-dark bg-red-dark/10 px-1 py-0.5 rounded"
            >Deleted</span>
            <span
                v-else-if="node._isNew"
                class="shrink-0 font-title text-[9px] font-semibold uppercase text-orange-dark bg-orange-light/20 px-1 py-0.5 rounded"
            >New</span>
            <span
                v-else-if="node._isChanged"
                class="shrink-0 font-title text-[9px] font-semibold uppercase text-grey-dark bg-grey-lighter px-1 py-0.5 rounded"
            >Draft</span>

            <template v-if="node._isDeleted">
                <button
                    type="button"
                    title="Restore (undo deletion)"
                    class="shrink-0 cursor-pointer text-orange-darker hover:text-orange-dark"
                    :disabled="restoring"
                    @click="restoreDialogue"
                >
                    <FontAwesomeIcon :icon="restoring ? 'fa-solid fa-circle-notch' : 'fa-solid fa-clock-rotate-left'" :class="{ 'animate-spin': restoring }" />
                </button>
            </template>
            <template v-else>
                <button
                    type="button"
                    title="Rename dialogue"
                    class="shrink-0 cursor-pointer text-grey-dark hover:text-orange-dark"
                    @click="startRename"
                >
                    <FontAwesomeIcon icon="fa-solid fa-pen" class="w-3" />
                </button>
                <button
                    type="button"
                    title="Open dialogue"
                    class="shrink-0 cursor-pointer text-orange-darker hover:text-orange-dark"
                    @click="openDialogue"
                >
                    <FontAwesomeIcon icon="fa-solid fa-play" class="w-3.5" />
                </button>
            </template>
        </div>

        <!-- Rename reference confirmation -->
        <Teleport to="body">
            <div v-if="renamePrompt" class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50">
                <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                    <div class="flex items-center gap-2 px-5 py-4 bg-orange-darker rounded-t-xl text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-circle-info" />
                        Rename Dialogue
                    </div>
                    <div class="px-5 py-4 flex flex-col gap-3 text-sm font-title">
                        <p>
                            Renaming <span class="font-bold text-orange-darker">{{ node._file }}</span> to
                            <span class="font-bold text-orange-darker">{{ renamePrompt.newFullName }}</span>.
                        </p>
                        <div class="flex items-start gap-2 border border-orange-medium/40 bg-orange-light/10 text-orange-darker rounded-lg px-3 py-2.5 text-xs">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                            <span>
                                {{ renamePrompt.references.length }} repl{{ renamePrompt.references.length === 1 ? 'y' : 'ies' }}
                                across {{ referenceDialogueCount(renamePrompt.references) }} dialogue{{ referenceDialogueCount(renamePrompt.references) === 1 ? '' : 's' }}
                                link{{ renamePrompt.references.length === 1 ? 's' : '' }} into this dialogue. Update them to point to the new name?
                            </span>
                        </div>
                    </div>
                    <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                        <button type="button" class="px-3 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" @click="renamePrompt = null">Cancel</button>
                        <button
                            type="button"
                            class="px-3 py-2 rounded font-title text-sm text-orange-darker border border-orange-medium hover:bg-orange-light/20 cursor-pointer"
                            @click="performRename(renamePrompt.newFullName, false)"
                        >Leave them as-is</button>
                        <button
                            type="button"
                            class="px-3 py-2 rounded font-title text-sm text-white bg-orange-darker hover:bg-orange-dark cursor-pointer"
                            @click="performRename(renamePrompt.newFullName, true)"
                        >Update all references</button>
                    </div>
                </div>
            </div>
        </Teleport>
    </div>
</template>
