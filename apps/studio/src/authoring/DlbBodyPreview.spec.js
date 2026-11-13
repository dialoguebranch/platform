import { describe, it, expect } from 'vitest';
import { bodyPreview } from './DlbBodyPreview.js';

// The plain text of a node's body, for the preview on its card in the graph editor (#200).

describe('bodyPreview', () => {
    it('keeps the text and drops the reply links', () => {
        expect(bodyPreview('Hello there.\n[[Hi!|Second]]\n[[Bye.|End]]')).toBe('Hello there.');
    });

    it('drops the command tags but keeps the text inside them', () => {
        expect(bodyPreview('<<set $count = 1>>Hello <<if $friend>>friend<<else>>stranger<<endif>>.'))
            .toBe('Hello friend stranger.');
        expect(bodyPreview('Look: <<action type="image" value="owl.png">> nice.')).toBe('Look: nice.');
    });

    it('keeps variables as they are written', () => {
        expect(bodyPreview('You have $count apples.')).toBe('You have $count apples.');
    });

    it('collapses whitespace and line breaks', () => {
        expect(bodyPreview('  One\n\n   two\tthree  ')).toBe('One two three');
    });

    it('cuts a long text at a word, with an ellipsis', () => {
        const text = 'word '.repeat(40);
        const preview = bodyPreview(text, 30);
        expect(preview.endsWith('…')).toBe(true);
        expect(preview.length).toBeLessThanOrEqual(31);
        expect(preview).toBe('word word word word word word…');
    });

    it('is empty for an empty body, or one with only replies and commands', () => {
        expect(bodyPreview('')).toBe('');
        expect(bodyPreview(null)).toBe('');
        expect(bodyPreview('<<set $a = 1>>\n[[Go|Next]]')).toBe('');
    });
});
