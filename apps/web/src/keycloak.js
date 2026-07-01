import Keycloak from "keycloak-js";
import config from "./config.js";

const keycloak = new Keycloak({
    url: config.keycloak.url,
    realm: config.keycloak.realm,
    clientId: config.keycloak.clientId,
});

/**
 * Initialises the Keycloak adapter. Resolves once Keycloak has determined whether an existing
 * session exists (via the silent-check-sso iframe), without forcing a redirect for anonymous
 * visitors.
 *
 * @returns {Promise<boolean>} whether the user is authenticated.
 */
export async function initKeycloak() {
    const authenticated = await keycloak.init({
        onLoad: "check-sso",
        silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html",
        pkceMethod: "S256",
    });

    // Centralise proactive token refresh here so components don't each run their own timer.
    keycloak.onTokenExpired = () => {
        keycloak.updateToken(30).catch(() => keycloak.login());
    };
    setInterval(() => {
        if (keycloak.authenticated) keycloak.updateToken(30).catch(() => keycloak.login());
    }, 10000);

    return authenticated;
}

export default keycloak;
