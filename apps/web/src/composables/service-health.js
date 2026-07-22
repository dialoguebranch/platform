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
 * Checks whether both backend services the app depends on — the DLB API and Keycloak — are
 * actually reachable, before keycloak.js's onLoad:'login-required' redirect fires. That redirect
 * is a full top-level navigation to Keycloak's own hosted login/SSO-check flow — triggered
 * whether or not the user already has a valid session, since there's no way to know that
 * client-side without asking Keycloak's server anyway. If Keycloak or the DLB API behind it is
 * down, that navigation would otherwise strand the user on a broken/unreachable page instead of
 * this app's own status screen, so this check has to run first, unconditionally.
 *
 * Also returns the API's reported software version, so the caller can compare it against this
 * client's own build-time {@code __APP_VERSION__} and warn about a mismatch (e.g. a stale cached
 * bundle after a deploy) before proceeding into the app.
 *
 * Both endpoints used here are public/unauthenticated:
 * - {@code /info/all} — the DLB API's own public service-info end-point.
 * - {@code /realms/{realm}/.well-known/openid-configuration} — the same OIDC discovery document
 *   keycloak-js itself fetches as part of `keycloak.init()`, so if this fails, `initKeycloak()`
 *   would have failed the exact same way.
 *
 * @returns {Promise<{apiUp: boolean, keycloakUp: boolean, serviceVersion: (string|null)}>}
 */
export async function checkServicesHealth() {
    const keycloakBase = config.keycloak.url.replace(/\/$/, '');
    const discoveryUrl = `${keycloakBase}/realms/${encodeURIComponent(config.keycloak.realm)}` +
        '/.well-known/openid-configuration';

    const [apiStatus, keycloakUp] = await Promise.all([
        fetchApiStatus(config.baseUrl + '/info/all'),
        isReachable(discoveryUrl),
    ]);

    return { apiUp: apiStatus.up, keycloakUp, serviceVersion: apiStatus.serviceVersion };
}
