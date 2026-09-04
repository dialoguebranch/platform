<script setup>
// Renders one resolved <<action>> (an Action, from an ACTION statement segment). Four types:
//   link    – an inline anchor (label = the `text` parameter, or the value)
//   image   – an <img>
//   video   – a <video> for a direct media URL, otherwise a labelled link
//   generic – a small chip showing the value (a client hooks the actual behaviour); parameters
//             are shown on hover. Callers that need to act on it should watch the debug log or,
//             on the participant page, the `dlb-action` CustomEvent.
import { computed } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const props = defineProps({
    action: { type: Object, required: true },
});

const isHttpUrl = computed(() => /^https?:\/\//i.test(props.action.value ?? ''));
const isMediaUrl = computed(() =>
    isHttpUrl.value && /\.(mp4|webm|ogg|ogv|mov)(\?.*)?$/i.test(props.action.value));

const linkLabel = computed(() => props.action.parameter('text') ?? props.action.value);

const genericTitle = computed(() => {
    const params = props.action.parameters ?? {};
    const entries = Object.entries(params);
    return entries.length
        ? entries.map(([k, v]) => `${k}=${v}`).join(', ')
        : 'generic action';
});
</script>

<template>
    <a
        v-if="action.type === 'link'"
        :href="action.value"
        target="_blank"
        rel="noopener noreferrer"
        class="underline decoration-dotted underline-offset-2 hover:opacity-80"
    >{{ linkLabel }}</a>

    <img
        v-else-if="action.type === 'image'"
        :src="action.value"
        :alt="action.parameter('text') ?? action.value"
        class="my-2 block max-h-64 max-w-full rounded"
    />

    <video
        v-else-if="action.type === 'video' && isMediaUrl"
        :src="action.value"
        controls
        class="my-2 block max-h-64 max-w-full rounded"
    ></video>

    <a
        v-else-if="action.type === 'video'"
        :href="action.value"
        target="_blank"
        rel="noopener noreferrer"
        class="my-2 inline-flex items-center gap-2 rounded border border-current px-2 py-1 text-sm hover:opacity-80"
    >
        <FontAwesomeIcon icon="fa-solid fa-play" />{{ linkLabel }}
    </a>

    <span
        v-else
        :title="genericTitle"
        class="my-1 inline-flex items-center gap-1.5 rounded bg-black/10 px-2 py-0.5 font-mono text-xs align-middle"
    >
        <FontAwesomeIcon icon="fa-solid fa-bolt" class="opacity-60" />{{ action.value }}
    </span>
</template>
