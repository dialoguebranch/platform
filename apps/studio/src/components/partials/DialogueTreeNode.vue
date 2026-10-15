<script setup>
import { computed, inject, nextTick, ref, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { downloadBlob } from '@/composables/download-blob.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { DLB_APP_MODE_DRAFT } from '@/StudioClientState.js';
import { sortTreeLevel } from '@/composables/dialogue-tree.js';
import ContextMenu from '../widgets/ContextMenu.vue';

const props = defineProps({
    name: String,
    node: Object,
    path: String,
    openFolders: Object,
    sortMode: String,
    // While a filter is active every folder shows expanded so matches are visible, without
    // touching the user's saved openFolders state.
    forceExpanded: Boolean,
    depth: {
        type: Number,
        default: 0,
    },
});

const emit = defineEmits(['toggleFolder', 'openDialogue', 'dialoguesChanged', 'dialoguesReplaced', 'dialogueDeleted']);

const state = inject('state');
const client = useAuthoringClient();

const isDraftMode = computed(() => state.value.mode === DLB_APP_MODE_DRAFT);

function openDialogue() {
    emit('openDialogue', props.node._file);
}

const isFile = computed(() => !!props.node._file);
const isOpen = computed(() => props.forceExpanded || !!props.openFolders[props.path]);
const children = computed(() => {
    if (isFile.value) return [];
    return sortTreeLevel(Object.entries(props.node._children), props.sortMode);
});

// ---- Export as .dlb (#341) ----
// Downloads the dialogue's current draft as a script. The file is named after the dialogue's last
// name segment (not its folder), so importing it back into that folder gives the same name.

const exporting = ref(false);

function exportDialogue() {
    if (exporting.value) return;
    exporting.value = true;
    dismissError();
    const fileName = props.node._file.split('/').pop() + '.dlb';
    client.exportDialogue(state.value.selectedProject?.slug, props.node._file)
        .then((blob) => downloadBlob(blob, fileName))
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            exporting.value = false;
        });
}

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

const renameField = useTemplateRef('rename-input');

function startRename() {
    renameInput.value = props.name;
    renaming.value = true;
    // Opened from the row's menu, which hands the focus back to where it came from as it closes;
    // the field takes it over once rendered.
    nextTick(() => renameField.value?.focus());
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

// ---- Discard changes (#363) ----
// Makes a changed dialogue identical to its published version again. A dialogue renamed since it
// was published gets its published name back; links in other dialogues that were updated for a
// rename (of the dialogue or its nodes) then no longer match, which the confirmation warns about.

const discardPrompt = ref(false);
const discarding = ref(false);

function discardChanges() {
    discardPrompt.value = false;
    discarding.value = true;
    dismissError();
    client.discardDraftDialogueChanges(state.value.selectedProject?.slug, props.node._file)
        .then((result) => {
            emit('dialoguesReplaced', [...new Set([result.discardedName, result.dialogueName])]);
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            discarding.value = false;
        });
}

// ---- Row menu (#389) ----
// A dialogue row shows only its launch button (▶); its other actions are in a context menu,
// opened by right-clicking the row or with the "⋯" button. A dialogue pending deletion has no
// menu: Restore is its one visible button. In Live Mode there's nothing to offer besides ▶.

const hasMenu = computed(() => isDraftMode.value && !props.node._isDeleted);
const menuButton = useTemplateRef('menu-button');
const menu = ref(null); // { x, y } or { anchor }, or null when closed

const menuItems = computed(() => {
    const items = [
        { id: 'open', label: 'Open', icon: 'fa-solid fa-play', test: 'menu-open' },
        { id: 'rename', label: 'Rename…', icon: 'fa-solid fa-pen', test: 'menu-rename' },
        { id: 'export', label: 'Export as .dlb', icon: 'fa-solid fa-file-export', test: 'menu-export' },
    ];
    if (props.node._isChanged && !props.node._isNew) {
        items.push({ id: 'discard', label: 'Discard changes…', icon: 'fa-solid fa-rotate-left', test: 'menu-discard' });
    }
    items.push({ separator: true });
    items.push({ id: 'delete', label: 'Delete…', icon: 'fa-solid fa-trash', danger: true, test: 'menu-delete' });
    return items;
});

function openMenuAtPointer(event) {
    if (!hasMenu.value) return;
    event.preventDefault();
    menu.value = { x: event.clientX, y: event.clientY };
}

function openMenuFromButton() {
    menu.value = { anchor: menuButton.value };
}

function onMenuSelect(item) {
    switch (item.id) {
    case 'open': openDialogue(); break;
    case 'rename': startRename(); break;
    case 'export': exportDialogue(); break;
    case 'discard': discardPrompt.value = true; break;
    case 'delete': askDelete(); break;
    }
}

// ---- Delete (#390) ----
// Marks the dialogue pending deletion, restorable until the next publish. The confirmation says
// how many replies in other dialogues link to it, since those links break.

const deletePrompt = ref(null); // { references } while confirming, or null
const deleting = ref(false);

function askDelete() {
    dismissError();
    client.findDialogueReferences(state.value.selectedProject?.slug, props.node._file)
        .then((references) => {
            deletePrompt.value = {
                references: references.filter((r) => r.dialogueName !== props.node._file),
            };
        })
        .catch((error) => {
            showError(describeError(error));
        });
}

function deleteDialogue() {
    deletePrompt.value = null;
    deleting.value = true;
    dismissError();
    client.deleteDraftDialogue(state.value.selectedProject?.slug, props.node._file)
        .then(() => {
            emit('dialogueDeleted', props.node._file);
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            deleting.value = false;
        });
}

function referenceDialogueCount(references) {
    return new Set((references ?? []).map((r) => r.dialogueName)).size;
}
</script>

<template>
    <div class="flex flex-col gap-0.5">
        <!-- Folder -->
        <div
            v-if="!isFile"
            class="flex items-center gap-1 font-title font-black text-xs bg-grey-lighter px-1 py-0.5 text-gray-600 select-none"
            :class="forceExpanded ? 'cursor-default' : 'cursor-pointer hover:text-gray-800'"
            :style="{ marginLeft: (depth * 12) + 'px' }"
            @click="!forceExpanded && $emit('toggleFolder', path)"
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
                :sortMode="sortMode"
                :forceExpanded="forceExpanded"
                :depth="depth + 1"
                @toggleFolder="$emit('toggleFolder', $event)"
                @openDialogue="(name) => $emit('openDialogue', name)"
                @dialoguesChanged="$emit('dialoguesChanged')"
                @dialoguesReplaced="$emit('dialoguesReplaced', $event)"
                @dialogueDeleted="$emit('dialogueDeleted', $event)"
            />
        </template>

        <!-- File: rename input replaces the normal row while active -->
        <div
            v-if="isFile && renaming"
            class="flex items-center gap-1 bg-grey-lighter px-1 py-0.5"
            :style="{ marginLeft: (depth * 12) + 'px' }"
        >
            <input
                ref="rename-input"
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
            class="group flex items-center gap-1.5 font-title font-black text-xs bg-grey-lighter px-1 py-0.5"
            :style="{ marginLeft: (depth * 12) + 'px' }"
            data-test="dialogue-row"
            @contextmenu="openMenuAtPointer"
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
            >Changed</span>

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
                    v-if="hasMenu"
                    ref="menu-button"
                    type="button"
                    title="More actions"
                    aria-haspopup="menu"
                    :aria-expanded="!!menu"
                    class="shrink-0 cursor-pointer text-grey-dark hover:text-orange-dark opacity-0 group-hover:opacity-100 focus:opacity-100 [@media(hover:none)]:opacity-100"
                    :class="{ 'opacity-100': menu }"
                    data-test="row-menu-button"
                    @click="openMenuFromButton"
                >
                    <FontAwesomeIcon :icon="exporting || discarding || deleting ? 'fa-solid fa-circle-notch' : 'fa-solid fa-ellipsis'" :class="['w-3', { 'animate-spin': exporting || discarding || deleting }]" />
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

        <ContextMenu
            v-if="menu"
            :items="menuItems"
            :x="menu.x ?? null"
            :y="menu.y ?? null"
            :anchor="menu.anchor ?? null"
            @select="onMenuSelect"
            @close="menu = null"
        />

        <!-- Delete confirmation -->
        <Teleport to="body">
            <div v-if="deletePrompt" class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50" data-test="delete-prompt">
                <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                    <div class="flex items-center gap-2 px-5 py-4 bg-orange-darker rounded-t-xl text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-trash" />
                        Delete Dialogue
                    </div>
                    <div class="px-5 py-4 flex flex-col gap-3 text-sm font-title">
                        <p>
                            Delete <span class="font-bold text-orange-darker">{{ node._file }}</span>?
                            It is marked for deletion and can be restored until the project is published next, which
                            removes it for good<template v-if="!node._isNew">; until then the published version stays live</template>.
                            Its open tabs are closed.
                        </p>
                        <div
                            v-if="deletePrompt.references.length"
                            class="flex items-start gap-2 border border-orange-medium/40 bg-orange-light/10 text-orange-darker rounded-lg px-3 py-2.5 text-xs"
                            data-test="delete-references"
                        >
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                            <span>
                                {{ deletePrompt.references.length }} repl{{ deletePrompt.references.length === 1 ? 'y' : 'ies' }}
                                in {{ referenceDialogueCount(deletePrompt.references) }} other dialogue{{ referenceDialogueCount(deletePrompt.references) === 1 ? '' : 's' }}
                                link{{ deletePrompt.references.length === 1 ? 's' : '' }} to it; those links will break.
                            </span>
                        </div>
                    </div>
                    <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                        <button type="button" class="px-3 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" @click="deletePrompt = null">Cancel</button>
                        <button
                            type="button"
                            class="px-3 py-2 rounded font-title text-sm text-white bg-red-dark hover:opacity-90 cursor-pointer"
                            data-test="delete-confirm"
                            @click="deleteDialogue"
                        >Delete</button>
                    </div>
                </div>
            </div>
        </Teleport>

        <!-- Discard changes confirmation -->
        <Teleport to="body">
            <div v-if="discardPrompt" class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50" data-test="discard-prompt">
                <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                    <div class="flex items-center gap-2 px-5 py-4 bg-orange-darker rounded-t-xl text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-rotate-left" />
                        Discard Changes
                    </div>
                    <div class="px-5 py-4 flex flex-col gap-3 text-sm font-title">
                        <p>
                            Discard the draft changes of <span class="font-bold text-orange-darker">{{ node._file }}</span>?
                            It becomes identical to its published version again: its nodes and their layout, its script
                            comment and its translations. This can't be undone.
                        </p>
                        <p v-if="node._previousPublishedName" data-test="discard-renamed">
                            It gets its published name back: <span class="font-bold text-orange-darker">{{ node._previousPublishedName }}</span>.
                        </p>
                        <div class="flex items-start gap-2 border border-orange-medium/40 bg-orange-light/10 text-orange-darker rounded-lg px-3 py-2.5 text-xs">
                            <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                            <span>
                                If you renamed this dialogue or any of its nodes and updated the references in other
                                dialogues, those links will no longer match.
                            </span>
                        </div>
                    </div>
                    <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                        <button type="button" class="px-3 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" @click="discardPrompt = false">Cancel</button>
                        <button
                            type="button"
                            class="px-3 py-2 rounded font-title text-sm text-white bg-orange-darker hover:bg-orange-dark cursor-pointer"
                            data-test="discard-confirm"
                            @click="discardChanges"
                        >Discard changes</button>
                    </div>
                </div>
            </div>
        </Teleport>

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
