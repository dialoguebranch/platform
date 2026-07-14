<script setup>
import { ref, inject, watch, computed } from 'vue';
import { VueFlow } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { parseHeaderTags, serializeHeaderTags, getPosition, setPosition } from '@/dlb-lib/util/DlbHeaderTags.js';
import { extractReplyLinks } from '@/dlb-lib/util/DlbReplyLinks.js';
import { colorForId } from '@/composables/node-colors.js';
import NodeEditPanel from './NodeEditPanel.vue';

// Pure content component — no header of its own. It's only ever embedded in
// InteractionTester.vue's "edit" mode, whose own header (with the balloon/text/edit
// ModeSelector) also hosts this component's Add Node / Refresh actions, via the exposed
// addNode/reload/isLoading/isCreatingNode below.
const props = defineProps({
    dialogueName: { type: String, default: null },
});

// Lets InteractionTester.vue track, per tab, whether its dialogue was edited and which node was
// most recently touched — used to offer restarting a draft test from that node instead of Start.
const emit = defineEmits(['nodeChanged', 'nodeDeleted']);

const state = inject('state');
const client = useClient();

// Simple grid fallback for nodes that don't have a `position` tag yet (e.g. dialogues authored
// before this editor existed) — just enough so nothing renders stacked on top of itself.
const GRID_COLUMNS = 4;
const GRID_SPACING_X = 240;
const GRID_SPACING_Y = 140;

const loading = ref(false);
const flowNodes = ref([]);
const flowEdges = ref([]);

// Raw DBDraftNode rows keyed by title, kept in sync with the server so position/content edits
// can reconstruct a node's full header (parse → mutate one field → re-serialize) without
// clobbering any other tags already on it.
const rawNodesByTitle = ref(new Map());

function buildGraph(nodes) {
    rawNodesByTitle.value = new Map(nodes.map((node) => [node.title, node]));

    const parsedByTitle = new Map();
    nodes.forEach((node, index) => {
        const tags = parseHeaderTags(node.header);
        const hasPosition = tags.has('position');
        const position = hasPosition
            ? getPosition(tags)
            : { x: (index % GRID_COLUMNS) * GRID_SPACING_X, y: Math.floor(index / GRID_COLUMNS) * GRID_SPACING_Y };
        parsedByTitle.set(node.title, { node, tags, position });
    });

    const externalLinkCounts = new Map();
    const newFlowEdges = [];
    for (const { node } of parsedByTitle.values()) {
        for (const link of extractReplyLinks(node.body)) {
            if (link.isExternal) {
                externalLinkCounts.set(node.title, (externalLinkCounts.get(node.title) ?? 0) + 1);
                continue;
            }
            // A dangling internal link (target doesn't exist in this dialogue) is a validation
            // problem the "Publish Project" flow already surfaces — just skip drawing an edge.
            if (!parsedByTitle.has(link.nodeTitle)) continue;
            newFlowEdges.push({
                id: `${node.title}=>${link.nodeTitle}#${newFlowEdges.length}`,
                source: node.title,
                target: link.nodeTitle,
                label: link.displayText ?? undefined,
            });
        }
    }

    flowNodes.value = [...parsedByTitle.values()].map(({ node, tags, position }) => ({
        id: node.title,
        type: 'dialogueNode',
        position,
        data: {
            title: node.title,
            speaker: tags.get('speaker') ?? '',
            colorId: tags.get('colorId') ?? '0',
            externalLinks: externalLinkCounts.get(node.title) ?? 0,
        },
    }));
    flowEdges.value = newFlowEdges;
}

function loadNodes() {
    if (!props.dialogueName) return;
    loading.value = true;
    dismissError();
    client.listDraftNodes(state.value.selectedProject?.slug, props.dialogueName)
        .then((nodes) => {
            buildGraph(nodes);
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            loading.value = false;
        });
}

watch(() => props.dialogueName, loadNodes, { immediate: true });

// ---- Position persistence (drag-end, not per-frame — same pattern as ResizablePanels.vue's
// saveLeftPanelWidth/saveRightPanelWidth on stopDragResize) ----

function onNodeDragStop({ node }) {
    const rawNode = rawNodesByTitle.value.get(node.id);
    if (!rawNode) return;
    const tags = parseHeaderTags(rawNode.header);
    setPosition(tags, node.position.x, node.position.y);
    const newHeader = serializeHeaderTags(tags);
    dismissError();
    client.updateDraftNode(state.value.selectedProject?.slug, props.dialogueName, node.id,
        newHeader, rawNode.body)
        .then((updated) => {
            // Position-only changes don't count as content edits — they don't affect what a test
            // run of this dialogue actually does — so no 'nodeChanged' here (see InteractionTester's
            // handleReturnFromEdit, which restarts/notifies based on that event).
            rawNodesByTitle.value.set(node.id, updated);
        })
        .catch((error) => {
            showError(describeError(error));
        });
}

// ---- Node creation ----

const creatingNode = ref(false);

function nextDefaultTitle() {
    const existing = new Set(rawNodesByTitle.value.keys());
    let n = existing.size + 1;
    while (existing.has(`NewNode${n}`)) n++;
    return `NewNode${n}`;
}

function addNode() {
    if (!props.dialogueName || creatingNode.value) return;
    const title = nextDefaultTitle();
    // Cascade new nodes diagonally from the origin so repeated clicks don't stack on top of
    // each other; a proper "place near the current viewport" position can follow later.
    const index = rawNodesByTitle.value.size;
    const x = (index % GRID_COLUMNS) * GRID_SPACING_X;
    const y = Math.floor(index / GRID_COLUMNS) * GRID_SPACING_Y;
    const header = serializeHeaderTags(setPosition(new Map([['title', title]]), x, y));

    creatingNode.value = true;
    dismissError();
    client.createDraftNode(state.value.selectedProject?.slug, props.dialogueName, title, header, '')
        .then(() => {
            loadNodes();
            emit('nodeChanged', title);
        })
        .catch((error) => {
            showError(describeError(error));
        })
        .finally(() => {
            creatingNode.value = false;
        });
}

// ---- Node selection / content editing ----

const selectedNodeTitle = ref(null);
const selectedNode = computed(() => rawNodesByTitle.value.get(selectedNodeTitle.value) ?? null);

function onNodeClick({ node }) {
    selectedNodeTitle.value = node.id;
}

function onNodeSaved(updatedNode) {
    rawNodesByTitle.value.set(updatedNode.title, updatedNode);
    buildGraph([...rawNodesByTitle.value.values()]);
    selectedNodeTitle.value = null;
    emit('nodeChanged', updatedNode.title);
}

function onNodeDeleted(deletedTitle) {
    rawNodesByTitle.value.delete(deletedTitle);
    buildGraph([...rawNodesByTitle.value.values()]);
    selectedNodeTitle.value = null;
    emit('nodeDeleted', deletedTitle);
}

function onNodeRenamed(updatedNode) {
    // A rename can rewrite OTHER nodes' bodies too (if references were updated) — a full reload
    // is simplest and safest, rather than trying to replicate that rewrite client-side.
    selectedNodeTitle.value = null;
    loadNodes();
    emit('nodeChanged', updatedNode.title);
}

defineExpose({
    reload: loadNodes,
    addNode,
    isLoading: () => loading.value,
    isCreatingNode: () => creatingNode.value,
});
</script>

<template>
    <div class="relative h-full">
        <div v-if="!dialogueName" class="flex items-center justify-center h-full text-grey-dark font-title text-sm">
            Select "Edit" on a draft dialogue to open it here.
        </div>
        <VueFlow
            v-else
            v-model:nodes="flowNodes"
            v-model:edges="flowEdges"
            fit-view-on-init
            class="h-full"
            @node-drag-stop="onNodeDragStop"
            @node-click="onNodeClick"
        >
            <template #node-dialogueNode="{ data }">
                <div
                    class="rounded-lg shadow border-l-4 bg-white px-3 py-2 text-xs font-title min-w-[140px] max-w-[200px] cursor-pointer hover:shadow-md"
                    :style="{ borderLeftColor: colorForId(data.colorId) }"
                >
                    <div class="font-bold text-orange-darker truncate">{{ data.title }}</div>
                    <div v-if="data.speaker" class="text-grey-dark truncate">{{ data.speaker }}</div>
                    <div v-if="data.externalLinks" class="mt-1 inline-flex items-center gap-1 text-[10px] text-orange-dark" :title="`${data.externalLinks} link(s) to other dialogues`">
                        <FontAwesomeIcon icon="fa-solid fa-arrow-up-right-from-square" />
                        {{ data.externalLinks }} external
                    </div>
                </div>
            </template>
        </VueFlow>

        <NodeEditPanel
            v-if="selectedNode"
            :node="selectedNode"
            :dialogueName="dialogueName"
            @close="selectedNodeTitle = null"
            @saved="onNodeSaved"
            @deleted="onNodeDeleted"
            @renamed="onNodeRenamed"
        />
    </div>
</template>
