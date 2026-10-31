<script setup>
import { inject, ref } from 'vue';
import { useAuthoringClient, getBrowserTimeZone } from '@/composables/client.js';
import { logEvent } from '@/composables/debug-log.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';

// "Test as a female, male or undefined user" (#454). In Authoring Mode, tests run against the
// tester's copy of the variables; this sets the end user's gender in it, $dlb.userGender, which
// selects gender-specific translations from the next step on. It shows the copy's current value,
// also when that was changed elsewhere (call load()).
const emit = defineEmits(['changed']);

const state = inject('state');
const client = useAuthoringClient();

const USER_GENDER = 'dlb.userGender';
const GENDERS = [
    { value: 'undefined', label: 'any user' },
    { value: 'female', label: 'a female user' },
    { value: 'male', label: 'a male user' },
];
const gender = ref('undefined');

function load() {
    const slug = state.value.selectedProject?.slug;
    if (!slug) return;
    client.getTestVariables({ projectSlug: slug, timeZone: getBrowserTimeZone() })
        .then((variables) => {
            const value = variables.find((v) => v.name === USER_GENDER)?.value;
            gender.value = value === 'female' || value === 'male' ? value : 'undefined';
        })
        .catch(() => { /* keeps its value; testing still works */ });
}

function onChange(value) {
    const slug = state.value.selectedProject?.slug;
    if (!slug) return;
    gender.value = value;
    dismissError();
    client.setTestVariable({
        projectSlug: slug, variableName: USER_GENDER,
        variableValue: value === 'undefined' ? null : value, timeZone: getBrowserTimeZone(),
    })
        .then(() => {
            logEvent('variables', 'Testing as $1', GENDERS.find((g) => g.value === value)?.label);
            emit('changed');
        })
        .catch((error) => {
            showError(describeError(error));
            load();
        });
}

load();

defineExpose({ load });
</script>

<template>
    <span class="text-xs font-title text-grey-dark whitespace-nowrap">as</span>
    <select
        :value="gender"
        title="The end user's gender in tests ($dlb.userGender in the test values): it selects translations that change with it, from the next step on"
        data-test="test-user-gender"
        class="h-6 min-w-0 px-2 border border-grey-light rounded-lg text-xs font-title focus:outline-none focus:border-orange-dark bg-white cursor-pointer"
        @change="onChange($event.target.value)"
    >
        <option v-for="g in GENDERS" :key="g.value" :value="g.value">{{ g.label }}</option>
    </select>
</template>
