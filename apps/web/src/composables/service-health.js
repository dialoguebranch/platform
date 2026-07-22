import config from '../config.js';

const CHECK_TIMEOUT_MS = 5000;

async function fetchWithTimeout(url) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), CHECK_TIMEOUT_MS);
    try {
        return await fetch(url, { method: 'GET', signal: controller.signal });
    } finally {
        clearTimeout(timeout);
    }
}

async function isReachable(url) {
    try {
        const response = await fetchWithTimeout(url);
        return response.ok;
    } catch {
        return false;
    }
}

/**
 * Fetches the DLB API's public service-info payload, returning both whether it's reachable and
 * (if so) its reported {@code serviceVersion} — the same field {@link
 * ../components/pages/MainPage.vue} shows as "Web Service vX.Y.Z". Reused here instead of a
 * separate reachability-only call so the version check doesn't cost a second round trip.
 *
 * @param url the {@code /info/all} end-point to call.
 * @returns {Promise<{up: boolean, serviceVersion: (string|null)}>}
 */
async function fetchApiStatus(url) {
    try {
        const response = await fetchWithTimeout(url);
        if (!response.ok) return { up: false, serviceVersion: null };
        const body = await response.json().catch(() => null);
        return { up: true, serviceVersion: body?.serviceVersion ?? null };
    } catch {
        return { up: false, serviceVersion: null };
    }
}

/**
 * Checks whether both backends this app depends on — the DLB API and the BFF's own auth path
 * (which in turn depends on Keycloak) — are actually reachable, before redirecting the browser to
 * log in (see src/auth.js's redirectToLogin, called from src/main.js). That redirect is a full
 * top-level navigation, ending on the BFF and then Keycloak's own hosted login page — triggered
 * whether or not the user already has a valid session, since there's no way to know that
 * client-side without asking the server anyway. If either backend is down, that navigation would
 * otherwise strand the user on a broken/unreachable page instead of this app's own status screen,
 * so this check has to run first, unconditionally.
 *
 * Also returns the API's reported software version, so the caller can compare it against this
 * client's own build-time {@code __APP_VERSION__} and warn about a mismatch (e.g. a stale cached
 * bundle after a deploy) before proceeding into the app.
 *
 * Both endpoints used here are public/unauthenticated, and both are called as same-origin
 * relative paths through the BFF (see vite.config.js's dev-server proxy for local development):
 * - {@code /api/v1/info/all} — the DLB API's own public service-info end-point, proxied through
 *   without a session (see the BFF's SecurityConfig and ApiProxyController).
 * - {@code /actuator/health} — the BFF's own health endpoint. Not a literal Keycloak reachability
 *   check (this app has no business knowing Keycloak's address at all anymore — that's entirely
 *   the BFF's concern), but the right layer for this app to probe now: if the BFF itself is up,
 *   the login redirect at least lands somewhere real, and any actual Keycloak-side outage will
 *   surface at that point the same way a broken third-party login page always would.
 *
 * @returns {Promise<{apiUp: boolean, authUp: boolean, serviceVersion: (string|null)}>}
 */
export async function checkServicesHealth() {
    const [apiStatus, authUp] = await Promise.all([
        fetchApiStatus(config.baseUrl + '/info/all'),
        isReachable('/actuator/health'),
    ]);

    return { apiUp: apiStatus.up, authUp, serviceVersion: apiStatus.serviceVersion };
}
