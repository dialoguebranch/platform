// Guards against out-of-order async responses. Several places in this app can re-trigger a fetch
// before the previous one resolves (switching tabs, a manual refresh button, re-fetching after an
// edit) — without this, an older response arriving after a newer one would silently overwrite
// state with stale data. Call next() right before issuing each request, keep the returned id, and
// check isCurrent(id) in every then()/catch()/finally() before acting on the result.
export function useLatestRequest() {
    let requestId = 0;
    return {
        next: () => ++requestId,
        isCurrent: (id) => id === requestId,
    };
}
