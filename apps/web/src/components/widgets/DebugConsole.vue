<script setup>
import { ref, computed, inject, watch, watchEffect, nextTick } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { debugLog } from '../../composables/debug-log.js';
import { cookiesVersion } from '../../dlb-lib/util/DocumentFunctions.js';

const state = inject('state');

// Backed by the `state.debugConsoleVisible` cookie (see WCTAClientState.js) so the debug
// console's open/closed state survives a page reload.
const open = computed({
    get: () => state.value.debugConsoleVisible,
    set: (val) => { state.value.debugConsoleVisible = val; },
});
// Backed by the `state.debugConsoleShow{Api,Events,Cookies}` cookies (see WCTAClientState.js)
// so the filter selection survives a page reload.
const showApi = computed({
    get: () => state.value.debugConsoleShowApi,
    set: (val) => { state.value.debugConsoleShowApi = val; },
});
const showEvents = computed({
    get: () => state.value.debugConsoleShowEvents,
    set: (val) => { state.value.debugConsoleShowEvents = val; },
});
const showCookies = computed({
    get: () => state.value.debugConsoleShowCookies,
    set: (val) => { state.value.debugConsoleShowCookies = val; },
});
const cookies = ref([]);

function readCookies() {
    cookies.value = document.cookie
        .split(';')
        .map(s => s.trim())
        .filter(Boolean)
        .map(s => {
            const eq = s.indexOf('=');
            return { name: s.slice(0, eq), value: decodeURIComponent(s.slice(eq + 1)) };
        })
        .sort((a, b) => a.name.localeCompare(b.name));
}

watch(open, (val) => { if (val) readCookies(); });
// Refresh the cookie list whenever any cookie is written, so it stays live while visible
// instead of only updating when manually reopened or refreshed.
watch(cookiesVersion, () => { if (showCookies.value) readCookies(); });
const keyword = ref('');
const expanded = ref(new Set());
const logBody = ref(null);

// Backed by the `state.debugConsole{Width,Height}` cookies (see WCTAClientState.js). Resizing
// updates these refs live for a smooth drag; the cookies are only written once the drag ends
// (see onResizeEnd below), same as ResizablePanels.vue does for the side panels.
const panelWidth = ref(state.value.debugConsoleWidth ?? 720);
const panelHeight = ref(state.value.debugConsoleHeight ?? 250);

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
        state.value.debugConsoleWidth = panelWidth.value;
        state.value.debugConsoleHeight = panelHeight.value;
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

// Some API fields (e.g. DBDraftTranslation.content) are themselves a whole JSON document stored
// as a string, rather than nested JSON structure — recursively parse any string value that looks
// like a JSON object/array so it gets indented too, instead of showing up as one long line packed
// with escaped quotes inside an otherwise pretty-printed body. The startsWith/endsWith check keeps
// this from mangling plain strings that merely happen to be valid JSON on their own (e.g. "123" or
// "true") into a different type — only strings that look like a JSON object/array are unwrapped.
function parseNestedJsonStrings(value) {
    if (typeof value === 'string') {
        const trimmed = value.trim();
        const looksLikeJson = (trimmed.startsWith('{') && trimmed.endsWith('}'))
            || (trimmed.startsWith('[') && trimmed.endsWith(']'));
        if (!looksLikeJson) return value;
        try {
            return parseNestedJsonStrings(JSON.parse(trimmed));
        } catch {
            return value;
        }
    }
    if (Array.isArray(value)) return value.map(parseNestedJsonStrings);
    if (value && typeof value === 'object') {
        return Object.fromEntries(
            Object.entries(value).map(([key, val]) => [key, parseNestedJsonStrings(val)]));
    }
    return value;
}

function prettyBody(raw) {
    if (!raw) return null;
    try {
        return JSON.stringify(parseNestedJsonStrings(JSON.parse(raw)), null, 2);
    } catch {
        return raw;
    }
}

function statusClass(status) {
    if (status >= 200 && status < 300) return 'text-icon-button';
    if (status >= 400 && status < 500) return 'text-orange-dark';
    return 'text-red-dark'; // covers 5xx and 0 (network error)
}

const copiedId = ref(null);

function copyEntry(entry) {
    let text;
    if (entry.type === 'api') {
        text = `[${formatTime(entry.timestamp)}] API ${entry.method} ${entry.path} → ${entry.status}`;
        if (entry.requestBody) text += '\nRequest: ' + prettyBody(entry.requestBody);
        if (entry.responseBody) text += '\nResponse: ' + prettyBody(entry.responseBody);
    } else {
        const message = entry.parts
            ? entry.parts.map(p => p.text).join('')
            : entry.message;
        text = `[${formatTime(entry.timestamp)}] EVENT ${entry.category} — ${message}`;
        if (entry.detail && !entry.parts) text += ' — ' + JSON.stringify(entry.detail);
    }
    navigator.clipboard.writeText(text).then(() => {
        copiedId.value = entry.id;
        setTimeout(() => { copiedId.value = null; }, 1500);
    });
}
</script>

<template>
    <!-- Toggle button -->
    <button
        class="fixed bottom-4 right-4 z-50 rounded-full w-7.5 h-7.5 bg-icon-button hover:bg-icon-button-hover cursor-pointer"
        @click="open = !open"
        title="Debug Console"
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
        <div class="flex items-center gap-2 px-3 py-1.5 bg-orange-darker text-white shrink-0">
            <FontAwesomeIcon icon="fa-solid fa-bug" />
            <span class="font-title font-bold">Debug Console</span>
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
                :class="['px-2 py-0.5 rounded text-[11px] border transition-colors cursor-pointer font-title', showCookies ? 'bg-orange-dark border-orange-medium text-white' : 'bg-transparent border-orange-medium text-orange-light hover:bg-orange-darker']"
                @click="showCookies = !showCookies; if (showCookies) readCookies()"
            >Cookies</button>

            <button
                class="ml-1 px-2 py-0.5 rounded text-[11px] border border-red-dark text-red-dark hover:bg-red-dark hover:text-white cursor-pointer font-title flex items-center gap-1"
                @click="clearLog"
            ><FontAwesomeIcon icon="fa-solid fa-xmark" />Clear</button>

            <button
                class="ml-1 text-orange-light hover:text-white cursor-pointer"
                @click="open = false"
            ><FontAwesomeIcon icon="fa-solid fa-xmark" /></button>
        </div>

        <!-- Cookies panel -->
        <div v-if="showCookies" class="shrink-0 border-b border-grey-light bg-grey-lighter font-mono overflow-x-auto mb-2 shadow-[0_4px_6px_-2px_rgba(0,0,0,0.15)]" style="z-index:1; position:relative;">
            <div class="flex items-center gap-2 px-3 py-1 border-b border-grey-light">
                <FontAwesomeIcon icon="fa-solid fa-cookie-bite" class="text-orange-darker" />
                <span class="font-title font-bold text-orange-darker text-[11px]">Cookies</span>
                <div class="grow"></div>
                <button class="text-orange-light hover:text-orange-darker cursor-pointer" title="Refresh cookies" @click="readCookies">
                    <FontAwesomeIcon icon="fa-solid fa-arrows-rotate" class="text-[10px]" />
                </button>
            </div>
            <div class="overflow-y-auto" :style="{ maxHeight: (panelHeight * 0.3) + 'px' }">
                <div v-if="cookies.length === 0" class="px-3 py-1 text-text-subtle italic">No cookies.</div>
                <div v-for="cookie in cookies" :key="cookie.name" class="flex gap-2 px-3 py-0.5 border-b border-grey-lighter last:border-0">
                    <span class="font-bold text-orange-darker shrink-0">{{ cookie.name }}</span>
                    <span class="text-text truncate">{{ cookie.value }}</span>
                </div>
            </div>
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
                        <span class="px-1.5 py-0.5 rounded bg-orange-darker text-orange-light text-[10px] shrink-0">API</span>
                        <span class="text-orange-darker font-bold shrink-0">{{ entry.method }}</span>
                        <span class="text-text truncate grow">{{ entry.path }}</span>
                        <span :class="['shrink-0 font-bold', statusClass(entry.status)]">{{ entry.status }}</span>
                        <button
                            class="shrink-0 text-text-subtle hover:text-orange-darker cursor-pointer"
                            :title="copiedId === entry.id ? 'Copied!' : 'Copy to clipboard'"
                            @click.stop="copyEntry(entry)"
                        >
                            <FontAwesomeIcon :icon="copiedId === entry.id ? 'fa-solid fa-check' : 'fa-regular fa-copy'" class="text-[10px]" />
                        </button>
                    </div>
                    <div v-if="expanded.has(entry.id)" class="px-8 pb-2 bg-grey-lighter">
                        <template v-if="entry.status === 0">
                            <span class="text-red-dark italic text-[11px]">Network error — no response received.</span>
                        </template>
                        <template v-else-if="entry.responseBody">
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
                        <span class="text-text grow">
                            <template v-if="entry.parts">
                                <template v-for="(part, i) in entry.parts" :key="i">
                                    <code v-if="part.code" class="font-mono bg-grey-lighter px-0.5 rounded text-orange-darker">{{ part.text }}</code>
                                    <span v-else>{{ part.text }}</span>
                                </template>
                            </template>
                            <template v-else>{{ entry.message }}</template>
                        </span>
                        <span v-if="entry.detail && !entry.parts" class="text-text-subtle shrink-0">— {{ JSON.stringify(entry.detail) }}</span>
                        <button
                            class="shrink-0 text-text-subtle hover:text-orange-darker cursor-pointer"
                            :title="copiedId === entry.id ? 'Copied!' : 'Copy to clipboard'"
                            @click="copyEntry(entry)"
                        >
                            <FontAwesomeIcon :icon="copiedId === entry.id ? 'fa-solid fa-check' : 'fa-regular fa-copy'" class="text-[10px]" />
                        </button>
                    </div>
                </template>
            </div>
        </div>
    </div>
</template>
