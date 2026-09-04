import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DialogueActionSegment from './DialogueActionSegment.vue';
import { Action } from '@/dlb-lib/model/Action';

const mountAction = (json) =>
    mount(DialogueActionSegment, { props: { action: Action.fromJSON(json) } });

describe('DialogueActionSegment', () => {
    it('link: an anchor labelled by the `text` parameter, opening in a new tab', () => {
        const w = mountAction({
            type: 'link', value: 'https://www.dialoguebranch.com/', parameters: { text: 'website' },
        });
        const a = w.get('a');
        expect(a.attributes('href')).toBe('https://www.dialoguebranch.com/');
        expect(a.attributes('target')).toBe('_blank');
        expect(a.text()).toBe('website');
    });

    it('link: falls back to the URL as the label', () => {
        const w = mountAction({ type: 'link', value: 'https://x.test/', parameters: {} });
        expect(w.get('a').text()).toBe('https://x.test/');
    });

    it('image: an <img> using the value as src', () => {
        const w = mountAction({ type: 'image', value: 'dog.png', parameters: {} });
        expect(w.get('img').attributes('src')).toBe('dog.png');
        expect(w.find('a').exists()).toBe(false);
    });

    it('video: a <video> element for a direct media URL', () => {
        const w = mountAction({ type: 'video', value: 'https://x.test/clip.mp4', parameters: {} });
        expect(w.get('video').attributes('src')).toBe('https://x.test/clip.mp4');
    });

    it('video: a labelled link for a non-media URL (e.g. YouTube)', () => {
        const w = mountAction({
            type: 'video', value: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', parameters: {},
        });
        expect(w.find('video').exists()).toBe(false);
        expect(w.get('a').attributes('href')).toContain('youtube.com');
    });

    it('generic: a chip showing the value, with the parameters on hover', () => {
        const w = mountAction({
            type: 'generic', value: 'OPEN_RECIPE_BOOK', parameters: { delay: '2000', page: '42' },
        });
        expect(w.text()).toContain('OPEN_RECIPE_BOOK');
        expect(w.get('span').attributes('title')).toBe('delay=2000, page=42');
        expect(w.find('a').exists()).toBe(false);
        expect(w.find('img').exists()).toBe(false);
    });
});
