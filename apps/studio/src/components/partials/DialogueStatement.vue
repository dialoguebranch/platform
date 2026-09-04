<script setup>
// Renders an agent statement. When it is plain text (the common case) this is a single sanitized
// v-html block, identical to before. When it also carries <<action>> segments, the segments are
// rendered in order: each text run through statementToHtml, each action through
// DialogueActionSegment.
import { computed } from 'vue';
import { statementToHtml } from '@/composables/sanitize-html.js';
import DialogueActionSegment from '../widgets/DialogueActionSegment.vue';

const props = defineProps({
    // A dlb-lib Statement.
    statement: { type: Object, required: true },
});

const hasAction = computed(() => props.statement.hasSegmentOfType?.('ACTION') ?? false);
const segments = computed(() => props.statement.segments ?? []);
</script>

<template>
    <div v-if="!hasAction" v-html="statementToHtml(statement.fullStatement())"></div>
    <div v-else class="space-y-2">
        <template v-for="(segment, index) in segments" :key="index">
            <div
                v-if="segment.type === 'TEXT' && segment.text && segment.text.trim()"
                v-html="statementToHtml(segment.text)"
            ></div>
            <DialogueActionSegment
                v-else-if="segment.type === 'ACTION' && segment.action"
                :action="segment.action"
            />
        </template>
    </div>
</template>
