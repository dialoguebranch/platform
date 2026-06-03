<script setup>
import { ref } from 'vue';
import { useClient } from '../../composables/client.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import IconButton from '../widgets/IconButton.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';

defineEmits([
    'selectDialogue',
]);

const client = useClient();

const dialogues = ref([]);

function listDialogues() {
    client.listDialogues()
    .then((json) => {
        dialogues.value = json.dialogueNames;
    })
    .catch((error) => {
        console.log(error);
    });
}

listDialogues();
</script>

<template>
    <div class="flex flex-col gap-1">
        <MainPagePanelHeader title="Dialogue Browser" class="sm:ml-2">
            <template #buttons>
                <IconButton icon="fa-solid fa-arrows-rotate" @click="listDialogues" />
            </template>
        </MainPagePanelHeader>
        <MainPagePanelContainer class="p-1 gap-1 flex flex-col sm:ml-1">
            <div
                v-for="dialogue in dialogues"
                class="cursor-pointer bg-grey-lighter text-orange-darker hover:text-orange-dark font-title font-black text-xs p-1"
                @click="$emit('selectDialogue', dialogue)"
            >
                <FontAwesomeIcon icon="fa fa-circle-play" />
                {{ dialogue }}
            </div>
        </MainPagePanelContainer>
    </div>
</template>
