// Maps a node's `colorId` header tag (0-9, per DialogueBranchConstants.DLB_RESERVED_HEADER_TAGS)
// to an accent color — purely cosmetic, shared between DialogueEditor.vue (node box border) and
// NodeEditPanel.vue (color picker swatches) so they never drift apart visually.
export const COLOR_PALETTE = [
    '#c2410c', '#0d9488', '#7c3aed', '#0369a1', '#be123c',
    '#65a30d', '#a16207', '#4f46e5', '#0891b2', '#be185d',
];

export function colorForId(colorId) {
    const index = parseInt(colorId, 10);
    return COLOR_PALETTE[Number.isNaN(index) ? 0 : Math.abs(index) % COLOR_PALETTE.length];
}
