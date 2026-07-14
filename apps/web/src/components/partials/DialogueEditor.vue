<script setup>
import { ref, inject, watch, computed } from 'vue';
import { VueFlow, MarkerType, BaseEdge, getStraightPath } from '@vue-flow/core';
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
// DialogueWorkspace.vue's "edit" mode, whose own header (with the balloon/text/edit
// ModeSelector) also hosts this component's Add Node / Refresh actions, via the exposed
// addNode/reload/isLoading/isCreatingNode below.
const props = defineProps({
    dialogueName: { type: String, default: null },
});

// nodeChanged/nodeDeleted let DialogueWorkspace.vue track, per tab, whether its dialogue was
// edited and which node was most recently touched — used to offer restarting a draft test from
// that node instead of Start (deliberately not raised for position-only drags, which don't affect
// what a test run does). dialogueSaved fires on every successful save, including drags — used to
// tell the Dialogue Browser its "New"/"Draft"/"Deleted" labels and Publish-enablement may be stale.
const emit = defineEmits(['nodeChanged', 'nodeDeleted', 'dialogueSaved']);

const state = inject('state');
const client = useClient();

// Simple grid fallback for nodes that don't have a `position` tag yet (e.g. dialogues authored
// before this editor existed) — just enough so nothing renders stacked on top of itself.
const GRID_COLUMNS = 4;
const GRID_SPACING_X = 240;
const GRID_SPACING_Y = 140;

// Matches theme.css's --color-lines (orange-dark) — used directly rather than via var() since
// edge markers are rendered into an SVG <defs> block where CSS custom property inheritance isn't
// reliable across all browsers.
const EDGE_COLOR = '#996600';

// Mirrors DialogueBranchConstants.DLB_NODE_START_ID / DLB_NODE_END_ID (core), whose comparisons
// are case-insensitive (see ActiveDialogue.java) — every dialogue needs a "Start" node as its
// entry point, and an "End" node (with no body) is how a dialogue terminates.
const RESERVED_NODE_START_ID = 'start';
const RESERVED_NODE_END_ID = 'end';

function isReservedNodeTitle(title, reservedId) {
    return title?.toLowerCase() === reservedId;
}

// Start/End get a fixed accent regardless of their colorId tag — see the theme.css comment on
// --color-node-start/--color-node-end for why.
function nodeHeaderColor(data) {
    if (data.isStart) return 'var(--color-node-start)';
    if (data.isEnd) return 'var(--color-node-end)';
    return colorForId(data.colorId);
}

// ---- Floating edges: connect each edge to the point on the node's rectangle nearest the other
// node (its center-to-center line's boundary intersection), rather than a fixed handle position.
// Our node template has no <Handle> elements at all (edges are derived from parsed [[links]], not
// user-drawn), so Vue Flow's normal handle-based positioning falls back to a fixed Top/Bottom
// point — this recomputes sourceX/Y and targetX/Y ourselves instead. See Vue Flow's "Floating
// Edges" example for the reference version of this geometry.

function getNodeCenter(node) {
    return {
        x: node.position.x + (node.dimensions?.width ?? 0) / 2,
        y: node.position.y + (node.dimensions?.height ?? 0) / 2,
    };
}

// The point on `node`'s rectangle where the line from its center to `otherNode`'s center exits.
function getNodeIntersection(node, otherNode) {
    const center = getNodeCenter(node);
    const { width, height } = node.dimensions ?? {};
    // Dimensions aren't measured yet on a node's first render — fall back to its center until
    // Vue Flow's ResizeObserver fills them in and this edge is recomputed.
    if (!width || !height) return center;

    const otherCenter = getNodeCenter(otherNode);
    const w = width / 2;
    const h = height / 2;

    const xx1 = (otherCenter.x - center.x) / (2 * w) - (otherCenter.y - center.y) / (2 * h);
    const yy1 = (otherCenter.x - center.x) / (2 * w) + (otherCenter.y - center.y) / (2 * h);
    const a = 1 / (Math.abs(xx1) + Math.abs(yy1) || 1);

    return {
        x: w * (a * xx1 + a * yy1) + center.x,
        y: h * (-a * xx1 + a * yy1) + center.y,
    };
}

function floatingEdgePath(sourceNode, targetNode) {
    const sourcePoint = getNodeIntersection(sourceNode, targetNode);
    const targetPoint = getNodeIntersection(targetNode, sourceNode);
    const [path, labelX, labelY] = getStraightPath({
        sourceX: sourcePoint.x,
        sourceY: sourcePoint.y,
        targetX: targetPoint.x,
        targetY: targetPoint.y,
    });
    return { path, labelX, labelY };
}

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
                type: 'floating',
                markerEnd: { type: MarkerType.ArrowClosed, color: EDGE_COLOR },
                style: { stroke: EDGE_COLOR, strokeWidth: 1.5 },
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
            isStart: isReservedNodeTitle(node.title, RESERVED_NODE_START_ID),
            isEnd: isReservedNodeTitle(node.title, RESERVED_NODE_END_ID),
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
            // run of this dialogue actually does — so no 'nodeChanged' here (see DialogueWorkspace's
            // handleReturnFromEdit, which restarts/notifies based on that event). They do still
            // change the draft's published-vs-current status, though (position is part of the
            // reconstructed script), so 'dialogueSaved' still fires.
            rawNodesByTitle.value.set(node.id, updated);
            emit('dialogueSaved');
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
            emit('dialogueSaved');
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
    emit('dialogueSaved');
}

function onNodeDeleted(deletedTitle) {
    rawNodesByTitle.value.delete(deletedTitle);
    buildGraph([...rawNodesByTitle.value.values()]);
    selectedNodeTitle.value = null;
    emit('nodeDeleted', deletedTitle);
    emit('dialogueSaved');
}

function onNodeRenamed(updatedNode) {
    // A rename can rewrite OTHER nodes' bodies too (if references were updated) — a full reload
    // is simplest and safest, rather than trying to replicate that rewrite client-side.
    selectedNodeTitle.value = null;
    loadNodes();
    emit('nodeChanged', updatedNode.title);
    emit('dialogueSaved');
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
            Open a dialogue from the Dialogue Browser to start editing.
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
                    class="rounded-lg shadow bg-white min-w-[150px] max-w-[210px] overflow-hidden cursor-pointer hover:shadow-md"
                    :class="{
                        'ring-2 ring-offset-1 ring-node-start': data.isStart,
                        'ring-2 ring-offset-1 ring-node-end': data.isEnd,
                    }"
                >
                    <div class="px-3 py-1.5 text-xs font-title font-bold text-white truncate flex items-center gap-1.5" :style="{ backgroundColor: nodeHeaderColor(data) }">
                        <FontAwesomeIcon v-if="data.isStart" icon="fa-solid fa-play" class="text-[10px] shrink-0" title="Start node — the dialogue's entry point" />
                        <FontAwesomeIcon v-if="data.isEnd" icon="fa-solid fa-flag-checkered" class="text-[10px] shrink-0" title="End node — terminates the dialogue" />
                        <span class="truncate">{{ data.title }}</span>
                    </div>
                    <div v-if="data.speaker || data.externalLinks" class="px-3 py-2 text-xs font-title">
                        <div v-if="data.speaker" class="text-grey-dark truncate">{{ data.speaker }}</div>
                        <div v-if="data.externalLinks" class="mt-1 inline-flex items-center gap-1 text-[10px] text-orange-dark" :title="`${data.externalLinks} link(s) to other dialogues`">
                            <FontAwesomeIcon icon="fa-solid fa-arrow-up-right-from-square" />
                            {{ data.externalLinks }} external
                        </div>
                    </div>
                    <div v-if="data.isEnd" class="px-3 py-2 text-[10px] italic text-grey-dark">Ends the conversation — no content</div>
                </div>
            </template>

            <template #edge-floating="{ id, sourceNode, targetNode, markerEnd, style, label, labelStyle, labelShowBg, labelBgStyle, labelBgPadding, labelBgBorderRadius }">
                <BaseEdge
                    :id="id"
                    :path="floatingEdgePath(sourceNode, targetNode).path"
                    :label-x="floatingEdgePath(sourceNode, targetNode).labelX"
                    :label-y="floatingEdgePath(sourceNode, targetNode).labelY"
                    :label="label"
                    :label-style="labelStyle"
                    :label-show-bg="labelShowBg"
                    :label-bg-style="labelBgStyle"
                    :label-bg-padding="labelBgPadding"
                    :label-bg-border-radius="labelBgBorderRadius"
                    :marker-end="markerEnd"
                    :style="style"
                />
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
