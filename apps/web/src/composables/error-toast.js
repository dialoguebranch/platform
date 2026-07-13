import { ref } from 'vue';

/**
 * Single shared error toast, shown as a page-level overlay (see ErrorToast.vue) so it never
 * pushes surrounding UI around — only one can be visible at a time.
 */
export const errorToast = ref(null); // { message }

export function showError(message) {
    errorToast.value = { message };
}

export function dismissError() {
    errorToast.value = null;
}
