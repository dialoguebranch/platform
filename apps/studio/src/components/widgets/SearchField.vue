<script setup>
import { computed, useAttrs } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// A search field with a magnifying glass in front of the text, for the management pages. Its
// width comes from the parent (a class on the component, applied to the wrapper); every other
// attribute, such as data-test, goes to the input itself.

defineOptions({ inheritAttrs: false });
const attrs = useAttrs();
const inputAttrs = computed(() => {
    const { class: _class, ...rest } = attrs;
    return rest;
});

const query = defineModel({ type: String, default: '' });

defineProps({
    placeholder: { type: String, default: 'Search…' },
});
</script>

<template>
    <div class="relative" :class="attrs.class">
        <FontAwesomeIcon
            icon="fa-solid fa-magnifying-glass"
            class="absolute left-3 top-1/2 -translate-y-1/2 text-xs text-orange-darker pointer-events-none"
        />
        <input
            v-model="query"
            type="search"
            class="w-full bg-white pl-8 pr-3 py-1.5 rounded-md border border-orange-darker/40 font-title text-sm focus:outline-none focus:border-orange-darker"
            :placeholder="placeholder"
            v-bind="inputAttrs"
        />
    </div>
</template>
