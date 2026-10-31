// A node's colour, its `nodeColor` header tag (#203): `#rrggbb`, purely cosmetic, shared between
// DialogueEditor.vue (node box border) and NodeEditPanel.vue (colour picker) so they never drift
// apart visually. The presets are offered as one-click swatches; any other `#rrggbb` is allowed.
export const COLOR_PALETTE = [
    '#c2410c', '#0d9488', '#7c3aed', '#0369a1', '#be123c',
    '#65a30d', '#a16207', '#4f46e5', '#0891b2', '#be185d',
];

/** The colour a node without a (valid) `nodeColor` gets. */
export const DEFAULT_NODE_COLOR = COLOR_PALETTE[0];

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;

/**
 * Returns a `nodeColor` value as a lowercase `#rrggbb`, or null if it isn't one.
 *
 * @param {?string} value
 * @returns {?string}
 */
export function normalizeNodeColor(value) {
    const trimmed = value?.trim() ?? '';
    return HEX_COLOR.test(trimmed) ? trimmed.toLowerCase() : null;
}

/**
 * The colour to draw a node in: its `nodeColor`, or the default if it has none or an invalid one.
 *
 * @param {?string} value the node's `nodeColor` tag.
 * @returns {string}
 */
export function colorForNode(value) {
    return normalizeNodeColor(value) ?? DEFAULT_NODE_COLOR;
}
