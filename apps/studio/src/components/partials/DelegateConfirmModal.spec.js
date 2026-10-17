/* @license
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DelegateConfirmModal from './DelegateConfirmModal.vue';

// Confirming who is being acted for (#330): acting for real warns what it changes, simulating
// (#411) doesn't.

const stubs = { FontAwesomeIcon: true, teleport: true };

function confirmFor(change) {
    return mount(DelegateConfirmModal, { props: { change }, global: { stubs } });
}

describe('DelegateConfirmModal', () => {
    it('warns what acting for real changes, with a button that says so', async () => {
        const wrapper = confirmFor({ mode: 'act', label: 'robin' });
        expect(wrapper.find('[data-test="act-warning"]').text()).toContain('their own');
        expect(wrapper.text()).toContain('Simulate as');
        const button = wrapper.find('[data-test="delegate-confirm-button"]');
        expect(button.text()).toBe('Act as robin');
        await button.trigger('click');
        expect(wrapper.emitted('confirm')).toHaveLength(1);
    });

    it('confirms simulating without a warning', () => {
        const wrapper = confirmFor({ mode: 'simulate', label: 'robin' });
        expect(wrapper.find('[data-test="act-warning"]').exists()).toBe(false);
        expect(wrapper.text()).toContain('Nothing of theirs is changed');
        expect(wrapper.find('[data-test="delegate-confirm-button"]').text()).toBe('Simulate as robin');
    });

    it('confirms going back to acting as yourself', async () => {
        const wrapper = confirmFor({ mode: null, label: 'robin' });
        expect(wrapper.find('[data-test="act-warning"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="delegate-confirm-button"]').text()).toBe('Act as myself');
        await wrapper.findAll('button').find((b) => b.text() === 'Cancel').trigger('click');
        expect(wrapper.emitted('cancel')).toHaveLength(1);
    });
});
