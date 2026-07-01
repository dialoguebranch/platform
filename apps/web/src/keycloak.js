import Keycloak from "keycloak-js";
import config from "./config.js";

const keycloak = new Keycloak({
    url: config.keycloak.url,
    realm: config.keycloak.realm,
    clientId: config.keycloak.clientId,
});

/**
 * Initialises the Keycloak adapter. If the user has no valid session, this redirects the whole
 * page to Keycloak's hosted login page — there is no in-app login screen. Only resolves once
 * the user is authenticated (the redirect abandons this page load otherwise).
 *
 * @returns {Promise<boolean>} whether the user is authenticated (always true once resolved).
 */
export async function initKeycloak() {
    const authenticated = await keycloak.init({
        onLoad: "login-required",
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
