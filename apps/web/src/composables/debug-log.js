import { ref } from 'vue';

let _nextId = 0;

export const debugLog = ref([]);

export function logApiCall(method, path, status, responseBody) {
    debugLog.value.push({
        id: _nextId++,
        type: 'api',
        timestamp: new Date(),
        method,
        path,
        status,
        responseBody,
    });
}

export function logEvent(category, message, ...values) {
    const parts = values.length > 0 ? buildParts(message, values) : null;
    debugLog.value.push({
        id: _nextId++,
        type: 'event',
        timestamp: new Date(),
        category,
        message,
        parts,
    });
}

function buildParts(message, values) {
    const parts = [];
    let remaining = message;
    for (let i = 0; i < values.length; i++) {
        const placeholder = `$${i + 1}`;
        const idx = remaining.indexOf(placeholder);
        if (idx === -1) break;
        if (idx > 0) parts.push({ text: remaining.slice(0, idx), code: false });
        parts.push({ text: String(values[i]), code: true });
        remaining = remaining.slice(idx + placeholder.length);
    }
    if (remaining) parts.push({ text: remaining, code: false });
    return parts;
}
