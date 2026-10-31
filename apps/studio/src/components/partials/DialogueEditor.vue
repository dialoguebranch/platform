<script setup>
import { ref, inject, watch, computed, useTemplateRef } from 'vue';
import { VueFlow, Panel, MarkerType, BaseEdge, getStraightPath, useVueFlow } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { tagsToMap, mapToTags, getPosition, setPosition } from '@/authoring/DlbHeaderTags.js';
import { extractReplyLinks } from '@/authoring/DlbReplyLinks.js';
import { colorForNode } from '@/composables/node-colors.js';
import { isUndefinedSpeaker, projectSpeakers, speakerForNewNode } from '@/composables/project-speakers.js';
import { useLatestRequest } from '@/composables/latest-request.js';
import NodeEditPanel from './NodeEditPanel.vue';
import PreambleBar from './PreambleBar.vue';

// Pure content component — no header of its own. It's only ever embedded in
// DialogueWorkspace.vue's "edit" mode, whose own toolbar (with the balloon/text/edit
// ModeSelector) also hosts this component's Add Node / Refresh actions, via the exposed
// addNode/reload/isLoading/isCreatingNode below.
const props = defineProps({
    dialogueName: { type: String, default: null },
});

// nodeChanged/nodeDeleted let DialogueWorkspace.vue track, per tab, whether its dialogue was
// edited and which node was most recently touched — used to offer restarting a draft test from
// that node instead of Start, and to force a "live" (published) test back to Draft mode. Raised
// for every draft mutation, including position-only drags: even though a drag doesn't change what
// a test run does, it does make the draft diverge from what's currently being tested/published,
// which is the condition DialogueWorkspace's handleReturnFromEdit cares about. dialogueSaved fires
// on every successful save, including drags — used to tell the Dialogue Browser its
// "New"/"Draft"/"Deleted" labels and Publish-enablement may be stale.
const emit = defineEmits(['nodeChanged', 'nodeDeleted', 'dialogueSaved']);

const state = inject('state');
const client = useAuthoringClient();

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

// Start/End get a fixed accent regardless of their nodeColor tag — see the theme.css comment on
// --color-node-start/--color-node-end for why.
function nodeHeaderColor(data) {
    if (data.isStart) return 'var(--color-node-start)';
    if (data.isEnd) return 'var(--color-node-end)';
    return colorForNode(data.nodeColor);
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

const { fitView, setCenter, getViewport, screenToFlowCoordinate } = useVueFlow();

const loading = ref(false);
const flowNodes = ref([]);
const flowEdges = ref([]);

// Draft nodes as the server returns them ({ title, tags, body, ... }), keyed by title and kept in
// sync with the server, so a position edit can send the node's complete tags with only its
// position changed.
const rawNodesByTitle = ref(new Map());

function buildGraph(nodes) {
    rawNodesByTitle.value = new Map(nodes.map((node) => [node.title, node]));

    const parsedByTitle = new Map();
    nodes.forEach((node, index) => {
        const tags = tagsToMap(node.tags);
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
            comment: tags.get('comment') ?? '',
            nodeColor: tags.get('nodeColor') ?? null,
            externalLinks: externalLinkCounts.get(node.title) ?? 0,
            isStart: isReservedNodeTitle(node.title, RESERVED_NODE_START_ID),
            isEnd: isReservedNodeTitle(node.title, RESERVED_NODE_END_ID),
        },
    }));
    flowEdges.value = newFlowEdges;
}

// DialogueEditor is a single persistent instance reused across tabs (see DialogueWorkspace.vue),
// so switching to a different dialogue — or double-clicking Refresh — before a fetch resolves must
// not let a stale response overwrite the now-current graph. A monotonic request id (rather than
// comparing dialogue names) also correctly orders two in-flight requests for the *same* name.
const { next: nextLoadNodesRequest, isCurrent: isCurrentLoadNodesRequest } = useLatestRequest();

function loadNodes() {
    if (!props.dialogueName) return;
    loading.value = true;
    dismissError();
    const requestId = nextLoadNodesRequest();
    client.listDraftNodes(state.value.selectedProject?.slug, props.dialogueName)
        .then((nodes) => {
            if (!isCurrentLoadNodesRequest(requestId)) return;
            buildGraph(nodes);
        })
        .catch((error) => {
            if (!isCurrentLoadNodesRequest(requestId)) return;
            showError(describeError(error));
        })
        .finally(() => {
            if (!isCurrentLoadNodesRequest(requestId)) return;
            loading.value = false;
        });
}

// Center the view on the Start node once — the next time this dialogue's nodes are measured
// after a genuine "open" (dialogueName changing, including switching tabs), rather than on every
// node measurement (e.g. after adding/renaming a node, or a manual refresh), which would yank the
// view away from wherever the user is currently working.
let centerOnOpenPending = false;

watch(() => props.dialogueName, (name) => {
    centerOnOpenPending = !!name;
    loadNodes();
}, { immediate: true });

// Fires whenever Vue Flow (re)measures node dimensions — including after the fetch above resolves
// and populates flowNodes for the first time.
function onNodesInitialized() {
    if (!centerOnOpenPending) return;
    centerOnOpenPending = false;

    const startNode = flowNodes.value.find((n) => isReservedNodeTitle(n.id, RESERVED_NODE_START_ID));
    if (!startNode) {
        fitView({ duration: 300 });
        return;
    }
    // Fit to the whole graph first (instantly) to establish the same "see everything" zoom level
    // as before, then smoothly pan — at that zoom — so the Start node ends up centered rather than
    // wherever it happened to be laid out.
    fitView({ duration: 0 }).then(() => {
        const { zoom } = getViewport();
        const cx = startNode.position.x + (startNode.dimensions?.width ?? 0) / 2;
        const cy = startNode.position.y + (startNode.dimensions?.height ?? 0) / 2;
        setCenter(cx, cy, { zoom, duration: 300 });
    });
}

// ---- Position persistence (drag-end, not per-frame — same pattern as ResizablePanels.vue's
// saveLeftPanelWidth/saveRightPanelWidth on stopDragResize) ----

function onNodeDragStop({ node }) {
    const rawNode = rawNodesByTitle.value.get(node.id);
    if (!rawNode) return;
    const tags = setPosition(tagsToMap(rawNode.tags), node.position.x, node.position.y);
    dismissError();
    client.updateDraftNode(state.value.selectedProject?.slug, props.dialogueName, node.id,
        mapToTags(tags), rawNode.body)
        .then((updated) => {
            rawNodesByTitle.value.set(node.id, updated);
            emit('nodeChanged', node.id);
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

// Rough on-screen footprint of a collapsed node (see the `#node-dialogueNode` template below) —
// just enough to center a newly placed node under the cursor rather than anchoring its corner.
const NEW_NODE_WIDTH = 180;
const NEW_NODE_HEIGHT = 30;

function addNode(position) {
    if (!props.dialogueName || creatingNode.value) return;
    const title = nextDefaultTitle();
    let x, y;
    if (position) {
        x = position.x - NEW_NODE_WIDTH / 2;
        y = position.y - NEW_NODE_HEIGHT / 2;
    } else {
        // Cascade new nodes diagonally from the origin so repeated clicks don't stack on top of
        // each other; a proper "place near the current viewport" position can follow later.
        const index = rawNodesByTitle.value.size;
        x = (index % GRID_COLUMNS) * GRID_SPACING_X;
        y = Math.floor(index / GRID_COLUMNS) * GRID_SPACING_Y;
    }
    // A new node gets the project's default speaker, or else its first one (#396).
    const header = new Map([['title', title]]);
    const speaker = speakerForNewNode(projectSpeakers.value);
    if (speaker) header.set('speaker', speaker);
    const tags = mapToTags(setPosition(header, x, y));

    creatingNode.value = true;
    dismissError();
    client.createDraftNode(state.value.selectedProject?.slug, props.dialogueName, tags, '')
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

function onPaneContextMenu(event) {
    event.preventDefault();
    addNode(screenToFlowCoordinate({ x: event.clientX, y: event.clientY }));
}

// ---- Node selection / content editing ----

const selectedNodeTitle = ref(null);
const selectedNode = computed(() => rawNodesByTitle.value.get(selectedNodeTitle.value) ?? null);
const nodeEditPanel = useTemplateRef('node-edit-panel');

function onNodeClick({ node }) {
    // Switching the selected node repopulates NodeEditPanel's form from the new node's data,
    // silently discarding any in-progress edit to the previously selected node — refuse to do
    // that while a save for it is still in flight (re-clicking the same node is always a no-op
    // regardless). See NodeEditPanel's own closePanel() for the equivalent guard on Close/Cancel.
    if (nodeEditPanel.value?.saving && node.id !== selectedNodeTitle.value) return;
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

const preambleBar = useTemplateRef('preamble-bar');

function reload() {
    loadNodes();
    preambleBar.value?.reload();
}

defineExpose({
    reload,
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
            class="h-full"
            @node-drag-stop="onNodeDragStop"
            @node-click="onNodeClick"
            @nodes-initialized="onNodesInitialized"
            @pane-context-menu="onPaneContextMenu"
        >
            <Panel position="top-left">
                <PreambleBar ref="preamble-bar" :dialogue-name="dialogueName" @saved="emit('dialogueSaved')" />
            </Panel>

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
                        <span v-if="data.comment" class="ml-auto shrink-0 cursor-help" :title="data.comment" data-test="node-comment-icon">
                            <FontAwesomeIcon icon="fa-regular fa-comment" class="text-[11px]" />
                        </span>
                    </div>
                    <div v-if="data.speaker || data.externalLinks" class="px-3 py-2 text-xs font-title">
                        <div v-if="data.speaker" class="flex items-center gap-1 text-grey-dark min-w-0">
                            <span class="truncate">{{ data.speaker }}</span>
                            <span
                                v-if="isUndefinedSpeaker(data.speaker, projectSpeakers)"
                                class="shrink-0 text-orange-dark cursor-help"
                                :title="`&quot;${data.speaker}&quot; isn't one of the project's speakers. Add it in Configure Project, or choose another speaker.`"
                                data-test="node-undefined-speaker"
                            >
                                <FontAwesomeIcon icon="fa-solid fa-triangle-exclamation" class="text-[11px]" />
                            </span>
                        </div>
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
            ref="node-edit-panel"
            :node="selectedNode"
            :dialogueName="dialogueName"
            @close="selectedNodeTitle = null"
            @saved="onNodeSaved"
            @deleted="onNodeDeleted"
            @renamed="onNodeRenamed"
        />
    </div>
</template>
