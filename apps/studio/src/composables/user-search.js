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

import { onBeforeUnmount, ref } from 'vue';
import { useAuthoringClient } from './client.js';
import { useLatestRequest } from './latest-request.js';

// Searching the users the Web Service knows (`listUsers`), shared by the delegate user picker and
// the Users page (#407). search() waits until typing pauses; refresh() searches right away; both
// start at the first page, and loadMore() appends the next one. A response that a newer search
// has overtaken is ignored.
export function useUserSearch({ pageSize = 50, debounceMs = 250 } = {}) {
    const client = useAuthoringClient();
    const latest = useLatestRequest();

    const query = ref('');
    const results = ref([]); // [{ username, subject, variables, dialogueLogs }]
    const loading = ref(false);
    const loadError = ref(false);
    const hasMore = ref(false);

    let page = 0;
    let timer = null;

    function load(nextPage) {
        const id = latest.next();
        loading.value = true;
        loadError.value = false;
        return client.listUsers({ search: query.value.trim(), page: nextPage, pageSize })
            .then((rows) => {
                if (!latest.isCurrent(id)) return;
                results.value = nextPage === 0 ? rows : [...results.value, ...rows];
                page = nextPage;
                hasMore.value = rows.length === pageSize;
            })
            .catch(() => {
                if (!latest.isCurrent(id)) return;
                if (nextPage === 0) results.value = [];
                loadError.value = true;
            })
            .finally(() => {
                if (latest.isCurrent(id)) loading.value = false;
            });
    }

    function refresh() {
        clearTimeout(timer);
        return load(0);
    }

    function search() {
        clearTimeout(timer);
        timer = setTimeout(refresh, debounceMs);
    }

    function loadMore() {
        if (loading.value || !hasMore.value) return Promise.resolve();
        return load(page + 1);
    }

    onBeforeUnmount(() => clearTimeout(timer));

    return { query, results, loading, loadError, hasMore, search, refresh, loadMore };
}
