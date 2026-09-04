import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DialogueStatement from './DialogueStatement.vue';
import { Statement } from '@/dlb-lib/model/Statement';
import { Segment } from '@/dlb-lib/model/Segment';

describe('DialogueStatement', () => {
    it('plain text: a single sanitized block, paragraphs preserved', () => {
        const statement = new Statement([
            new Segment('TEXT', 'First paragraph.\n\nSecond paragraph.'),
        ]);
        const w = mount(DialogueStatement, { props: { statement } });
        expect(w.findAll('p')).toHaveLength(2);
        expect(w.find('a').exists()).toBe(false);
    });

    it('with an action segment: renders the text runs and the action in order', () => {
        const statement = new Statement([
            new Segment('TEXT', 'Check out this platform.'),
            Segment.fromJSON({
                segmentType: 'ACTION',
                action: { type: 'link', value: 'https://www.dialoguebranch.com/', parameters: {} },
            }),
        ]);
        const w = mount(DialogueStatement, { props: { statement } });
        expect(w.text()).toContain('Check out this platform.');
        const a = w.get('a');
        expect(a.attributes('href')).toBe('https://www.dialoguebranch.com/');
    });

    it('drops empty text runs around an action', () => {
        const statement = new Statement([
            new Segment('TEXT', '   '),
            Segment.fromJSON({
                segmentType: 'ACTION',
                action: { type: 'image', value: 'dog.png', parameters: {} },
            }),
            new Segment('TEXT', ''),
        ]);
        const w = mount(DialogueStatement, { props: { statement } });
        expect(w.findAll('p')).toHaveLength(0);
        expect(w.get('img').attributes('src')).toBe('dog.png');
    });
});
