<script setup>
import { ref, inject, watch, computed, useTemplateRef } from 'vue';
import { VueFlow, Panel, MarkerType, BaseEdge, getStraightPath, useVueFlow } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import { Background } from '@vue-flow/background';
import { Controls, ControlButton } from '@vue-flow/controls';
import '@vue-flow/controls/dist/style.css';
import { MiniMap } from '@vue-flow/minimap';
import '@vue-flow/minimap/dist/style.css';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useAuthoringClient } from '@/composables/client.js';
import { describeError } from '@/composables/error-message.js';
import { showError, dismissError } from '@/composables/error-toast.js';
import { tagsToMap, mapToTags, getPosition, setPosition } from '@/authoring/DlbHeaderTags.js';
import { extractReplyLinks } from '@/authoring/DlbReplyLinks.js';
import { bodyPreview } from '@/authoring/DlbBodyPreview.js';
import { colorForNode } from '@/composables/node-colors.js';
import { isUndefinedSpeaker, projectSpeakers, speakerForNewNode } from '@/composables/project-speakers.js';
import { useLatestRequest } from '@/composables/latest-request.js';
import NodeEditPanel from './NodeEditPanel.vue';
import PreambleBar from './PreambleBar.vue';
import ContextMenu from '../widgets/ContextMenu.vue';

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

// The MiniMap paints its nodes as SVG `fill` attributes, which can't resolve `var(--...)`, so the
// Start/End accents are read from the theme once, as plain colours.
let themeNodeColors = null;

function minimapNodeColor(node) {
    if (!themeNodeColors) {
        const style = getComputedStyle(document.documentElement);
        themeNodeColors = {
            start: style.getPropertyValue('--color-node-start').trim() || '#04aa6d',
            end: style.getPropertyValue('--color-node-end').trim() || '#44403c',
        };
    }
    if (node.data?.isStart) return themeNodeColors.start;
    if (node.data?.isEnd) return themeNodeColors.end;
    return colorForNode(node.data?.nodeColor);
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

const { fitView, setCenter, getViewport, screenToFlowCoordinate, findNode } = useVueFlow();

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
            preview: bodyPreview(node.body),
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

// Saves the new position of every node that was dragged: with several nodes selected, they all
// move together, and Vue Flow reports them all in `nodes` (#196). They're saved one after another,
// so updates to the same dialogue never race each other on the server; a failure stops the rest,
// since the error is shown and a reload shows what was saved.
async function onNodeDragStop({ node, nodes }) {
    const moved = nodes?.length ? nodes : [node];
    const slug = state.value.selectedProject?.slug;
    dismissError();
    let saved = false;
    try {
        for (const movedNode of moved) {
            const rawNode = rawNodesByTitle.value.get(movedNode.id);
            if (!rawNode) continue;
            const tags = setPosition(tagsToMap(rawNode.tags),
                movedNode.position.x, movedNode.position.y);
            const updated = await client.updateDraftNode(slug, props.dialogueName, movedNode.id,
                mapToTags(tags), rawNode.body);
            rawNodesByTitle.value.set(movedNode.id, updated);
            emit('nodeChanged', movedNode.id);
            saved = true;
        }
    } catch (error) {
        showError(describeError(error));
    } finally {
        if (saved) emit('dialogueSaved');
    }
}

// ---- The MiniMap's connector lines ----
// Vue Flow's MiniMap draws nodes only. It lets a slot draw each node, in flow coordinates, in the
// order of the nodes; so the first node also draws every edge, as a line between the centres of
// its two nodes, just before its own rectangle: the lines end up under all the nodes. The nodes'
// positions are Vue Flow's own, so the lines follow a node while it's dragged.

const minimapFirstNodeId = computed(() => flowNodes.value[0]?.id ?? null);

function nodeCentre(id) {
    const node = findNode(id);
    if (!node?.dimensions?.width) return null;
    const position = node.computedPosition ?? node.position;
    return { x: position.x + node.dimensions.width / 2, y: position.y + node.dimensions.height / 2 };
}

const minimapEdgeLines = computed(() => flowEdges.value.flatMap((edge) => {
    if (edge.source === edge.target) return [];
    const from = nodeCentre(edge.source);
    const to = nodeCentre(edge.target);
    return from && to ? [{ id: edge.id, x1: from.x, y1: from.y, x2: to.x, y2: to.y }] : [];
}));

// ---- Mouse and keyboard hints (the ? button under the zoom controls) ----

const showHints = ref(false);
// Vue Flow adds to a selection with Cmd on a Mac and Ctrl elsewhere.
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform ?? '');
const multiSelectKey = isMac ? 'Cmd' : 'Ctrl';

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
    // A new node gets the project's default speaker, or else its first one (#396); in a project
    // without speakers, the one used most in this dialogue.
    const header = new Map([['title', title]]);
    const usedSpeakers = [...rawNodesByTitle.value.values()]
        .map((node) => tagsToMap(node.tags).get('speaker'));
    const speaker = speakerForNewNode(projectSpeakers.value, usedSpeakers);
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

// A right-click on the canvas opens a menu to add a node there, so a stray right-click doesn't
// make one; a double-click on the canvas adds it at once, for whoever adds many. (Not
// Shift+right-click: Firefox keeps that for its own menu.)
const paneMenu = ref(null); // { x, y } on screen, and the point in the flow it was opened at

const PANE_MENU_ITEMS = [
    { id: 'add-node', label: 'Add node here', icon: 'fa-solid fa-plus', test: 'pane-menu-add-node' },
];

function onPaneContextMenu(event) {
    event.preventDefault();
    const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY });
    paneMenu.value = { x: event.clientX, y: event.clientY, position };
}

// Vue Flow reports no double-click on the canvas itself, so it's caught here: the target is the
// canvas only on its empty part, not on a node, the map or the buttons. Vue Flow's own zoom on a
// double-click is off (zoom-on-double-click), so the two don't both happen.
function onCanvasDoubleClick(event) {
    if (!event.target?.classList?.contains('vue-flow__pane')) return;
    addNode(screenToFlowCoordinate({ x: event.clientX, y: event.clientY }));
}

function onPaneMenuSelect(item) {
    if (item.id === 'add-node' && paneMenu.value) addNode(paneMenu.value.position);
}

// ---- Node selection / content editing ----

const selectedNodeTitle = ref(null);
const selectedNode = computed(() => rawNodesByTitle.value.get(selectedNodeTitle.value) ?? null);
const nodeEditPanel = useTemplateRef('node-edit-panel');

function onNodeClick({ event, node }) {
    // A click with Shift, Ctrl or Cmd adds the node to the selection, or takes it out (Vue Flow
    // does that); it doesn't open the node for editing.
    if (event?.shiftKey || event?.ctrlKey || event?.metaKey) return;
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
    <div class="relative h-full" @dblclick="onCanvasDoubleClick">
        <div v-if="!dialogueName" class="flex items-center justify-center h-full text-grey-dark font-title text-sm">
            Open a dialogue from the Dialogue Browser to start editing.
        </div>
        <VueFlow
            v-else
            v-model:nodes="flowNodes"
            v-model:edges="flowEdges"
            class="h-full"
            :delete-key-code="false"
            :zoom-on-double-click="false"
            @node-drag-stop="onNodeDragStop"
            @node-click="onNodeClick"
            @nodes-initialized="onNodesInitialized"
            @pane-context-menu="onPaneContextMenu"
        >
            <Panel position="top-left">
                <PreambleBar ref="preamble-bar" :dialogue-name="dialogueName" @saved="emit('dialogueSaved')" />
            </Panel>

            <!-- Canvas chrome (#192): a dot grid, zoom / fit / lock buttons, and a map of the whole
                 dialogue for orientation, styled like Studio's toolbars (see the <style> below). -->
            <Background variant="dots" :gap="20" :size="1.2" pattern-color="#b4b4b4" />
            <Controls position="bottom-left" class="dlb-flow-controls" :fit-view-params="{ duration: 300 }" data-test="graph-controls">
                <ControlButton
                    title="Mouse and keyboard hints"
                    :class="{ 'dlb-control-active': showHints }"
                    data-test="graph-hints-button"
                    @click="showHints = !showHints"
                >
                    <FontAwesomeIcon icon="fa-solid fa-question" />
                </ControlButton>
            </Controls>
            <MiniMap
                position="bottom-right"
                class="dlb-flow-minimap"
                pannable
                zoomable
                :node-color="minimapNodeColor"
                :node-border-radius="4"
                mask-color="rgba(240, 240, 240, 0.6)"
                aria-label="Map of the whole dialogue"
                data-test="graph-minimap"
            >
                <template #node-dialogueNode="{ id, position, dimensions, color, borderRadius, selected }">
                    <g>
                        <g v-if="id === minimapFirstNodeId" data-test="minimap-edges">
                            <line
                                v-for="line in minimapEdgeLines"
                                :key="line.id"
                                :x1="line.x1" :y1="line.y1" :x2="line.x2" :y2="line.y2"
                                :stroke="EDGE_COLOR"
                                stroke-width="0.75"
                                stroke-opacity="0.6"
                                vector-effect="non-scaling-stroke"
                            />
                        </g>
                        <rect
                            :x="position.x" :y="position.y"
                            :width="dimensions.width" :height="dimensions.height"
                            :rx="borderRadius" :ry="borderRadius"
                            :fill="color"
                            :stroke="selected ? EDGE_COLOR : null"
                            :stroke-width="selected ? 2 : null"
                            vector-effect="non-scaling-stroke"
                        />
                    </g>
                </template>
            </MiniMap>

            <template #node-dialogueNode="{ data, selected }">
                <div
                    class="rounded-lg shadow bg-white min-w-[150px] max-w-[210px] overflow-hidden cursor-pointer hover:shadow-md"
                    :class="{
                        'ring-2 ring-offset-1 ring-node-start': data.isStart,
                        'ring-2 ring-offset-1 ring-node-end': data.isEnd,
                        'dlb-node-selected': selected,
                    }"
                    :data-selected="selected ? 'true' : null"
                >
                    <div class="px-3 py-1.5 text-xs font-title font-bold text-white truncate flex items-center gap-1.5" :style="{ backgroundColor: nodeHeaderColor(data) }">
                        <FontAwesomeIcon v-if="data.isStart" icon="fa-solid fa-play" class="text-[10px] shrink-0" title="Start node — the dialogue's entry point" />
                        <FontAwesomeIcon v-if="data.isEnd" icon="fa-solid fa-flag-checkered" class="text-[10px] shrink-0" title="End node — terminates the dialogue" />
                        <span class="truncate">{{ data.title }}</span>
                        <span v-if="data.comment" class="ml-auto shrink-0 cursor-help" :title="data.comment" data-test="node-comment-icon">
                            <FontAwesomeIcon icon="fa-regular fa-comment" class="text-[11px]" />
                        </span>
                    </div>
                    <div v-if="data.speaker || data.preview || data.externalLinks" class="px-3 py-2 text-xs font-title">
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
                        <div v-if="data.preview" class="mt-1 text-[11px] leading-snug text-black/70 line-clamp-2 break-words" data-test="node-preview">{{ data.preview }}</div>
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

        <div
            v-if="dialogueName && showHints"
            class="absolute left-12 bottom-3 z-10 w-80 rounded border border-grey-light bg-white shadow-md text-xs font-title"
            role="dialog"
            aria-label="Mouse and keyboard hints"
            data-test="graph-hints"
        >
            <div class="flex items-center justify-between px-3 py-1.5 bg-grey-lighter border-b border-grey-light rounded-t">
                <span class="font-bold">Mouse and keyboard</span>
                <button type="button" class="text-grey-dark hover:text-black" title="Close" data-test="graph-hints-close" @click="showHints = false">
                    <FontAwesomeIcon icon="fa-solid fa-xmark" />
                </button>
            </div>
            <dl class="dlb-hints px-3 py-2">
                <dt>Click a node</dt><dd>Edit it</dd>
                <dt>Drag a node</dt><dd>Move it</dd>
                <dt><kbd>Shift</kbd> + drag the canvas</dt><dd>Select the nodes in the box</dd>
                <dt><kbd>{{ multiSelectKey }}</kbd> + click a node</dt><dd>Add it to the selection, or take it out</dd>
                <dt>Drag a selected node</dt><dd>Move all selected nodes together</dd>
                <dt>Click the canvas</dt><dd>Clear the selection</dd>
                <dt>Right-click the canvas</dt><dd>A menu to add a node there</dd>
                <dt>Double-click the canvas</dt><dd>Add a node there at once</dd>
                <dt>Drag the canvas</dt><dd>Move around</dd>
                <dt>Scroll</dt><dd>Zoom</dd>
                <dt>Drag or scroll in the map</dt><dd>Move around, zoom</dd>
            </dl>
        </div>

        <ContextMenu
            v-if="paneMenu"
            :items="PANE_MENU_ITEMS"
            :x="paneMenu.x"
            :y="paneMenu.y"
            @select="onPaneMenuSelect"
            @close="paneMenu = null"
        />

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

<style scoped>
/* Selecting several nodes (Shift+drag a box, or Shift/Ctrl/Cmd+click): Studio imports only Vue
   Flow's structural CSS, not its theme, so without these the box and the selected nodes are
   invisible. Orange, as Studio's lines and highlights are. */
:deep(.vue-flow__selection) {
    background: rgba(246, 203, 139, 0.18); /* --color-orange-light */
    border: 1px dashed var(--color-orange-dark);
    border-radius: 4px;
}

:deep(.vue-flow__selection:focus),
:deep(.vue-flow__selection:focus-visible) {
    outline: none;
}

/* After a box selection Vue Flow leaves a rectangle around the selected nodes, to drag them by.
   It's hidden: the nodes' own outlines show the selection, dragging any selected node moves them
   all (and is saved, onNodeDragStop), while a drag on that rectangle would be a selection drag,
   which this editor doesn't save. */
:deep(.vue-flow__nodesselection-rect) {
    display: none;
}

/* An outline rather than a ring, so it shows next to the Start and End nodes' own rings. */
.dlb-node-selected {
    outline: 2px solid var(--color-orange-dark);
    outline-offset: 3px;
}

/* Vue Flow's Controls and MiniMap, in the look of Studio's toolbar controls (see "UI building
   blocks" in CLAUDE.md): white, grey border, rounded corners, 24px buttons. */
:deep(.dlb-flow-controls) {
    box-shadow: none;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

:deep(.dlb-flow-controls .vue-flow__controls-button) {
    box-sizing: border-box;
    width: 24px;
    height: 24px;
    padding: 0;
    background: white;
    border: 1px solid var(--color-grey-light);
    border-radius: 4px;
    color: var(--color-orange-darker);
}

:deep(.dlb-flow-controls .vue-flow__controls-button svg) {
    max-width: 11px;
    max-height: 11px;
    fill: currentColor;
}

:deep(.dlb-flow-controls .vue-flow__controls-button:hover) {
    background: var(--color-grey-lighter);
}

:deep(.dlb-flow-controls .vue-flow__controls-button.dlb-control-active) {
    background: var(--color-orange-dark);
    border-color: var(--color-orange-dark);
    color: white;
}

.dlb-hints {
    display: grid;
    grid-template-columns: auto 1fr;
    column-gap: 12px;
    row-gap: 4px;
}

.dlb-hints dt {
    color: var(--color-orange-darker);
    white-space: nowrap;
}

.dlb-hints kbd {
    display: inline-block;
    padding: 0 4px;
    border: 1px solid var(--color-grey-light);
    border-bottom-width: 2px;
    border-radius: 3px;
    background: var(--color-grey-lighter);
    font-family: inherit;
    font-size: 10px;
    line-height: 14px;
}

:deep(.dlb-flow-minimap) {
    background: white;
    border: 1px solid var(--color-grey-light);
    border-radius: 4px;
    overflow: hidden;
}
</style>
