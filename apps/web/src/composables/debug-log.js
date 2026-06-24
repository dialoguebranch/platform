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

export function logEvent(category, message, detail = null) {
    debugLog.value.push({
        id: _nextId++,
        type: 'event',
        timestamp: new Date(),
        category,
        message,
        detail,
    });
}
