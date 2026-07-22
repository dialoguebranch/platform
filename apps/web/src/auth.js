import { DocumentFunctions } from './dlb-lib/util/DocumentFunctions.js';

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
 * Ends the session: submits a real top-level POST navigation to the BFF's logout endpoint
 * (RP-initiated — ends both this app's session and the Keycloak SSO session).
 *
 * This can't be `window.location.href = '/logout'` (a GET): Spring Security's `LogoutFilter`
 * only matches `POST /logout` by default, so a GET gets a 404. It also can't be a `fetch()` POST:
 * that was tried first, and while it does correctly end *this app's own* session, a fetch's
 * internal redirect-following does not carry cookies across the origin change to Keycloak's own
 * logout endpoint the way a real navigation does — so Keycloak's SSO session survives, and the
 * very next login redirect (see redirectToLogin) silently re-authenticates against it without
 * ever showing a login prompt, which looks exactly like logout did nothing.
 *
 * A real browser navigation doesn't have that problem (this is exactly how login already works),
 * but a plain navigation can't set the required `X-XSRF-TOKEN` header either — so this builds and
 * submits an actual `<form>`, carrying the token as a hidden field instead (the BFF's CSRF
 * handler accepts either, see SecurityConfig's SpaCsrfTokenRequestHandler). The BFF's own
 * redirect back to this app after a successful logout is handled server-side (see
 * postLoginRedirectUrl in the BFF's application.yml, reused for both login and logout).
 */
export function redirectToLogout() {
    const csrfToken = DocumentFunctions.getCookie('XSRF-TOKEN');
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = LOGOUT_PATH;
    if (csrfToken) {
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = '_csrf';
        csrfInput.value = csrfToken;
        form.appendChild(csrfInput);
    }
    document.body.appendChild(form);
    form.submit();
}
