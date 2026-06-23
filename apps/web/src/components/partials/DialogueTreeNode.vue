<script setup>
import { computed } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const props = defineProps({
    name: String,
    node: Object,
    path: String,
    openFolders: Object,
    depth: {
        type: Number,
        default: 0,
    },
});

const emit = defineEmits(['toggleFolder', 'selectDialogue']);

const isFile = computed(() => !!props.node._file);
const isOpen = computed(() => !!props.openFolders[props.path]);
const children = computed(() => {
    if (isFile.value) return [];
    return Object.entries(props.node._children).sort(([, a], [, b]) => {
        const aIsFolder = !a._file;
        const bIsFolder = !b._file;
        if (aIsFolder !== bIsFolder) return aIsFolder ? -1 : 1;
        return 0;
    });
});
</script>

<template>
    <div>
        <!-- Folder -->
        <div
            v-if="!isFile"
            class="cursor-pointer flex items-center gap-1 font-title font-black text-xs p-1 text-gray-600 hover:text-gray-800 select-none"
            :style="{ paddingLeft: (depth * 12 + 4) + 'px' }"
            @click="$emit('toggleFolder', path)"
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
                :depth="depth + 1"
                @toggleFolder="$emit('toggleFolder', $event)"
                @selectDialogue="$emit('selectDialogue', $event)"
            />
        </template>

        <!-- File -->
        <div
            v-if="isFile"
            class="cursor-pointer flex items-center gap-1 font-title font-black text-xs p-1 text-orange-darker hover:text-orange-dark"
            :style="{ paddingLeft: (depth * 12 + 4) + 'px' }"
            @click="$emit('selectDialogue', node._file)"
        >
            <FontAwesomeIcon icon="fa-solid fa-circle-play" class="w-3.5" />
            <span>{{ name }}</span>
        </div>
    </div>
</template>
