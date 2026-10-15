<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import ContextMenuList from './ContextMenuList.vue';

// A context menu: a list of actions, opened at the pointer (right-click) or below an element
// (a "⋯" button), with submenus. The parent shows it with v-if and removes it on `close`; choosing
// an item emits `select` with that item, and then `close`.
//
// Items: { label, icon?, danger?, disabled?, title?, test?, children? } or { separator: true }.
// `icon` is a Font Awesome class string; `danger` marks a destructive action; `test` becomes the
// item's data-test attribute; `children` makes it a submenu.
//
// Keyboard: the first item is focused on opening; ↑/↓ (Home/End) move, → or Enter opens a
// submenu, ← or Escape closes it, Enter or Space chooses, Escape closes the menu (focus returns to
// what had it before), Tab closes it. It also closes on a click outside it, and when the window
// scrolls or resizes, since it would no longer line up with what it was opened for.

const props = defineProps({
    items: { type: Array, required: true },
    // Open at this point (viewport coordinates), for a right-click.
    x: { type: Number, default: null },
    y: { type: Number, default: null },
    // Or open below this element, for a menu button.
    anchor: { type: Object, default: null },
    zIndex: { type: Number, default: 10050 },
});

const emit = defineEmits(['select', 'close']);

const container = ref(null);
const place = props.anchor
    ? { below: props.anchor.getBoundingClientRect() }
    : { x: props.x ?? 0, y: props.y ?? 0 };
// Focus goes back here when the menu is left with the keyboard or an item is chosen.
const previousFocus = document.activeElement;

function close({ restoreFocus }) {
    emit('close');
    if (restoreFocus && previousFocus?.isConnected) previousFocus.focus();
}

// An item chosen with Enter or Space is chosen on keydown; the matching keyup then lands on
// whatever has the focus next, such as a field the chosen action opened (which would submit on
// Enter) or the trigger getting the focus back (which Space would press again). Swallow it.
function swallowActivationKeyup() {
    const handler = (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            event.stopPropagation();
        }
        cleanup();
    };
    const cleanup = () => {
        window.removeEventListener('keyup', handler, true);
        clearTimeout(timer);
    };
    window.addEventListener('keyup', handler, true);
    const timer = setTimeout(cleanup, 1000);
}

function select(item) {
    swallowActivationKeyup();
    emit('select', item);
    close({ restoreFocus: true });
}

function onPointerDownOutside(event) {
    if (!container.value?.contains(event.target)) close({ restoreFocus: false });
}

function onViewportChange(event) {
    if (event.type === 'scroll' && container.value?.contains(event.target)) return;
    close({ restoreFocus: false });
}

onMounted(() => {
    document.addEventListener('pointerdown', onPointerDownOutside, true);
    window.addEventListener('scroll', onViewportChange, true);
    window.addEventListener('resize', onViewportChange);
});

onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', onPointerDownOutside, true);
    window.removeEventListener('scroll', onViewportChange, true);
    window.removeEventListener('resize', onViewportChange);
});
</script>

<template>
    <Teleport to="body">
        <div ref="container" class="fixed" :style="{ zIndex }" data-test="context-menu">
            <ContextMenuList :items="items" :place="place" @select="select" @close="close" />
        </div>
    </Teleport>
</template>
