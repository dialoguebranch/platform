import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { tagsToMap, mapToTags, getPosition, setPosition, RESERVED_HEADER_TAGS, USER_SPEAKER } from './DlbHeaderTags.js';

const TAGS = [
    { key: 'title', value: 'Start' },
    { key: '2', value: 'a digit key' },
    { key: 'comment', value: 'see https://example.com // kept' },
];

describe('tagsToMap and mapToTags', () => {
    it('keep the order of tags, even for number-like keys', () => {
        const map = tagsToMap(TAGS);
        expect([...map.keys()]).toEqual(['title', '2', 'comment']);
        expect(mapToTags(map)).toEqual(TAGS);
    });

    it('lets the first occurrence of a key win, and treats no tags as none', () => {
        const map = tagsToMap([{ key: 'title', value: 'A' }, { key: 'title', value: 'B' }]);
        expect(map.get('title')).toBe('A');
        expect(tagsToMap(undefined).size).toBe(0);
    });
});

describe('position', () => {
    it('reads and sets the position tag, keeping the other tags in place', () => {
        const map = tagsToMap([...TAGS, { key: 'position', value: '10,20' }]);
        expect(getPosition(map)).toEqual({ x: 10, y: 20 });
        const tags = mapToTags(setPosition(map, 30.4, 40.6));
        expect(tags.map((tag) => tag.key)).toEqual(['title', '2', 'comment', 'position']);
        expect(tags[3].value).toBe('30,41');
    });

    it('defaults a missing or malformed position to 0,0', () => {
        expect(getPosition(tagsToMap([]))).toEqual({ x: 0, y: 0 });
        expect(getPosition(tagsToMap([{ key: 'position', value: 'x' }]))).toEqual({ x: 0, y: 0 });
    });
});

describe('RESERVED_HEADER_TAGS', () => {
    it('matches DialogueBranchConstants.DLB_RESERVED_HEADER_TAGS in core', () => {
        // Vitest runs from apps/studio.
        const constants = readFileSync(resolve(process.cwd(),
            '../../packages/core/src/main/java/com/dialoguebranch/model/common/DialogueBranchConstants.java'),
            'utf8');
        const declaration = constants.match(/DLB_RESERVED_HEADER_TAGS\s*=\s*\{([^}]*)\}/)[1];
        const coreTags = [...declaration.matchAll(/"([^"]+)"/g)].map((match) => match[1]);
        expect(RESERVED_HEADER_TAGS).toEqual(coreTags);
        expect(RESERVED_HEADER_TAGS).toContain('comment');
    });
});

describe('USER_SPEAKER', () => {
    it('matches DialogueBranchConstants.DLB_USER_SPEAKER in core', () => {
        const constants = readFileSync(resolve(process.cwd(),
            '../../packages/core/src/main/java/com/dialoguebranch/model/common/DialogueBranchConstants.java'),
            'utf8');
        expect(USER_SPEAKER).toBe(constants.match(/DLB_USER_SPEAKER\s*=\s*"([^"]*)"/)[1]);
    });
});
