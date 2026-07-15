import DOMPurify from 'dompurify';

// Dialogue authors can intentionally embed simple HTML formatting (e.g. <b>, <i>, <br>) in a
// node's text, which is rendered via v-html. That same text can also contain resolved $variable
// values supplied at runtime (via the Variable Browser, or a free-text INPUT_REPLY captured from
// an end user) — those must never be allowed to inject executable content. Sanitizing here keeps
// author-authored formatting working while closing off that injection path.
export function sanitizeHtml(html) {
    return DOMPurify.sanitize(html ?? '');
}
