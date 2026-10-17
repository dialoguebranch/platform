<script setup>
// The white box of a main page panel. A panel with a toolbar (filter and sort controls) puts it in
// the box's first row, like the Dialogue Workspace's tab strip, so the three panels' boxes start at
// the same height: only the content below the toolbar scrolls.
defineProps({
    // Classes for the scrolling content area, when there's a toolbar.
    bodyClass: { type: [String, Array, Object], default: '' },
});
</script>

<template>
    <div v-if="$slots.toolbar" class="basis-0 grow min-h-0 mx-1 sm:mx-0 mb-2 bg-white shadow-[0_0_1px_black] flex flex-col">
        <div class="shrink-0 bg-grey-lighter border-b border-grey-light">
            <slot name="toolbar" />
        </div>
        <div class="basis-0 grow min-h-0 overflow-y-auto" :class="bodyClass">
            <slot />
        </div>
    </div>
    <div v-else class="basis-0 grow overflow-y-auto mx-1 sm:mx-0 mb-2 bg-white shadow-[0_0_1px_black]">
        <slot />
    </div>
</template>
