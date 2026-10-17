<script setup>
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

// The greeting in PageHeader: a speech-bubble icon and the text in the default slot. From tablet
// width up the icon hangs in the margin, so the text lines up with the edge of the content below
// it; on a phone, where that margin is narrow, it stays in line.
</script>

<template>
    <span class="relative flex items-start gap-1.5 sm:block" data-test="greeting">
        <FontAwesomeIcon icon="fa-regular fa-comment" class="shrink-0 mt-0.5 text-orange-darker sm:absolute sm:-left-5 sm:top-0" />
        <span><slot /></span>
    </span>
</template>
