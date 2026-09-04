<script setup>
// Renders a reply whose statement contains one or more <<input>> commands: the reply text with a
// DialogueInput widget in place of each input segment, plus a submit button that is enabled only
// once every input is valid. On submit it emits the collected { variableName: value } map.
import { computed, reactive, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { sanitizeHtml } from '@/composables/sanitize-html.js';
import DialogueInput from '../widgets/DialogueInput.vue';
import DialogueActionSegment from '../widgets/DialogueActionSegment.vue';

const props = defineProps({
    // A dlb-lib BasicReply whose statement has at least one INPUT segment.
    reply: { type: Object, required: true },
    disabled: { type: Boolean, default: false },
    // Tailwind classes for the submit button, so the caller controls balloon vs text styling.
    buttonClass: { type: String, default: '' },
});

const emit = defineEmits(['submit']);

const segments = computed(() => props.reply.statement?.segments ?? []);

// Collected values and per-input validity, keyed by the input segment's position in the reply.
const valuesByIndex = reactive({});
const validByIndex = reactive({});

segments.value.forEach((segment, index) => {
    if (segment.type === 'INPUT') validByIndex[index] = false;
});

const allValid = computed(() =>
    Object.keys(validByIndex).length === 0 ||
    Object.values(validByIndex).every(Boolean),
);

const submitted = ref(false);

function onValue(index, value) {
    valuesByIndex[index] = value;
}

function onValid(index, valid) {
    validByIndex[index] = valid;
}

function onSubmit() {
    if (props.disabled || submitted.value || !allValid.value) return;
    submitted.value = true;
    const merged = Object.assign({}, ...Object.values(valuesByIndex));
    emit('submit', merged);
}
</script>

<template>
    <div class="font-title text-left leading-relaxed w-full">
        <span v-if="reply.endsDialogue" class="mr-1 opacity-75">
            <FontAwesomeIcon icon="fa-solid fa-xmark" title="This reply ends the dialogue" />
        </span>
        <template v-for="(segment, index) in segments" :key="index">
            <span v-if="segment.type === 'TEXT'" v-html="sanitizeHtml(segment.text)"></span>
            <DialogueInput
                v-else-if="segment.type === 'INPUT'"
                :segment="segment"
                :disabled="disabled || submitted"
                @update:value="(v) => onValue(index, v)"
                @update:valid="(v) => onValid(index, v)"
            />
            <DialogueActionSegment
                v-else-if="segment.type === 'ACTION' && segment.action"
                :action="segment.action"
            />
        </template>
        <button
            type="button"
            :class="buttonClass"
            :disabled="disabled || submitted || !allValid"
            @click="onSubmit"
        >
            <FontAwesomeIcon icon="fa-solid fa-paper-plane" class="mr-1.5" />Send
        </button>
    </div>
</template>
