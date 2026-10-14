<script setup>
import { ref, watch, inject, computed } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { useLatestRequest } from '@/composables/latest-request.js';
import { preambleToText, textToPreamble } from '@/authoring/DlbPreamble.js';
import PushButton from '../widgets/PushButton.vue';

// The dialogue's preamble (#346): the comment block at the start of its script, about the script
// as a whole. Shown at the top of the graph editor and edited as plain text; DlbPreamble.js adds
// and removes the `// ` prefixes.

const props = defineProps({
    dialogueName: { type: String, required: true },
});

const emit = defineEmits(['saved']);

const state = inject('state');
const client = useAuthoringClient();
const { next: nextRequest, isCurrent: isCurrentRequest } = useLatestRequest();

const preamble = ref('');
const collapsed = ref(false);
const editing = ref(false);
const draft = ref('');
const saving = ref(false);
const error = ref('');

const text = computed(() => preambleToText(preamble.value));

function load() {
    editing.value = false;
    error.value = '';
    const requestId = nextRequest();
    client.listDraftDialogues(state.value.selectedProject?.slug)
        .then((dialogues) => {
            if (!isCurrentRequest(requestId)) return;
            preamble.value = dialogues.find((d) => d.name === props.dialogueName)?.preamble ?? '';
        })
        .catch(() => {
            if (!isCurrentRequest(requestId)) return;
            preamble.value = '';
        });
}
watch(() => props.dialogueName, load, { immediate: true });

function startEditing() {
    draft.value = text.value;
    error.value = '';
    editing.value = true;
}

function save() {
    saving.value = true;
    error.value = '';
    client.updateDraftPreamble(state.value.selectedProject?.slug, props.dialogueName,
        textToPreamble(draft.value))
        .then((result) => {
            preamble.value = result.preamble;
            editing.value = false;
            collapsed.value = false;
            emit('saved');
        })
        .catch((err) => {
            error.value = describeError(err);
        })
        .finally(() => {
            saving.value = false;
        });
}

defineExpose({ reload: load });
</script>

<template>
    <div class="font-title text-xs max-w-md" data-test="preamble-bar">
        <!-- Editing -->
        <div v-if="editing" class="bg-white rounded-lg shadow-md p-3 flex flex-col gap-2 w-[28rem] max-w-full">
            <label class="font-bold text-orange-darker">Script comment</label>
            <textarea
                v-model="draft"
                data-test="preamble-text"
                rows="4"
                class="font-mono text-xs p-2 rounded-md border-2 border-gray-300 resize-y"
                placeholder="What this dialogue is for, notes for other authors..."
            ></textarea>
            <div v-if="error" class="text-red-600">{{ error }}</div>
            <div class="flex justify-end gap-2">
                <button type="button" class="px-3 py-1 rounded text-grey-dark hover:underline" :disabled="saving" @click="editing = false">Cancel</button>
                <PushButton text="Save" variant="green" :loading="saving" data-test="preamble-save" @click="save" />
            </div>
        </div>

        <!-- Collapsed: keep the canvas clear and expose the preamble on hover. -->
        <button
            v-else-if="preamble && collapsed"
            type="button"
            class="bg-white/95 rounded-lg shadow p-2 text-grey-dark hover:text-orange-darker cursor-pointer"
            :title="text"
            aria-label="Show script comment"
            data-test="preamble-collapsed"
            @click="collapsed = false"
        >
            <FontAwesomeIcon icon="fa-regular fa-comment" />
        </button>

        <!-- Showing -->
        <div v-else-if="preamble" class="bg-white/95 rounded-lg shadow p-2 pr-3 flex gap-2 items-start">
            <button type="button" class="text-grey-dark mt-0.5 cursor-pointer" title="Hide script comment" @click="collapsed = true">
                <FontAwesomeIcon icon="fa-solid fa-caret-down" />
            </button>
            <div class="min-w-0 flex-1">
                <div class="whitespace-pre-wrap text-grey-dark" data-test="preamble-shown">{{ text }}</div>
            </div>
            <button type="button" class="text-grey-dark hover:text-orange-darker cursor-pointer" title="Edit script comment" data-test="preamble-edit" @click="startEditing">
                <FontAwesomeIcon icon="fa-solid fa-pen" />
            </button>
        </div>

        <!-- None yet -->
        <button
            v-else
            type="button"
            class="bg-white/90 rounded-lg shadow px-2 py-1 text-grey-dark hover:text-orange-darker cursor-pointer inline-flex items-center gap-1.5"
            data-test="preamble-add"
            @click="startEditing"
        >
            <FontAwesomeIcon icon="fa-regular fa-comment" />
            Add script comment
        </button>
    </div>
</template>
