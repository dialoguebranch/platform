<script setup>
import { ref, computed, watchEffect, nextTick } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { debugLog } from '../../composables/debug-log.js';

const open = ref(false);
const showApi = ref(true);
const showEvents = ref(true);
const expanded = ref(new Set());
const logBody = ref(null);

const entries = computed(() => {
    return [...debugLog.value]
        .filter(e => (e.type === 'api' && showApi.value) || (e.type === 'event' && showEvents.value));
});

watchEffect(() => {
    // track entries so this re-runs when the list changes
    entries.value;
    if (logBody.value) {
        nextTick(() => { logBody.value.scrollTop = logBody.value.scrollHeight; });
    }
});

function toggleEntry(id, event) {
    const next = new Set(expanded.value);
    const expanding = !next.has(id);
    if (expanding) {
        next.add(id);
        expanded.value = next;
        nextTick(() => { logBody.value.scrollTop = logBody.value.scrollHeight; });
    } else {
        const row = event.currentTarget;
        next.delete(id);
        expanded.value = next;
        nextTick(() => { row.scrollIntoView({ block: 'nearest' }); });
    }
}

function clearLog() {
    debugLog.value = [];
    expanded.value = new Set();
}

function formatTime(date) {
    const h = String(date.getHours()).padStart(2, '0');
    const m = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    const ms = String(date.getMilliseconds()).padStart(3, '0');
    return `${h}:${m}:${s}.${ms}`;
}

function prettyBody(raw) {
    if (!raw) return null;
    try {
        return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
        return raw;
    }
}

function statusClass(status) {
    if (status >= 200 && status < 300) return 'text-icon-button';
    if (status >= 400 && status < 500) return 'text-orange-dark';
    return 'text-red-dark';
}
</script>

<template>
    <!-- Toggle button -->
    <button
        class="fixed bottom-4 right-4 z-50 rounded-full w-7.5 h-7.5 bg-icon-button hover:bg-icon-button-hover cursor-pointer"
        @click="open = !open"
        title="Debug window"
    >
        <FontAwesomeIcon icon="fa-solid fa-bug" class="text-white" />
    </button>

    <!-- Panel -->
    <div
        v-if="open"
        class="fixed bottom-4 right-[60px] z-50 w-[720px] h-[250px] flex flex-col bg-white shadow-[0_0_4px_black] overflow-hidden text-xs"
    >
        <!-- Header -->
        <div class="flex items-center gap-2 px-3 py-1.5 bg-box text-white shrink-0">
            <FontAwesomeIcon icon="fa-solid fa-bug" />
            <span class="font-title font-bold">Debug Log</span>
            <span class="text-orange-light text-[11px]">({{ debugLog.length }} entries)</span>

            <div class="grow"></div>

            <!-- Filter toggles -->
            <button
                :class="['px-2 py-0.5 rounded text-[11px] border transition-colors cursor-pointer font-title', showApi ? 'bg-orange-dark border-orange-medium text-white' : 'bg-transparent border-orange-medium text-orange-light hover:bg-orange-darker']"
                @click="showApi = !showApi"
            >API Calls</button>
            <button
                :class="['px-2 py-0.5 rounded text-[11px] border transition-colors cursor-pointer font-title', showEvents ? 'bg-orange-dark border-orange-medium text-white' : 'bg-transparent border-orange-medium text-orange-light hover:bg-orange-darker']"
                @click="showEvents = !showEvents"
            >Events</button>

            <button
                class="ml-1 px-2 py-0.5 rounded text-[11px] border border-red-dark text-red-dark hover:bg-red-dark hover:text-white cursor-pointer font-title flex items-center gap-1"
                @click="clearLog"
            ><FontAwesomeIcon icon="fa-solid fa-xmark" />Clear</button>

            <button
                class="ml-1 text-orange-light hover:text-white cursor-pointer"
                @click="open = false"
            ><FontAwesomeIcon icon="fa-solid fa-xmark" /></button>
        </div>

        <!-- Log entries -->
        <div ref="logBody" class="overflow-y-auto grow font-mono">
            <div v-if="entries.length === 0" class="p-4 text-text-subtle text-center italic">No entries.</div>

            <div
                v-for="entry in entries"
                :key="entry.id"
                class="border-b border-grey-lighter"
            >
                <!-- Entry header row -->
                <div
                    class="flex items-baseline gap-2 px-3 py-1 hover:bg-grey-lighter cursor-pointer select-none"
                    @click="toggleEntry(entry.id, $event)"
                >
                    <FontAwesomeIcon
                        :icon="expanded.has(entry.id) ? 'fa-solid fa-caret-down' : 'fa-solid fa-caret-right'"
                        class="text-lines w-3 shrink-0"
                    />
                    <span class="text-text-subtle shrink-0">{{ formatTime(entry.timestamp) }}</span>

                    <!-- API entry -->
                    <template v-if="entry.type === 'api'">
                        <span class="px-1.5 py-0.5 rounded bg-box text-orange-light text-[10px] shrink-0">API</span>
                        <span class="text-orange-darker font-bold shrink-0">{{ entry.method }}</span>
                        <span class="text-text truncate grow">{{ entry.path }}</span>
                        <span :class="['shrink-0 font-bold', statusClass(entry.status)]">{{ entry.status }}</span>
                    </template>

                    <!-- Event entry -->
                    <template v-else>
                        <span class="px-1.5 py-0.5 rounded bg-lines text-white text-[10px] shrink-0">EVENT</span>
                        <span class="text-orange-darker font-bold shrink-0">{{ entry.category }}</span>
                        <span class="text-text truncate grow">{{ entry.message }}</span>
                    </template>
                </div>

                <!-- Expanded detail -->
                <div v-if="expanded.has(entry.id)" class="px-8 pb-2 bg-grey-lighter">
                    <template v-if="entry.type === 'api' && entry.responseBody">
                        <pre class="text-orange-darker whitespace-pre-wrap break-all text-[11px] leading-relaxed">{{ prettyBody(entry.responseBody) }}</pre>
                    </template>
                    <template v-else-if="entry.type === 'api'">
                        <span class="text-text-subtle italic">No response body.</span>
                    </template>
                    <template v-else-if="entry.detail !== null">
                        <pre class="text-orange-darker whitespace-pre-wrap break-all text-[11px] leading-relaxed">{{ JSON.stringify(entry.detail, null, 2) }}</pre>
                    </template>
                    <template v-else>
                        <span class="text-text-subtle italic">No detail.</span>
                    </template>
                </div>
            </div>
        </div>
    </div>
</template>
