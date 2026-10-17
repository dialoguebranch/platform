import { describe, it, expect } from 'vitest';
import { preambleToText, textToPreamble } from './DlbPreamble.js';

describe('preambleToText', () => {
    it('removes the // and one space, keeping further indentation', () => {
        expect(preambleToText('// What this is for.\n//   Indented.\n  //No space.'))
            .toBe('What this is for.\n  Indented.\nNo space.');
    });

    it('turns no preamble into empty text', () => {
        expect(preambleToText('')).toBe('');
        expect(preambleToText(undefined)).toBe('');
    });
});

describe('textToPreamble', () => {
    it('makes each line a comment line, and an empty line a bare //', () => {
        expect(textToPreamble('What this is for.\n\n  Indented.\r\n   \nLast'))
            .toBe('// What this is for.\n//\n//   Indented.\n//\n// Last');
    });

    it('leaves out empty lines at the start and end', () => {
        expect(textToPreamble('\n \nFirst.\n\n\nSecond.\n\n')).toBe('// First.\n//\n//\n// Second.');
    });

    it('turns empty text into no preamble', () => {
        expect(textToPreamble('')).toBe('');
        expect(textToPreamble(' \n \n')).toBe('');
    });

    it('round-trips a stored preamble written with "// "', () => {
        const preamble = '// What this is for.\n//\n//   Indented.';
        expect(textToPreamble(preambleToText(preamble))).toBe(preamble);
    });
});
