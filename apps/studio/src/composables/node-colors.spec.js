import { describe, it, expect } from 'vitest';
import { normalizeNodeColor, colorForNode, DEFAULT_NODE_COLOR } from './node-colors.js';

describe('node colours (#203)', () => {
    it('accepts #rrggbb only, lowercased', () => {
        expect(normalizeNodeColor('#ABCDEF')).toBe('#abcdef');
        expect(normalizeNodeColor(' #0369a1 ')).toBe('#0369a1');
        expect(normalizeNodeColor('#abc')).toBeNull();
        expect(normalizeNodeColor('red')).toBeNull();
        expect(normalizeNodeColor('3')).toBeNull();
        expect(normalizeNodeColor(null)).toBeNull();
    });

    it('draws a node without a valid colour in the default one', () => {
        expect(colorForNode('#0369a1')).toBe('#0369a1');
        expect(colorForNode(undefined)).toBe(DEFAULT_NODE_COLOR);
        expect(colorForNode('5')).toBe(DEFAULT_NODE_COLOR);
    });
});
