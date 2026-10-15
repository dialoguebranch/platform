<script setup>
import { nextTick, onMounted, ref } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// One level of a ContextMenu (see ContextMenu.vue, the widget to use): renders its items, places
// itself inside the window, handles keyboard navigation within the level, and opens an item's
// submenu as a nested ContextMenuList beside it.

const props = defineProps({
    items: { type: Array, required: true },
    // Where to open: { x, y } (a point), { below: DOMRect } (under an element) or
    // { beside: DOMRect } (next to a parent item, for a submenu).
    place: { type: Object, required: true },
    // Focus the first item when opening (keyboard use, or the top level).
    autofocus: { type: Boolean, default: true },
    level: { type: Number, default: 0 },
});

const emit = defineEmits(['select', 'back', 'close']);

const MARGIN = 8;

const root = ref(null);
const position = ref({ left: 0, top: 0 });
const placed = ref(false);
const openIndex = ref(-1);
const openWithFocus = ref(false);
const itemButtons = ref([]);

const isEnabled = (item) => item && !item.separator && !item.disabled;

function place() {
    const rect = root.value.getBoundingClientRect();
    const maxLeft = window.innerWidth - rect.width - MARGIN;
    const maxTop = window.innerHeight - rect.height - MARGIN;
    let left;
    let top;
    if (props.place.beside) {
        const parent = props.place.beside;
        left = parent.right + rect.width + MARGIN <= window.innerWidth ? parent.right : parent.left - rect.width;
        top = parent.top - 4;
    } else if (props.place.below) {
        const anchor = props.place.below;
        left = anchor.left;
        top = anchor.bottom + rect.height + MARGIN <= window.innerHeight ? anchor.bottom : anchor.top - rect.height;
    } else {
        left = props.place.x + rect.width + MARGIN <= window.innerWidth ? props.place.x : props.place.x - rect.width;
        top = props.place.y + rect.height + MARGIN <= window.innerHeight ? props.place.y : props.place.y - rect.height;
    }
    position.value = {
        left: Math.max(MARGIN, Math.min(left, maxLeft)),
        top: Math.max(MARGIN, Math.min(top, maxTop)),
    };
    placed.value = true;
}

function focusItem(index) {
    itemButtons.value[index]?.focus();
}

function enabledIndexes() {
    return props.items.map((item, i) => (isEnabled(item) ? i : -1)).filter((i) => i >= 0);
}

onMounted(async () => {
    // Measured while still invisible, then shown; focus only once it is visible, since a
    // browser doesn't focus a hidden element.
    await nextTick();
    place();
    await nextTick();
    if (props.autofocus) focusItem(enabledIndexes()[0]);
});

function openSubmenu(index, withFocus) {
    openWithFocus.value = withFocus;
    openIndex.value = index;
}

function activate(index, withFocus) {
    const item = props.items[index];
    if (!isEnabled(item)) return;
    if (item.children) openSubmenu(index, withFocus);
    else emit('select', item);
}

function onPointerEnter(index) {
    const item = props.items[index];
    if (isEnabled(item) && item.children) openSubmenu(index, false);
    else openIndex.value = -1;
}

function onKeydown(event, index) {
    const enabled = enabledIndexes();
    const at = enabled.indexOf(index);
    switch (event.key) {
    case 'ArrowDown':
        focusItem(enabled[(at + 1) % enabled.length]);
        break;
    case 'ArrowUp':
        focusItem(enabled[(at - 1 + enabled.length) % enabled.length]);
        break;
    case 'Home':
        focusItem(enabled[0]);
        break;
    case 'End':
        focusItem(enabled[enabled.length - 1]);
        break;
    case 'ArrowRight':
        if (props.items[index].children) activate(index, true);
        break;
    case 'Enter':
    case ' ':
        activate(index, true);
        break;
    case 'ArrowLeft':
        if (props.level > 0) emit('back');
        break;
    case 'Escape':
        if (props.level > 0) emit('back');
        else emit('close', { restoreFocus: true });
        break;
    case 'Tab':
        emit('close', { restoreFocus: false });
        break;
    default:
        return;
    }
    event.preventDefault();
    event.stopPropagation();
}

function onSubmenuBack() {
    const index = openIndex.value;
    openIndex.value = -1;
    focusItem(index);
}

function itemRect(index) {
    return itemButtons.value[index]?.getBoundingClientRect();
}

function itemKey(item, index) {
    return item.id ?? (item.separator ? `separator-${index}` : item.label);
}
</script>

<template>
    <div
        ref="root"
        role="menu"
        class="fixed w-56 bg-white rounded-lg shadow-2xl border border-grey-light py-1 font-title text-sm"
        :style="{ left: position.left + 'px', top: position.top + 'px', visibility: placed ? 'visible' : 'hidden' }"
        @contextmenu.prevent
    >
        <template v-for="(item, index) in items" :key="itemKey(item, index)">
            <div v-if="item.separator" role="separator" class="my-1 border-t border-grey-light"></div>
            <div v-else>
                <button
                    :ref="(el) => { itemButtons[index] = el; }"
                    type="button"
                    role="menuitem"
                    tabindex="-1"
                    :title="item.title"
                    :disabled="item.disabled"
                    :aria-haspopup="item.children ? 'menu' : undefined"
                    :aria-expanded="item.children ? openIndex === index : undefined"
                    :data-test="item.test"
                    :class="[
                        'flex items-center gap-2 w-full px-3 py-1.5 text-left focus:outline-none',
                        item.disabled ? 'text-grey-light cursor-not-allowed'
                            : item.danger ? 'text-red-dark hover:bg-red-dark/10 focus:bg-red-dark/10 cursor-pointer'
                                : 'text-grey-darker hover:bg-orange-light/20 hover:text-orange-darker focus:bg-orange-light/20 focus:text-orange-darker cursor-pointer',
                        { 'bg-orange-light/20 text-orange-darker': openIndex === index },
                    ]"
                    @click="activate(index, false)"
                    @pointerenter="onPointerEnter(index)"
                    @keydown="onKeydown($event, index)"
                >
                    <FontAwesomeIcon v-if="item.icon" :icon="item.icon" class="w-4 shrink-0" />
                    <span class="flex-1 truncate">{{ item.label }}</span>
                    <FontAwesomeIcon v-if="item.children" icon="fa-solid fa-caret-right" class="text-[10px]" />
                </button>
                <ContextMenuList
                    v-if="item.children && openIndex === index"
                    :items="item.children"
                    :place="{ beside: itemRect(index) }"
                    :autofocus="openWithFocus"
                    :level="level + 1"
                    @select="(selected) => emit('select', selected)"
                    @back="onSubmenuBack"
                    @close="(how) => emit('close', how)"
                />
            </div>
        </template>
    </div>
</template>
