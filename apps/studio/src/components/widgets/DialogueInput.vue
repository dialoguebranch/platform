<script setup>
// Renders the widget for one resolved <<input>> command (an INPUT statement segment) inside a
// reply, and reports the value(s) the user entered plus whether they are valid. Covers all six
// Dialogue Branch input types: text, longtext, email, numeric, time, set.
import { computed, ref, watch } from 'vue';

const props = defineProps({
    // A dlb-lib Segment with type === 'INPUT'.
    segment: { type: Object, required: true },
    disabled: { type: Boolean, default: false },
});

// emits { [variableName]: value } (for 'set', one entry per option) and the validity flag.
const emit = defineEmits(['update:value', 'update:valid']);

const params = computed(() => props.segment.parameters ?? {});
const type = computed(() => props.segment.inputType);

// --- local input state -------------------------------------------------------

// For 'set': a map of option variable name -> boolean. For everything else: a single string.
const setState = ref(
    Object.fromEntries((params.value.options ?? []).map((o) => [o.variableName, false])),
);
const textState = ref('');

// --- derived value + validity ----------------------------------------------

// `v-model` on `<input type="number">` casts to a number, so never assume a string here.
const asText = computed(() => (textState.value == null ? '' : String(textState.value)));

const value = computed(() => {
    if (type.value === 'set') {
        return { ...setState.value };
    }
    const name = params.value.variableName;
    if (type.value === 'numeric') {
        const n = Number(textState.value);
        return { [name]: Number.isFinite(n) && asText.value.trim() !== '' ? n : asText.value };
    }
    return { [name]: asText.value };
});

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;

function withinLength(s) {
    const min = params.value.min;
    const max = params.value.max;
    if (min != null && s.length < Number(min)) return false;
    if (max != null && s.length > Number(max)) return false;
    return true;
}

const valid = computed(() => {
    const text = asText.value;
    switch (type.value) {
        case 'set':
            // Any combination of toggles (including none) is a valid submission.
            return true;
        case 'text':
        case 'longtext':
            return text.trim().length > 0 && withinLength(text);
        case 'email':
            return EMAIL_RE.test(text.trim());
        case 'numeric': {
            const n = Number(textState.value);
            if (text.trim() === '' || !Number.isFinite(n)) return false;
            if (params.value.min != null && n < Number(params.value.min)) return false;
            if (params.value.max != null && n > Number(params.value.max)) return false;
            return true;
        }
        case 'time': {
            if (!TIME_RE.test(text)) return false;
            if (params.value.minTime && text < params.value.minTime) return false;
            if (params.value.maxTime && text > params.value.maxTime) return false;
            return true;
        }
        default:
            return true;
    }
});

watch([value, valid], () => {
    emit('update:value', value.value);
    emit('update:valid', valid.value);
}, { immediate: true, deep: true });

// --- time-input attributes -----------------------------------------------------

const timeStep = computed(() => {
    const g = Number(params.value.granularityMinutes);
    return Number.isFinite(g) && g > 0 ? g * 60 : 60;
});

const inputClass =
    'font-title bg-white px-2 py-1 rounded border border-solid border-grey-light text-sm ' +
    'focus:outline-none focus:border-orange-dark disabled:bg-grey-lighter';
</script>

<template>
    <!-- set: a checkbox per labelled option -->
    <span v-if="type === 'set'" class="inline-flex flex-wrap gap-x-4 gap-y-1 align-middle">
        <label
            v-for="option in params.options"
            :key="option.variableName"
            class="inline-flex items-center gap-1.5 font-title text-sm cursor-pointer"
        >
            <input
                type="checkbox"
                :disabled="disabled"
                v-model="setState[option.variableName]"
                class="accent-orange-dark cursor-pointer"
            />
            {{ option.text }}
        </label>
    </span>

    <textarea
        v-else-if="type === 'longtext'"
        v-model="textState"
        :disabled="disabled"
        :placeholder="segment.description ?? ''"
        :minlength="params.min ?? undefined"
        :maxlength="params.max ?? undefined"
        rows="2"
        :class="inputClass + ' align-middle w-full'"
    ></textarea>

    <input
        v-else-if="type === 'time'"
        type="time"
        v-model="textState"
        :disabled="disabled"
        :step="timeStep"
        :min="params.minTime ?? undefined"
        :max="params.maxTime ?? undefined"
        :class="inputClass + ' align-middle'"
    />

    <input
        v-else-if="type === 'numeric'"
        type="number"
        v-model="textState"
        :disabled="disabled"
        :placeholder="segment.description ?? ''"
        :min="params.min ?? undefined"
        :max="params.max ?? undefined"
        :class="inputClass + ' align-middle w-24'"
    />

    <input
        v-else
        :type="type === 'email' ? 'email' : 'text'"
        v-model="textState"
        :disabled="disabled"
        :placeholder="segment.description ?? ''"
        :minlength="params.min ?? undefined"
        :maxlength="params.max ?? undefined"
        :class="inputClass + ' align-middle'"
        :style="params.max ? { width: `${Math.min(Number(params.max) + 2, 40)}ch` } : {}"
    />
</template>
