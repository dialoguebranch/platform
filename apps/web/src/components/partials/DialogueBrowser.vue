<script setup>
import { ref } from 'vue';
import { useClient } from '../../composables/client.js';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import IconButton from '../widgets/IconButton.vue';
import MainPagePanelHeader from '../widgets/MainPagePanelHeader.vue';
import MainPagePanelContainer from '../widgets/MainPagePanelContainer.vue';
import DialogueTreeNode from './DialogueTreeNode.vue';

defineEmits([
    'selectDialogue',
]);

const client = useClient();

const tree = ref([]);
const openFolders = ref({});

function buildTree(names) {
    const root = {};
    for (const name of names) {
        const parts = name.split('/');
        let node = root;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!node[parts[i]]) node[parts[i]] = { _children: {} };
            node = node[parts[i]]._children;
        }
        const leaf = parts[parts.length - 1];
        node[leaf] = { _file: name };
    }
    return root;
}

function listDialogues() {
    client.listDialogues()
    .then((json) => {
        const root = buildTree(json.dialogueNames);
        tree.value = Object.entries(root).sort(([, a], [, b]) => {
            const aIsFolder = !a._file;
            const bIsFolder = !b._file;
            if (aIsFolder !== bIsFolder) return aIsFolder ? -1 : 1;
            return 0;
        });
        openFolders.value = {};
    })
    .catch((error) => {
        console.log(error);
    });
}

function toggleFolder(path) {
    openFolders.value[path] = !openFolders.value[path];
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
            <DialogueTreeNode
                v-for="[name, node] in tree"
                :key="name"
                :name="name"
                :node="node"
                :path="name"
                :openFolders="openFolders"
                @toggleFolder="toggleFolder"
                @selectDialogue="$emit('selectDialogue', $event)"
            />
        </MainPagePanelContainer>
    </div>
</template>
