<script setup>
import { ref, computed, watchEffect, nextTick } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { debugLog } from '../../composables/debug-log.js';

const open = ref(false);
const showApi = ref(true);
const showEvents = ref(true);
const keyword = ref('');
const expanded = ref(new Set());
const logBody = ref(null);

const panelWidth = ref(720);
const panelHeight = ref(250);

function onResizeStart(e) {
    e.preventDefault();
    const startX = e.clientX;
    const startY = e.clientY;
    const startW = panelWidth.value;
    const startH = panelHeight.value;

    function onMove(e) {
        panelWidth.value  = Math.max(320, startW - (e.clientX - startX));
        panelHeight.value = Math.max(150, startH - (e.clientY - startY));
    }
    function onUp() {
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
    }
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
}

const entries = computed(() => {
    const kw = keyword.value.trim().toLowerCase();
    return [...debugLog.value]
        .filter(e => (e.type === 'api' && showApi.value) || (e.type === 'event' && showEvents.value))
        .filter(e => {
            if (!kw) return true;
            if (e.type === 'api') return (e.method + ' ' + e.path + ' ' + e.status + ' ' + (e.responseBody ?? '')).toLowerCase().includes(kw);
            return (e.category + ' ' + e.message + ' ' + JSON.stringify(e.detail ?? '')).toLowerCase().includes(kw);
        });
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
        class="fixed bottom-4 right-[60px] z-50 flex flex-col bg-white shadow-[0_0_4px_black] overflow-hidden text-xs"
        :style="{ width: panelWidth + 'px', height: panelHeight + 'px' }"
    >
        <!-- Resize handle (top-left corner) -->
        <div
            class="absolute top-0 left-0 w-4 h-4 cursor-nw-resize z-10 flex items-center justify-center"
            @mousedown="onResizeStart"
        >
            <svg width="8" height="8" viewBox="0 0 8 8" class="text-orange-light opacity-60">
                <line x1="1" y1="7" x2="7" y2="1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                <line x1="1" y1="4" x2="4" y2="1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
        </div>
        <!-- Header -->
        <div class="flex items-center gap-2 px-3 py-1.5 bg-box text-white shrink-0">
            <FontAwesomeIcon icon="fa-solid fa-bug" />
            <span class="font-title font-bold">Debug Log</span>
            <span class="text-orange-light text-[11px]">({{ debugLog.length }} entries)</span>

            <div class="grow"></div>

            <!-- Keyword filter -->
            <div class="relative flex items-center">
                <FontAwesomeIcon icon="fa-solid fa-magnifying-glass" class="absolute left-1.5 text-orange-light text-[10px] pointer-events-none" />
                <input
                    v-model="keyword"
                    type="text"
                    placeholder="Filter..."
                    class="pl-5 pr-1 py-0.5 rounded text-[11px] bg-orange-darker border border-orange-medium text-white placeholder-orange-light font-title focus:outline-none focus:border-orange-light w-28"
                />
                <button v-if="keyword" class="absolute right-1 text-orange-light hover:text-white cursor-pointer" @click="keyword = ''">
                    <FontAwesomeIcon icon="fa-solid fa-xmark" class="text-[10px]" />
                </button>
            </div>

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
                <!-- API entry (collapsable) -->
                <template v-if="entry.type === 'api'">
                    <div
                        class="flex items-baseline gap-2 px-3 py-1 hover:bg-grey-lighter cursor-pointer select-none"
                        @click="toggleEntry(entry.id, $event)"
                    >
                        <FontAwesomeIcon
                            :icon="expanded.has(entry.id) ? 'fa-solid fa-caret-down' : 'fa-solid fa-caret-right'"
                            class="text-lines w-3 shrink-0"
                        />
                        <span class="text-text-subtle shrink-0">{{ formatTime(entry.timestamp) }}</span>
                        <span class="px-1.5 py-0.5 rounded bg-box text-orange-light text-[10px] shrink-0">API</span>
                        <span class="text-orange-darker font-bold shrink-0">{{ entry.method }}</span>
                        <span class="text-text truncate grow">{{ entry.path }}</span>
                        <span :class="['shrink-0 font-bold', statusClass(entry.status)]">{{ entry.status }}</span>
                    </div>
                    <div v-if="expanded.has(entry.id)" class="px-8 pb-2 bg-grey-lighter">
                        <template v-if="entry.responseBody">
                            <pre class="text-orange-darker whitespace-pre-wrap break-all text-[11px] leading-relaxed">{{ prettyBody(entry.responseBody) }}</pre>
                        </template>
                        <template v-else>
                            <span class="text-text-subtle italic">No response body.</span>
                        </template>
                    </div>
                </template>

                <!-- Event entry (always expanded) -->
                <template v-else>
                    <div class="flex items-baseline gap-2 px-3 py-1">
                        <FontAwesomeIcon icon="fa-solid fa-circle-exclamation" class="text-lines w-3 shrink-0" />
                        <span class="text-text-subtle shrink-0">{{ formatTime(entry.timestamp) }}</span>
                        <span class="px-1.5 py-0.5 rounded bg-lines text-white text-[10px] shrink-0">EVENT</span>
                        <span class="text-orange-darker font-bold shrink-0">{{ entry.category }}</span>
                        <span class="text-text">
                            <template v-if="entry.parts">
                                <template v-for="(part, i) in entry.parts" :key="i">
                                    <code v-if="part.code" class="font-mono bg-grey-lighter px-0.5 rounded text-orange-darker">{{ part.text }}</code>
                                    <span v-else>{{ part.text }}</span>
                                </template>
                            </template>
                            <template v-else>{{ entry.message }}</template>
                        </span>
                        <span v-if="entry.detail && !entry.parts" class="text-text-subtle">— {{ JSON.stringify(entry.detail) }}</span>
                    </div>
                </template>
            </div>
        </div>
    </div>
</template>
