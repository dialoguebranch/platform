import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import InteractiveReply from './InteractiveReply.vue';
import { BasicReply } from '@/dlb-lib/model/BasicReply';
import { Statement } from '@/dlb-lib/model/Statement';
import { Segment } from '@/dlb-lib/model/Segment';

function inputReply() {
    const reply = BasicReply.emptyInstance();
    reply.replyId = 7;
    reply.endsDialogue = false;
    reply.statement = new Statement([
        new Segment('TEXT', 'My name is '),
        Segment.fromJSON({
            segmentType: 'INPUT', inputType: 'text', variableName: 'firstName', min: 2,
        }),
        new Segment('TEXT', ' and I am '),
        Segment.fromJSON({
            segmentType: 'INPUT', inputType: 'numeric', variableName: 'age', min: 0, max: 120,
        }),
        new Segment('TEXT', '.'),
    ]);
    return reply;
}

describe('InteractiveReply', () => {
    it('keeps the send button disabled until every input is valid, then emits the merged map', async () => {
        const wrapper = mount(InteractiveReply, { props: { reply: inputReply() } });
        const button = wrapper.get('button');
        expect(button.attributes('disabled')).toBeDefined();

        const [textInput, numberInput] = wrapper.findAll('input');
        await textInput.setValue('Robin');
        expect(button.attributes('disabled')).toBeDefined(); // age still empty
        await numberInput.setValue('41');
        expect(button.attributes('disabled')).toBeUndefined();

        await button.trigger('click');
        expect(wrapper.emitted('submit')).toHaveLength(1);
        expect(wrapper.emitted('submit')[0][0]).toEqual({ firstName: 'Robin', age: 41 });
    });

    it('ignores a second click (no double submit)', async () => {
        const wrapper = mount(InteractiveReply, { props: { reply: inputReply() } });
        const [textInput, numberInput] = wrapper.findAll('input');
        await textInput.setValue('Robin');
        await numberInput.setValue('41');
        await wrapper.get('button').trigger('click');
        await wrapper.get('button').trigger('click');
        expect(wrapper.emitted('submit')).toHaveLength(1);
    });

    it('respects the disabled prop', async () => {
        const wrapper = mount(InteractiveReply, {
            props: { reply: inputReply(), disabled: true },
        });
        const [textInput, numberInput] = wrapper.findAll('input');
        await textInput.setValue('Robin');
        await numberInput.setValue('41');
        expect(wrapper.get('button').attributes('disabled')).toBeDefined();
    });
});
