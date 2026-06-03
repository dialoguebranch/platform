<script setup>
import { onMounted, ref } from 'vue';
import { useClient } from '@/composables/client.js';
import IconButton from '../widgets/IconButton.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';

const emit = defineEmits([
    'changeVariable',
]);

const client = useClient();

const variables = ref([]);

const loadVariables = () => {
    client.getVariables()
    .then((vars) => {
        variables.value = vars;
    });
};

defineExpose({
    loadVariables,
});

function deleteVariable(name) {
    client.setVariable(name, null)
    .then(() => {
        emit('changeVariable');
        return loadVariables();
    });
}

function onChangeVariable(name, value) {
    client.setVariable(name, value)
    .then(() => {
        emit('changeVariable');
    });
}

onMounted(() => {
    loadVariables();
});
</script>

<template>
    <div class="flex flex-col gap-1">
        <MainPagePanelHeader title="Variable Browser" class="sm:mr-1">
            <template #buttons>
                <IconButton icon="fa-solid fa-arrows-rotate" />
            </template>
        </MainPagePanelHeader>
        <MainPagePanelContainer class="sm:mr-1">
            <div class="flex flex-col gap-1 m-1 overflow-hidden flex flex-col">
                <div v-for="variable in variables" :key="variable.name" class="flex bg-grey-lighter p-1 gap-1">
                    <div class="basis-0 grow flex flex-col">
                        <div class="font-title font-semibold text-sm text-orange-darker mx-1">{{ variable.name }}</div>
                        <input type="text" class="block font-title text-sm mt-1 p-1" v-model="variable.value" @input="onChangeVariable(variable.name, variable.value)"></input>
                    </div>
                    <div class="flex items-center">
                        <IconButton type="list-item" icon="fa-solid fa-trash" color="warning" @click="deleteVariable(variable.name)" />
                    </div>
                </div>
            </div>
        </MainPagePanelContainer>
    </div>
</template>
