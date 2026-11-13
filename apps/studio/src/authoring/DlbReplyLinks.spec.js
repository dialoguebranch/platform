import { describe, it, expect } from 'vitest';
import { extractReplyLinks, resolveDialogueReference } from './DlbReplyLinks.js';

// Reply links in a node body, and where an external one leads (#191).

describe('extractReplyLinks', () => {
    it('tells internal links from external ones', () => {
        const links = extractReplyLinks('[[Hi|Next]] [[On|other.Start]] [[Up|../intro/welcome.Second]]');
        expect(links.map((l) => [l.isExternal, l.dialogueRef, l.nodeTitle])).toEqual([
            [false, null, 'Next'], [true, 'other', 'Start'], [true, '../intro/welcome', 'Second'],
        ]);
    });
});

describe('resolveDialogueReference', () => {
    // The same rules as core's ExternalNodePointer.getAbsoluteDialogueId.
    it('is relative to the folder of the dialogue the link is in', () => {
        expect(resolveDialogueReference('main', 'other')).toBe('other');
        expect(resolveDialogueReference('excerpts/bobby/talk', 'gossip')).toBe('excerpts/bobby/gossip');
        expect(resolveDialogueReference('excerpts/bobby/talk', 'gossip/news')).toBe('excerpts/bobby/gossip/news');
    });

    it('ignores a leading slash, as core does', () => {
        expect(resolveDialogueReference('excerpts/bobby/talk', '/gossip/other')).toBe('excerpts/bobby/gossip/other');
    });

    it('goes up a folder for each ../', () => {
        expect(resolveDialogueReference('excerpts/bobby/talk', '../intro')).toBe('excerpts/intro');
        expect(resolveDialogueReference('excerpts/bobby/talk', '../../intro/welcome')).toBe('intro/welcome');
        expect(resolveDialogueReference('a/b/talk', '../x/../y/z')).toBe('a/y/z');
    });

    it('takes ./ from the project root', () => {
        expect(resolveDialogueReference('excerpts/bobby/talk', './intro/welcome')).toBe('intro/welcome');
    });

    it('is null for a reference that goes below the root or names no dialogue', () => {
        expect(resolveDialogueReference('main', '../other')).toBeNull();
        expect(resolveDialogueReference('a/talk', 'b/')).toBeNull();
        expect(resolveDialogueReference('a/talk', './')).toBeNull();
    });
});
