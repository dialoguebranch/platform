import { describe, it, expect } from 'vitest';
import {
    normalizeText, listEntries, findStaleEntries, withoutEntries, findStoredText, withTranslation,
    findContextVariants,
} from './DlbTranslations.js';

const CONTENT = {
    'Martin McOwl': {
        'Hello.': 'Hallo.',
        'Old line.': 'Oude regel.',
        female_addressee: { 'Hello.': 'Hallo, meid.', 'Gone.': 'Weg.' },
    },
    _user: { 'Fine.': 'Goed.' },
    Ghost: { 'Boo.': 'Boe.' },
};

const TERMS = [
    { speaker: 'Martin McOwl', term: 'Hello.' },
    { speaker: '_user', term: 'Fine.' },
    { speaker: 'Martin McOwl', term: 'How are you today?' },
];

describe('normalizeText', () => {
    it('trims and collapses whitespace, line breaks included', () => {
        expect(normalizeText('  How are\n  you  today? ')).toBe('How are you today?');
        expect(normalizeText('   ')).toBe('');
    });
});

describe('listEntries', () => {
    it('lists every entry with its speaker and nested contexts, in file order', () => {
        expect(listEntries(CONTENT)).toEqual([
            { speaker: 'Martin McOwl', contexts: [], text: 'Hello.', translation: 'Hallo.' },
            { speaker: 'Martin McOwl', contexts: [], text: 'Old line.', translation: 'Oude regel.' },
            { speaker: 'Martin McOwl', contexts: ['female_addressee'], text: 'Hello.', translation: 'Hallo, meid.' },
            { speaker: 'Martin McOwl', contexts: ['female_addressee'], text: 'Gone.', translation: 'Weg.' },
            { speaker: '_user', contexts: [], text: 'Fine.', translation: 'Goed.' },
            { speaker: 'Ghost', contexts: [], text: 'Boo.', translation: 'Boe.' },
        ]);
    });

    it('lists nothing for missing content', () => {
        expect(listEntries(undefined)).toEqual([]);
    });
});

describe('findStaleEntries', () => {
    it('finds entries matching no term on speaker and text, nested ones and lost speakers included', () => {
        expect(findStaleEntries(CONTENT, TERMS).map((e) => [e.speaker, e.contexts.join(' '), e.text])).toEqual([
            ['Martin McOwl', '', 'Old line.'],
            ['Martin McOwl', 'female_addressee', 'Gone.'],
            ['Ghost', '', 'Boo.'],
        ]);
    });

    it('matches a term filed under another speaker as stale', () => {
        const stale = findStaleEntries({ Anna: { 'Hello.': 'Hoi.' } }, TERMS);
        expect(stale.map((e) => e.speaker)).toEqual(['Anna']);
    });

    it('does not report an entry that differs from a term only in whitespace', () => {
        const content = { 'Martin McOwl': { 'How are\n you  today?': 'Hoe gaat het?' } };
        expect(findStaleEntries(content, TERMS)).toEqual([]);
    });
});

describe('withoutEntries', () => {
    it('removes the given entries and the groups that leaves empty', () => {
        const stale = findStaleEntries(CONTENT, TERMS);
        expect(withoutEntries(CONTENT, stale)).toEqual({
            'Martin McOwl': { 'Hello.': 'Hallo.', female_addressee: { 'Hello.': 'Hallo, meid.' } },
            _user: { 'Fine.': 'Goed.' },
        });
    });

    it('only removes an entry at its own place', () => {
        const nestedHello = { speaker: 'Martin McOwl', contexts: ['female_addressee'], text: 'Hello.' };
        const result = withoutEntries(CONTENT, [nestedHello]);
        expect(result['Martin McOwl']['Hello.']).toBe('Hallo.');
        expect(result['Martin McOwl'].female_addressee).toEqual({ 'Gone.': 'Weg.' });
    });

    it('does not change the content it is given', () => {
        const copy = JSON.parse(JSON.stringify(CONTENT));
        withoutEntries(CONTENT, findStaleEntries(CONTENT, TERMS));
        expect(CONTENT).toEqual(copy);
    });
});

describe('findStoredText', () => {
    const content = {
        Robin: { 'How are\n you?': 'Hoe gaat het?', 'Hello.': 'Hallo.', male_speaker: { 'Hello.': 'Hoi.' } },
    };

    it('finds the term itself', () => {
        expect(findStoredText(content, 'Robin', [], 'Hello.')).toBe('Hello.');
    });

    it('finds a stored text that differs only in whitespace', () => {
        expect(findStoredText(content, 'Robin', [], 'How are you?')).toBe('How are\n you?');
    });

    it('looks in a nested context group', () => {
        expect(findStoredText(content, 'Robin', ['male_speaker'], 'Hello.')).toBe('Hello.');
        expect(findStoredText(content, 'Robin', ['female_speaker'], 'Hello.')).toBeNull();
    });

    it('finds nothing for another speaker or a missing term', () => {
        expect(findStoredText(content, 'Anna', [], 'Hello.')).toBeNull();
        expect(findStoredText(content, 'Robin', [], 'Bye.')).toBeNull();
    });
});

describe('withTranslation', () => {
    it('replaces an entry stored with different whitespace, under the term', () => {
        const content = { Robin: { 'How are\n you?': 'Hoe gaat het?', 'Hello.': 'Hallo.' } };
        expect(withTranslation(content, 'Robin', [], 'How are you?', 'Alles goed?')).toEqual({
            Robin: { 'Hello.': 'Hallo.', 'How are you?': 'Alles goed?' },
        });
        expect(content.Robin['How are\n you?']).toBe('Hoe gaat het?');
    });

    it('sets a translation in a nested context group, creating groups as needed', () => {
        expect(withTranslation({}, 'Robin', ['female_addressee'], 'Hello.', 'Hallo, meid.')).toEqual({
            Robin: { female_addressee: { 'Hello.': 'Hallo, meid.' } },
        });
    });
});

describe('findContextVariants', () => {
    it('lists the context groups holding a translation of the term for the speaker', () => {
        const content = {
            Robin: {
                'Hello.': 'Hallo.',
                female_addressee: { 'Hello.': 'Hallo, meid.' },
                female_speaker: { male_addressee: { '\nHello. ': 'Hoi.' }, 'Bye.': 'Dag.' },
            },
            Anna: { male_speaker: { 'Hello.': 'Hé.' } },
        };
        expect(findContextVariants(content, 'Robin', 'Hello.')).toEqual([
            ['female_addressee'],
            ['female_speaker', 'male_addressee'],
        ]);
    });
});
