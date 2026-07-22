const LOGIN_PATH = '/oauth2/authorization/keycloak';
const LOGOUT_PATH = '/logout';

/**
 * Fetches the current session's identity from the BFF. Unlike a top-level navigation to a
 * protected path, GET /whoami never triggers a login redirect on its own — an unauthenticated
 * call gets a plain 401 instead (see the BFF's SecurityConfig), so the caller decides whether
 * and how to send the user to log in.
 *
 * @returns {Promise<{username: string, roles: string[]}|null>} null if there is no session.
 */
export async function fetchWhoAmI() {
    const response = await fetch('/whoami', { credentials: 'include' });
    if (response.status === 401) return null;
    if (!response.ok) throw new Error(`GET /whoami failed: ${response.status}`);
    return await response.json();
}

/**
 * Sends the browser to the BFF's login redirect. A real top-level navigation, not something to
 * call from within a fetch — the BFF redirects on to Keycloak's own hosted login page from here.
 */
export function redirectToLogin() {
    window.location.href = LOGIN_PATH;
}

/**
 * Sends the browser to the BFF's logout endpoint (RP-initiated: ends both this app's session and
 * the Keycloak SSO session, then redirects back to `/`). A real top-level navigation — Spring
 * Security's logout redirect chain ends on Keycloak's own domain, which a fetch() call can't
 * usefully follow.
 */
export function redirectToLogout() {
    window.location.href = LOGOUT_PATH;
}
