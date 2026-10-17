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
import ModeBar from './ModeBar.vue';
import { DLB_APP_MODE_DRAFT, DLB_APP_MODE_LIVE } from '../../StudioClientState.js';

// The mode bar says which mode Studio is in, and who is being acted for (#330, #411).

const stubs = { FontAwesomeIcon: true };

function bar(mode, delegate = null) {
    return mount(ModeBar, { props: { mode, delegate }, global: { stubs } });
}

describe('ModeBar', () => {
    it('says which mode Studio is in, in that mode\'s colour', () => {
        const authoring = bar(DLB_APP_MODE_DRAFT);
        expect(authoring.find('[data-test="mode-bar-mode"]').text()).toContain('Authoring Mode');
        expect(authoring.find('[data-test="mode-bar"]').classes()).toContain('bg-mode-authoring');

        const live = bar(DLB_APP_MODE_LIVE);
        expect(live.find('[data-test="mode-bar-mode"]').text()).toContain('Live Mode');
        expect(live.find('[data-test="mode-bar"]').classes()).toContain('bg-mode-live');
        expect(live.find('[data-test="stop-delegate"]').exists()).toBe(false);
    });

    it('turns red while acting for real, says that it affects the user\'s real data, and stops on request', async () => {
        const wrapper = bar(DLB_APP_MODE_LIVE, { subject: 's', label: 'robin', mode: 'act' });
        expect(wrapper.find('[data-test="mode-bar"]').classes()).toContain('bg-red-dark');
        expect(wrapper.find('[data-test="acting-banner"]').text()).toContain("affect this user's real data");
        expect(wrapper.find('[data-test="simulating-banner"]').exists()).toBe(false);
        const stop = wrapper.find('[data-test="stop-delegate"]');
        expect(stop.text()).toBe('Stop acting as robin');
        await stop.trigger('click');
        expect(wrapper.emitted('stop')).toHaveLength(1);
    });

    it('keeps the mode\'s colour while simulating, and says that nothing is saved', () => {
        const wrapper = bar(DLB_APP_MODE_DRAFT, { subject: 's', label: 'robin', mode: 'simulate' });
        expect(wrapper.find('[data-test="mode-bar"]').classes()).toContain('bg-mode-authoring');
        expect(wrapper.find('[data-test="simulating-banner"]').text()).toContain('Nothing is saved');
        expect(wrapper.find('[data-test="stop-delegate"]').text()).toBe('Stop simulating');
    });
});
