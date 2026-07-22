import config from '../config.js';

const CHECK_TIMEOUT_MS = 5000;

async function isReachable(url) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), CHECK_TIMEOUT_MS);
    try {
        const response = await fetch(url, { method: 'GET', signal: controller.signal });
        return response.ok;
    } catch {
        return false;
    } finally {
        clearTimeout(timeout);
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
 * Both endpoints used here are public/unauthenticated:
 * - {@code /info/all} — the DLB API's own public service-info end-point.
 * - {@code /realms/{realm}/.well-known/openid-configuration} — the same OIDC discovery document
 *   keycloak-js itself fetches as part of `keycloak.init()`, so if this fails, `initKeycloak()`
 *   would have failed the exact same way.
 *
 * @returns {Promise<{apiUp: boolean, keycloakUp: boolean}>}
 */
export async function checkServicesHealth() {
    const keycloakBase = config.keycloak.url.replace(/\/$/, '');
    const discoveryUrl = `${keycloakBase}/realms/${encodeURIComponent(config.keycloak.realm)}` +
        '/.well-known/openid-configuration';

    const [apiUp, keycloakUp] = await Promise.all([
        isReachable(config.baseUrl + '/info/all'),
        isReachable(discoveryUrl),
    ]);

    return { apiUp, keycloakUp };
}
