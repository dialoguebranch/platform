<script setup>
import { ref, onMounted, onUnmounted, useTemplateRef } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { DocumentFunctions } from '../../authoring/DocumentFunctions.js';

const props = defineProps({
    'cookiePrefix': String,
    'mobileTabNames': Array,
    'leftPanelLabel': {
        type: String,
        default: 'Panel',
    },
    'rightPanelLabel': {
        type: String,
        default: 'Panel',
    },
});

const emit = defineEmits(['resize']);

const selectedMobileTab = ref(1);

const selectMobileTab = (index) => {
    selectedMobileTab.value = index;
};

const minPanelWidth = 200;
const leftPanelWidth = ref(200);
const rightPanelWidth = ref(200);
// Either side panel can be collapsed to a narrow strip that opens it again; remembered per side.
const collapsed = ref({ left: false, right: false });

function collapsedCookie(side) {
    return 'state.' + props.cookiePrefix + (side === 'left' ? 'Left' : 'Right') + 'PanelCollapsed';
}

function initCollapsedState() {
    for (const side of ['left', 'right']) {
        collapsed.value[side] = DocumentFunctions.getCookie(collapsedCookie(side)) === 'true';
    }
}

function setCollapsed(side, value) {
    if (collapsed.value[side] === value) return;
    collapsed.value[side] = value;
    DocumentFunctions.setCookie(collapsedCookie(side), value.toString(), 365);
    reduceSideWidthsToFit();
}

function collapsePanel(side) {
    setCollapsed(side, true);
}

function expandPanel(side) {
    setCollapsed(side, false);
}

defineExpose({
    selectMobileTab,
    minPanelWidth,
    collapsed,
    collapsePanel,
    expandPanel,
});

const root = useTemplateRef('root');

const dividerWidth = 8;
var resizeListener;
var dragResizeState;

onMounted(() => {
    initCollapsedState();
    initPanelWidths();
    clearDragResizeState();
    resizeListener = () => reduceSideWidthsToFit();
    addEventListener('resize', resizeListener);
});

onUnmounted(() => {
    removeEventListener('resize', resizeListener);
});

function initPanelWidths() {
    let leftWidth = DocumentFunctions.getCookie('state.' + props.cookiePrefix + 'LeftPanelWidth');
    if (leftWidth) {
        leftPanelWidth.value = Math.max(minPanelWidth, parseInt(leftWidth));
    }
    let rightWidth = DocumentFunctions.getCookie('state.' + props.cookiePrefix + 'RightPanelWidth');
    if (rightWidth) {
        rightPanelWidth.value = Math.max(minPanelWidth, parseInt(rightWidth));
    }
    reduceSideWidthsToFit();
    // always save the widths again so the cookies stay valid
    saveLeftPanelWidth();
    saveRightPanelWidth();
}

function saveLeftPanelWidth() {
    DocumentFunctions.setCookie('state.' + props.cookiePrefix + 'LeftPanelWidth', leftPanelWidth.value.toString(), 365);
}

function saveRightPanelWidth() {
    DocumentFunctions.setCookie('state.' + props.cookiePrefix + 'RightPanelWidth', rightPanelWidth.value.toString(), 365);
}

function clearDragResizeState() {
    dragResizeState = {
        left: {
            dragging: false,
            startX: 0,
            startWidth: 0,
        },
        right: {
            dragging: false,
            startX: 0,
            startWidth: 0,
        },
    };
}

function stopDragResize() {
    if (!dragResizeState) {
        return;
    }
    if (dragResizeState.left.dragging) {
        saveLeftPanelWidth();
    } else if (dragResizeState.right.dragging) {
        saveRightPanelWidth();
    }
    clearDragResizeState();
}

function onMouseDownLeftDivider(event) {
    if (!dragResizeState) {
        return;
    }
    dragResizeState.left.dragging = true;
    dragResizeState.left.startX = event.pageX;
    dragResizeState.left.startWidth = leftPanelWidth.value;
}

function onMouseDownRightDivider(event) {
    if (!dragResizeState) {
        return;
    }
    dragResizeState.right.dragging = true;
    dragResizeState.right.startX = event.pageX;
    dragResizeState.right.startWidth = rightPanelWidth.value;
}

function onMouseMove(event) {
    if (!dragResizeState) {
        return;
    }
    if (dragResizeState.left.dragging) {
        onDragLeftDivider(event);
    } else if (dragResizeState.right.dragging) {
        onDragRightDivider(event);
    }
}

// The width a side panel takes up: none while collapsed.
function sideWidth(side) {
    if (collapsed.value[side]) return 0;
    return side === 'left' ? leftPanelWidth.value : rightPanelWidth.value;
}

function dividerCount() {
    return (collapsed.value.left ? 0 : 1) + (collapsed.value.right ? 0 : 1);
}

function onDragLeftDivider(event) {
    const leftSpace = root.value.clientWidth - dividerCount() * dividerWidth - minPanelWidth -
        sideWidth('right');
    if (leftSpace < minPanelWidth)
        return;
    const prefWidth = dragResizeState.left.startWidth + event.x - dragResizeState.left.startX;
    leftPanelWidth.value = Math.max(minPanelWidth, Math.min(leftSpace, prefWidth));
    emitResize();
}

function onDragRightDivider(event) {
    const rightSpace = root.value.clientWidth - dividerCount() * dividerWidth - minPanelWidth -
        sideWidth('left');
    if (rightSpace < minPanelWidth)
        return;
    const prefWidth = dragResizeState.right.startWidth + dragResizeState.right.startX - event.x;
    rightPanelWidth.value = Math.max(minPanelWidth, Math.min(rightSpace, prefWidth));
    emitResize();
}

function reduceSideWidthsToFit() {
    if (collapsed.value.left || collapsed.value.right) {
        // At most one side panel is open: it may take all the space but the main panel's minimum.
        const open = collapsed.value.left ? 'right' : 'left';
        if (!collapsed.value[open]) {
            const width = open === 'left' ? leftPanelWidth : rightPanelWidth;
            const maxWidth = root.value.clientWidth - dividerWidth - minPanelWidth;
            const fitted = Math.max(minPanelWidth, Math.min(maxWidth, width.value));
            if (fitted !== width.value) {
                width.value = fitted;
                if (open === 'left') saveLeftPanelWidth(); else saveRightPanelWidth();
            }
        }
        emitResize();
        return;
    }
    const sideSpace = root.value.clientWidth - 2 * dividerWidth - minPanelWidth;
    let leftWidth = leftPanelWidth.value;
    let rightWidth = rightPanelWidth.value;
    const reduce = leftWidth + rightWidth - sideSpace;
    if (reduce > 0) {
        // left and side panel now take more space than available:
        // try to reduce their sizes proportionally
        const prefReduceLeft = Math.round(reduce * leftWidth / (leftWidth + rightWidth));
        const prefLeftWidth = leftWidth - prefReduceLeft;
        // set leftWidth to prefLeftWidth, but keep enough space for the right panel, and never go
        // under minPanelWidth
        leftWidth = Math.max(minPanelWidth, Math.min(sideSpace - minPanelWidth, prefLeftWidth));
        // set rightWidth to remaining space, and never go under minPanelWidth
        rightWidth = Math.max(minPanelWidth, sideSpace - leftWidth);
        if (leftWidth != leftPanelWidth.value) {
            leftPanelWidth.value = leftWidth;
            saveLeftPanelWidth();
        }
        if (rightWidth != rightPanelWidth.value) {
            rightPanelWidth.value = rightWidth;
            saveRightPanelWidth();
        }
    }
    emitResize();
}

function emitResize() {
    setTimeout(() => {
        emit('resize');
    });
}

// A collapsed side panel's strip. On a wide screen it lines up with the panels' white boxes: below
// their title rows (4px + 30px + 4px, less 1px to match their outline; see MainPagePanelHeader) and
// above their 8px bottom margin.
const collapsedStripClasses = 'sm:flex basis-0 grow sm:basis-auto sm:flex-none sm:w-7 sm:mt-[37px] sm:mb-2 '
    + 'flex-col items-center justify-center gap-2 py-3 bg-grey-lighter hover:bg-menu-item-highlight '
    + 'border border-grey-light shadow-[0_0_1px_black] text-orange-darker cursor-pointer';

function getMobilePanelClasses(index) {
    return {
        'hidden': selectedMobileTab.value !== index,
        'flex': selectedMobileTab.value === index,
    }
}
</script>

<template>
    <div class="flex flex-col">
        <div class="grid grid-cols-3 bg-background space-x-px sm:hidden">
            <button
                v-for="(tab, index) in mobileTabNames"
                class="font-title font-medium text-sm px-4 py-2 text-center cursor-pointer"
                :class="{
                    'bg-white': selectedMobileTab != index,
                    'hover:bg-menu-item-highlight': selectedMobileTab != index,
                }"
                @click="selectMobileTab(index)"
            >
                {{ tab }}
            </button>
        </div>
        <div ref="root" class="grow bg-background flex" @mousemove.prevent="onMouseMove" @mouseup.prevent="stopDragResize" @mouseleave.prevent="stopDragResize">
            <template v-if="!collapsed.left">
                <div
                    :class="getMobilePanelClasses(0)"
                    class="sm:flex basis-0 grow sm:basis-auto sm:flex-none overflow-x-hidden flex-col"
                    :style="{width: leftPanelWidth + 'px'}"
                >
                    <slot name="left" />
                </div>
                <div class="cursor-col-resize hidden sm:block" :style="{width: dividerWidth + 'px'}" @mousedown.prevent="onMouseDownLeftDivider"></div>
            </template>
            <button
                v-else
                type="button"
                :class="[getMobilePanelClasses(0), collapsedStripClasses, 'sm:mr-2']"
                :title="`Show ${leftPanelLabel}`"
                data-test="expand-left-panel"
                @click="expandPanel('left')"
            >
                <FontAwesomeIcon icon="fa-solid fa-angles-right" class="text-xs" />
                <span class="hidden sm:block font-title text-xs [writing-mode:vertical-rl] rotate-180">{{ leftPanelLabel }}</span>
                <span class="sm:hidden font-title text-sm">Show {{ leftPanelLabel }}</span>
            </button>
            <div
                :class="getMobilePanelClasses(1)"
                class="sm:flex basis-0 grow overflow-x-hidden flex-col"
            >
                <slot name="main" />
            </div>
            <template v-if="!collapsed.right">
                <div class="cursor-col-resize hidden sm:block" :style="{width: dividerWidth + 'px'}" @mousedown.prevent="onMouseDownRightDivider"></div>
                <div
                    :class="getMobilePanelClasses(2)"
                    class="sm:flex basis-0 grow sm:basis-auto sm:flex-none overflow-x-hidden flex-col"
                    :style="{width: rightPanelWidth + 'px'}"
                >
                    <slot name="right" />
                </div>
            </template>
            <button
                v-else
                type="button"
                :class="[getMobilePanelClasses(2), collapsedStripClasses, 'sm:ml-2']"
                :title="`Show ${rightPanelLabel}`"
                data-test="expand-right-panel"
                @click="expandPanel('right')"
            >
                <FontAwesomeIcon icon="fa-solid fa-angles-left" class="text-xs" />
                <span class="hidden sm:block font-title text-xs [writing-mode:vertical-rl] rotate-180">{{ rightPanelLabel }}</span>
                <span class="sm:hidden font-title text-sm">Show {{ rightPanelLabel }}</span>
            </button>
        </div>
    </div>
</template>
