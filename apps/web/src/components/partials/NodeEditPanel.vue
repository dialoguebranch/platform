<script setup>
import { ref, watch, inject } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { showError } from '@/composables/error-toast.js';
import { parseHeaderTags, serializeHeaderTags } from '@/dlb-lib/util/DlbHeaderTags.js';
import { COLOR_PALETTE, colorForId } from '@/composables/node-colors.js';
import TextInput from '../widgets/TextInput.vue';
import PushButton from '../widgets/PushButton.vue';

// Matches DialogueBranchParser.NODE_NAME_REGEX in packages/core.
const NODE_NAME_REGEX = /^[A-Za-z0-9_-]+$/;

const props = defineProps({
    node: { type: Object, required: true }, // raw draft node: { title, header, body }
    dialogueName: { type: String, required: true },
});

const emit = defineEmits(['close', 'saved', 'deleted', 'renamed']);

const state = inject('state');
const client = useClient();

const title = ref('');
const speaker = ref('');
const colorId = ref('0');
const body = ref('');
const saving = ref(false);
const error = ref('');

// Re-populate the form whenever a different node is opened (or this one is reloaded after a
// rename elsewhere touched it) — the panel always reflects props.node, not local-only state.
// A pending save's `.then()` still fires `emit('saved'|'renamed', ...)` after the panel closes if
// the user closes it mid-save — guard the close paths (header X, Cancel, backdrop click) so the
// user can't dismiss the panel while that request is still in flight.
function closePanel() {
    if (saving.value) return;
    emit('close');
}

function loadFromNode() {
    const tags = parseHeaderTags(props.node.header);
    title.value = props.node.title;
    speaker.value = tags.get('speaker') ?? '';
    colorId.value = tags.get('colorId') ?? '0';
    body.value = props.node.body ?? '';
    error.value = '';
}
watch(() => props.node, loadFromNode, { immediate: true });

// Saving is a single action regardless of whether the title changed: if it did, we transparently
// check for references first and — only if any are found — ask whether to update them, rather
// than requiring a separate manual "check" step before the user is even allowed to type a new
// title.
function onSave() {
    const newTitle = title.value.trim();
    if (!newTitle) {
        error.value = 'Title is required.';
        return;
    }
    if (!NODE_NAME_REGEX.test(newTitle)) {
        error.value = 'Title may only contain letters, digits, underscores, and hyphens.';
        return;
    }
    error.value = '';
    saving.value = true;

    if (newTitle === props.node.title) {
        saveContent(props.node.header, props.node.title, false);
        return;
    }

    client.findNodeReferences(state.value.selectedProject?.slug, props.dialogueName, props.node.title)
        .then((references) => {
            if (references.length > 0) {
                saving.value = false;
                renamePrompt.value = { newTitle, references };
            } else {
                performRename(newTitle, false);
            }
        })
        .catch((err) => {
            saving.value = false;
            error.value = describeError(err);
        });
}

function saveContent(baseHeader, nodeTitle, wasRenamed) {
    // Parse whichever header is currently authoritative (the renamed node's, if a rename just
    // happened, since only its title: line differs from props.node.header) and only overwrite
    // the two fields this panel edits, preserving every other tag untouched.
    const tags = parseHeaderTags(baseHeader);
    tags.set('speaker', speaker.value);
    tags.set('colorId', String(colorId.value));
    const header = serializeHeaderTags(tags);

    client.updateDraftNode(state.value.selectedProject?.slug, props.dialogueName, nodeTitle, header, body.value)
        .then((updated) => {
            emit(wasRenamed ? 'renamed' : 'saved', updated);
        })
        .catch((err) => {
            error.value = describeError(err);
        })
        .finally(() => {
            saving.value = false;
        });
}

// ---- Rename reference confirmation (only shown when references were actually found) ----

const renamePrompt = ref(null); // { newTitle, references: [...] }

function performRename(newTitle, updateReferences) {
    renamePrompt.value = null;
    saving.value = true;
    client.renameDraftNode(state.value.selectedProject?.slug, props.dialogueName, props.node.title,
        newTitle, updateReferences)
        .then((result) => {
            saveContent(result.node.header, newTitle, true);
        })
        .catch((err) => {
            saving.value = false;
            error.value = describeError(err);
        });
}

// ---- Delete (warns about references first, but never blocks) ----

const deletePrompt = ref(null); // { references: [...] | null }
const checkingDelete = ref(false);
const deleting = ref(false);

function onDeleteClick() {
    checkingDelete.value = true;
    error.value = '';
    client.findNodeReferences(state.value.selectedProject?.slug, props.dialogueName, props.node.title)
        .then((references) => {
            deletePrompt.value = { references };
        })
        .catch(() => {
            // Reference lookup itself failed — still let the user delete, just without a preview.
            deletePrompt.value = { references: null };
        })
        .finally(() => {
            checkingDelete.value = false;
        });
}

function confirmDelete() {
    deleting.value = true;
    client.deleteDraftNode(state.value.selectedProject?.slug, props.dialogueName, props.node.title)
        .then(() => {
            deletePrompt.value = null;
            emit('deleted', props.node.title);
        })
        .catch((err) => {
            showError(describeError(err));
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
    <Teleport to="body">
        <div class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50" @click.self="closePanel">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 flex flex-col max-h-[85vh]">

                <!-- Header -->
                <div class="flex items-center justify-between px-5 py-4 bg-orange-darker rounded-t-xl shrink-0">
                    <div class="flex items-center gap-2 text-white font-title font-bold">
                        <FontAwesomeIcon icon="fa-solid fa-diagram-project" class="shrink-0" />
                        Edit Node
                    </div>
                    <button
                        class="text-orange-light hover:text-white shrink-0 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:text-orange-light"
                        :class="{ 'cursor-pointer': !saving }"
                        :disabled="saving"
                        @click="closePanel"
                    >
                        <FontAwesomeIcon icon="fa-solid fa-xmark" />
                    </button>
                </div>

                <!-- Content -->
                <div class="overflow-y-auto px-5 py-4 flex flex-col gap-4">
                    <div v-if="error" class="flex items-center gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-xl px-4 py-3 text-sm font-title">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0" />
                        {{ error }}
                    </div>

                    <!-- Title + Speaker -->
                    <div class="flex gap-3">
                        <div class="flex-1 min-w-0">
                            <label class="block font-title font-bold text-sm text-orange-darker mb-1">Title</label>
                            <TextInput class="w-full" v-model="title" placeholder="NodeTitle" />
                        </div>
                        <div class="flex-1 min-w-0">
                            <label class="block font-title font-bold text-sm text-orange-darker mb-1">Speaker</label>
                            <TextInput class="w-full" v-model="speaker" placeholder="Speaker name..." />
                        </div>
                    </div>

                    <!-- Color -->
                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Color</label>
                        <div class="flex items-center gap-1.5">
                            <button
                                v-for="(color, index) in COLOR_PALETTE"
                                :key="index"
                                type="button"
                                :title="`colorId: ${index}`"
                                class="w-6 h-6 rounded-full cursor-pointer transition-transform"
                                :class="String(colorId) === String(index) ? 'ring-2 ring-offset-2 ring-orange-darker scale-110' : ''"
                                :style="{ backgroundColor: color }"
                                @click="colorId = String(index)"
                            ></button>
                        </div>
                    </div>

                    <!-- Body -->
                    <div>
                        <label class="block font-title font-bold text-sm text-orange-darker mb-1">Body</label>
                        <textarea
                            v-model="body"
                            rows="10"
                            placeholder="Node body script..."
                            class="w-full px-3 py-2 border border-grey-light rounded-lg text-xs font-mono focus:outline-none focus:border-orange-dark resize-y"
                        ></textarea>
                    </div>
                </div>

                <!-- Footer -->
                <div class="flex items-center justify-between gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl shrink-0">
                    <button
                        type="button"
                        class="px-3 py-2 rounded font-title text-sm text-red-dark border border-red-dark/40 hover:bg-red-dark/10 cursor-pointer flex items-center gap-2"
                        :disabled="checkingDelete"
                        @click="onDeleteClick"
                    >
                        <FontAwesomeIcon :icon="checkingDelete ? 'fa-solid fa-circle-notch' : 'fa-solid fa-trash'" :class="{ 'animate-spin': checkingDelete }" />
                        Delete
                    </button>
                    <div class="flex gap-3">
                        <button
                            type="button"
                            class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                            :class="{ 'cursor-pointer': !saving }"
                            :disabled="saving"
                            @click="closePanel"
                        >Cancel</button>
                        <PushButton text="Save" variant="green" :loading="saving" @click="onSave" />
                    </div>
                </div>
            </div>
        </div>

        <!-- Delete confirmation -->
        <div v-if="deletePrompt" class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                <div class="flex items-center gap-2 px-5 py-4 bg-red-dark rounded-t-xl text-white font-title font-bold">
                    <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" />
                    Delete Node
                </div>
                <div class="px-5 py-4 flex flex-col gap-3 text-sm font-title">
                    <p>You are about to permanently delete the node <span class="font-bold text-orange-darker">{{ node.title }}</span>.</p>
                    <div v-if="deletePrompt.references === null" class="text-xs text-grey-dark italic">
                        Could not check for references to this node — proceed with caution.
                    </div>
                    <div v-else-if="deletePrompt.references.length > 0" class="flex items-start gap-2 border border-red-dark/40 bg-red-dark/10 text-red-dark rounded-lg px-3 py-2.5 text-xs">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                        <span>
                            {{ deletePrompt.references.length }} repl{{ deletePrompt.references.length === 1 ? 'y' : 'ies' }}
                            across {{ referenceDialogueCount(deletePrompt.references) }} dialogue{{ referenceDialogueCount(deletePrompt.references) === 1 ? '' : 's' }}
                            still link{{ deletePrompt.references.length === 1 ? 's' : '' }} to this node. Deleting it will leave those links dangling.
                        </span>
                    </div>
                    <div v-else class="text-xs text-grey-dark italic">No other node references this one.</div>
                </div>
                <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button type="button" class="px-4 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" :disabled="deleting" @click="deletePrompt = null">Cancel</button>
                    <button
                        type="button"
                        class="px-4 py-2 rounded font-title text-sm text-white flex items-center gap-2 bg-red-dark hover:bg-red-900 cursor-pointer"
                        :disabled="deleting"
                        @click="confirmDelete"
                    >
                        <FontAwesomeIcon v-if="deleting" icon="fa-solid fa-circle-notch" class="animate-spin" />
                        Delete Permanently
                    </button>
                </div>
            </div>
        </div>

        <!-- Rename reference confirmation (triggered automatically from Save when the title
             changed and other nodes already link to the old one) -->
        <div v-if="renamePrompt" class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50">
            <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 flex flex-col">
                <div class="flex items-center gap-2 px-5 py-4 bg-orange-darker rounded-t-xl text-white font-title font-bold">
                    <FontAwesomeIcon icon="fa-solid fa-circle-info" />
                    Rename Node
                </div>
                <div class="px-5 py-4 flex flex-col gap-3 text-sm font-title">
                    <p>
                        Renaming <span class="font-bold text-orange-darker">{{ node.title }}</span> to
                        <span class="font-bold text-orange-darker">{{ renamePrompt.newTitle }}</span>.
                    </p>
                    <div class="flex items-start gap-2 border border-orange-medium/40 bg-orange-light/10 text-orange-darker rounded-lg px-3 py-2.5 text-xs">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="shrink-0 mt-0.5" />
                        <span>
                            {{ renamePrompt.references.length }} repl{{ renamePrompt.references.length === 1 ? 'y' : 'ies' }}
                            across {{ referenceDialogueCount(renamePrompt.references) }} dialogue{{ referenceDialogueCount(renamePrompt.references) === 1 ? '' : 's' }}
                            link{{ renamePrompt.references.length === 1 ? 's' : '' }} to this node. Update them to point to the new name?
                        </span>
                    </div>
                </div>
                <div class="flex items-center justify-end gap-3 px-5 py-4 border-t border-grey-light bg-grey-lighter rounded-b-xl">
                    <button type="button" class="px-3 py-2 rounded font-title text-sm text-grey-dark border border-grey-light hover:bg-grey-light cursor-pointer" :disabled="saving" @click="renamePrompt = null">Cancel</button>
                    <button
                        type="button"
                        class="px-3 py-2 rounded font-title text-sm text-orange-darker border border-orange-medium hover:bg-orange-light/20 cursor-pointer"
                        :disabled="saving"
                        @click="performRename(renamePrompt.newTitle, false)"
                    >Leave them as-is</button>
                    <button
                        type="button"
                        class="px-3 py-2 rounded font-title text-sm text-white bg-orange-darker hover:bg-orange-dark cursor-pointer flex items-center gap-2"
                        :disabled="saving"
                        @click="performRename(renamePrompt.newTitle, true)"
                    >
                        <FontAwesomeIcon v-if="saving" icon="fa-solid fa-circle-notch" class="animate-spin" />
                        Update all references
                    </button>
                </div>
            </div>
        </div>
    </Teleport>
</template>
