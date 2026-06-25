<script>
export default { inheritAttrs: false };
</script>

<script setup>
import { onMounted, ref, useAttrs } from 'vue';
const attrs = useAttrs();
import { useClient } from '@/composables/client.js';
import { logEvent } from '@/composables/debug-log.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import IconButton from '../widgets/IconButton.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';

function formatUpdatedTime(variable) {
    if (!variable.updatedTime) return 'Unknown';
    const date = new Date(variable.updatedTime);
    const tz = variable.updatedTimeZone || Intl.DateTimeFormat().resolvedOptions().timeZone;
    return date.toLocaleString(undefined, { timeZone: tz, dateStyle: 'medium', timeStyle: 'medium' });
}

const sourceLabels = {
    UNKNOWN:      { label: 'Unknown',          italic: true },
    DLB_SCRIPT:   { label: 'Script',           italic: false },
    INPUT_REPLY:  { label: 'User Input',       italic: false },
    WEB_SERVICE:  { label: 'Web Service',      italic: false },
    EXTERNAL:     { label: 'External Service', italic: false },
};

function formatUpdatedSource(variable) {
    return sourceLabels[variable.updatedSource] ?? { label: 'Unknown', italic: true };
}

const tooltip = ref(null);   // { variable, x, y }

function showTooltip(event, variable) {
    const rect = event.currentTarget.getBoundingClientRect();
    tooltip.value = {
        variable,
        x: rect.left - 8,
        y: rect.top + rect.height / 2,
    };
}

function hideTooltip() {
    tooltip.value = null;
}

const emit = defineEmits([
    'changeVariable',
]);

const client = useClient();

const variables = ref([]);

const loadVariables = () => {
    client.getVariables()
    .then((vars) => {
        variables.value = vars;
        dirtyVariables.value = new Set();
        deletingVariables.value = new Set();
        tooltip.value = null;
    });
};

defineExpose({
    loadVariables,
});

function deleteVariable(name) {
    confirmingDelete.value = null;
    logEvent('variable', 'Variable $1 deleted', name);
    const next = new Set(deletingVariables.value);
    next.add(name);
    deletingVariables.value = next;
    client.setVariable(name, null)
    .then(() => {
        emit('changeVariable');
        return loadVariables();
    });
}

const dirtyVariables = ref(new Set());
const confirmingDelete = ref(null);
const deletingVariables = ref(new Set());

function onVariableInput(variable) {
    dirtyVariables.value = new Set([...dirtyVariables.value, variable.name]);
}

function submitVariable(variable) {
    if (!dirtyVariables.value.has(variable.name)) return;
    logEvent('variable', 'Variable $1 updated to $2', variable.name, variable.value);
    client.setVariable(variable.name, variable.value)
    .then(() => {
        const next = new Set(dirtyVariables.value);
        next.delete(variable.name);
        dirtyVariables.value = next;
        emit('changeVariable');
    });
}

onMounted(() => {
    loadVariables();
});
</script>

<template>
<div class="flex flex-col gap-1" v-bind="attrs">
        <MainPagePanelHeader title="Variable Browser" class="sm:mr-1">
            <template #buttons>
                <IconButton icon="fa-solid fa-arrows-rotate" />
            </template>
        </MainPagePanelHeader>
        <MainPagePanelContainer class="sm:mr-1">
            <TransitionGroup tag="div" name="fade" class="flex flex-col gap-0.5 m-1 overflow-hidden flex flex-col">
                <div v-for="variable in variables" :key="variable.name" class="flex items-center bg-grey-lighter px-1 py-0.5 gap-1"
                    :class="{ 'opacity-0 transition-opacity duration-500': deletingVariables.has(variable.name) }">
                    <div class="font-title font-semibold text-xs text-orange-darker shrink-0">${{ variable.name }}</div>
                    <input type="text" class="font-title text-xs px-1 py-0.5 min-w-0 grow border border-grey-light rounded bg-white focus:outline-none focus:border-orange-dark" v-model="variable.value" @input="onVariableInput(variable)" @keyup.enter="submitVariable(variable)"></input>
                    <button type="button" class="w-5 h-5 flex items-center justify-center rounded"
                        :class="dirtyVariables.has(variable.name) ? 'text-orange-darker hover:text-orange-dark cursor-pointer' : 'text-grey-light cursor-not-allowed'"
                        :disabled="!dirtyVariables.has(variable.name)"
                        @click.stop="submitVariable(variable)">
                        <FontAwesomeIcon icon="fa-solid fa-cloud-arrow-up" />
                    </button>
                    <div class="flex items-center gap-1">
                        <button class="w-5 h-5 flex items-center justify-center text-orange-darker hover:text-orange-dark cursor-pointer"
                            @mouseenter="showTooltip($event, variable)"
                            @mouseleave="hideTooltip">
                            <FontAwesomeIcon icon="fa-solid fa-circle-info" />
                        </button>
                        <template v-if="confirmingDelete === variable.name">
                            <span class="font-title text-xs text-grey-dark whitespace-nowrap">Delete?</span>
                            <button type="button" class="w-5 h-5 flex items-center justify-center text-icon-button-warning hover:text-icon-button-warning-hover cursor-pointer" title="Confirm delete" @click.stop="deleteVariable(variable.name)">
                                <FontAwesomeIcon icon="fa-solid fa-check" />
                            </button>
                            <button type="button" class="w-5 h-5 flex items-center justify-center text-orange-darker hover:text-orange-dark cursor-pointer" title="Cancel" @click.stop="confirmingDelete = null">
                                <FontAwesomeIcon icon="fa-solid fa-xmark" />
                            </button>
                        </template>
                        <IconButton v-else type="list-item" icon="fa-solid fa-trash" color="warning" @click.stop="confirmingDelete = variable.name" />
                    </div>
                </div>
            </TransitionGroup>
        </MainPagePanelContainer>
</div>

<Teleport to="body">
        <div v-if="tooltip"
            class="fixed z-[9999] w-max bg-white border border-grey-light shadow-md rounded p-2 text-xs font-title text-grey-dark pointer-events-none -translate-x-full -translate-y-1/2"
            :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }">
            <div class="font-semibold text-orange-darker mb-1">Variable Info</div>
            <table class="whitespace-nowrap">
                <tbody>
                    <tr><td class="font-semibold pr-2 text-right">Updated:</td><td :class="{ italic: !tooltip.variable.updatedTime }">{{ formatUpdatedTime(tooltip.variable) }}</td></tr>
                    <tr><td class="font-semibold pr-2 text-right">Timezone:</td><td :class="{ italic: !tooltip.variable.updatedTimeZone }">{{ tooltip.variable.updatedTimeZone || 'Unknown' }}</td></tr>
                    <tr><td class="font-semibold pr-2 text-right">Source:</td><td :class="{ italic: formatUpdatedSource(tooltip.variable).italic }">{{ formatUpdatedSource(tooltip.variable).label }}</td></tr>
                </tbody>
            </table>
        </div>
</Teleport>
</template>

<style scoped>
.fade-leave-active {
    transition: opacity 0.5s ease;
}
.fade-leave-to {
    opacity: 0;
}
</style>
