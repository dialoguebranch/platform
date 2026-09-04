import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DialogueInput from './DialogueInput.vue';
import { Segment } from '@/dlb-lib/model/Segment';

function inputSegment(inputType, parameters, description = null) {
    const s = Segment.fromJSON({ segmentType: 'INPUT', inputType, description, parameters });
    return s;
}

function lastEmit(wrapper, name) {
    const events = wrapper.emitted(name);
    return events ? events[events.length - 1][0] : undefined;
}

describe('DialogueInput', () => {
    it('text: emits { name: value } and is valid once non-empty and within length', async () => {
        const wrapper = mount(DialogueInput, {
            props: { segment: inputSegment('text', { variableName: 'firstName', min: 2, max: 10 }) },
        });
        expect(lastEmit(wrapper, 'update:valid')).toBe(false);

        await wrapper.get('input').setValue('J');
        expect(lastEmit(wrapper, 'update:valid')).toBe(false); // below min length

        await wrapper.get('input').setValue('Jordan');
        expect(lastEmit(wrapper, 'update:value')).toEqual({ firstName: 'Jordan' });
        expect(lastEmit(wrapper, 'update:valid')).toBe(true);
    });

    it('email: only valid for a well-formed address', async () => {
        const wrapper = mount(DialogueInput, {
            props: { segment: inputSegment('email', { variableName: 'addr' }) },
        });
        await wrapper.get('input').setValue('nope');
        expect(lastEmit(wrapper, 'update:valid')).toBe(false);
        await wrapper.get('input').setValue('a@b.co');
        expect(lastEmit(wrapper, 'update:value')).toEqual({ addr: 'a@b.co' });
        expect(lastEmit(wrapper, 'update:valid')).toBe(true);
    });

    it('numeric: coerces to a number and enforces min/max', async () => {
        const wrapper = mount(DialogueInput, {
            props: { segment: inputSegment('numeric', { variableName: 'age', min: 0, max: 120 }) },
        });
        await wrapper.get('input').setValue('200');
        expect(lastEmit(wrapper, 'update:valid')).toBe(false);
        await wrapper.get('input').setValue('42');
        expect(lastEmit(wrapper, 'update:value')).toEqual({ age: 42 });
        expect(lastEmit(wrapper, 'update:valid')).toBe(true);
    });

    it('time: validates HH:MM and the min/max window', async () => {
        const wrapper = mount(DialogueInput, {
            props: {
                segment: inputSegment('time', {
                    variableName: 'wake', granularityMinutes: 15, minTime: '06:00', maxTime: '10:00',
                }),
            },
        });
        await wrapper.get('input').setValue('11:00');
        expect(lastEmit(wrapper, 'update:valid')).toBe(false);
        await wrapper.get('input').setValue('07:30');
        expect(lastEmit(wrapper, 'update:value')).toEqual({ wake: '07:30' });
        expect(lastEmit(wrapper, 'update:valid')).toBe(true);
    });

    it('set: one boolean per option, always valid', async () => {
        const wrapper = mount(DialogueInput, {
            props: {
                segment: inputSegment('set', {
                    options: [
                        { variableName: 'cheese', text: 'Cheese' },
                        { variableName: 'olives', text: 'Olives' },
                    ],
                }),
            },
        });
        expect(lastEmit(wrapper, 'update:value')).toEqual({ cheese: false, olives: false });
        expect(lastEmit(wrapper, 'update:valid')).toBe(true);

        await wrapper.get('input[type="checkbox"]').setValue(true);
        expect(lastEmit(wrapper, 'update:value')).toEqual({ cheese: true, olives: false });
    });

    it('longtext: renders a textarea', () => {
        const wrapper = mount(DialogueInput, {
            props: { segment: inputSegment('longtext', { variableName: 'bio' }) },
        });
        expect(wrapper.find('textarea').exists()).toBe(true);
    });
});
