/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.web.bff;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * Exposes {@code GET /whoami}, returning the current session's username and Dialogue Branch Web
 * Service roles as plain JSON.
 *
 * <p>Before this Backend-for-Frontend existed, the web client read this directly out of its own
 * decoded access token (see {@code src/main.js} in {@code apps/web}, before this change): the
 * whole point of the BFF is that the browser no longer holds that token at all, so this
 * information has to come from the server instead. The claim is read from the session's access
 * token (already parsed here without re-verifying its signature — it came from our own trusted
 * session, having just been used to call the Dialogue Branch Web Service itself), not from the ID
 * token: Keycloak does not include {@code resource_access} in the ID token by default.</p>
 *
 * @author Dennis Hofs
 */
@RestController
public class WhoAmIController {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String dlbWebServiceClientId;

    /**
     * Instances of this class are constructed through Spring.
     *
     * @param authorizedClientManager fetches (and refreshes) the current session's access token.
     * @param dlbWebServiceClientId the Keycloak client id whose roles to read out of the
     *                              {@code resource_access} claim — {@code dlb-web-service}, kept
     *                              configurable only so a differently-realmed deployment isn't
     *                              stuck with the name hard-coded.
     */
    public WhoAmIController(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${dlb.bff.web-service-client-id}") String dlbWebServiceClientId) {
        this.authorizedClientManager = authorizedClientManager;
        this.dlbWebServiceClientId = dlbWebServiceClientId;
    }

    /**
     * @param authentication the current session's authentication, used to look up its
     *                       authorized client (and access token).
     * @return {@code { "username": ..., "roles": [...] } }.
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoAmI(Authentication authentication) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("keycloak")
                .principal(authentication)
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new IllegalStateException("No authorized client for the current session");
        }
        String accessTokenValue = authorizedClient.getAccessToken().getTokenValue();

        JWTClaimsSet claims;
        try {
            claims = JWTParser.parse(accessTokenValue).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new IllegalStateException("Could not parse the session's access token", e);
        }

        Object usernameClaim = claims.getClaim("preferred_username");
        String username = usernameClaim == null ? "" : usernameClaim.toString();

        List<String> roles = List.of();
        Object resourceAccess = claims.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceAccessMap) {
            Object clientEntry = resourceAccessMap.get(dlbWebServiceClientId);
            if (clientEntry instanceof Map<?, ?> clientEntryMap
                    && clientEntryMap.get("roles") instanceof List<?> rawRoles) {
                roles = rawRoles.stream().map(String::valueOf).toList();
            }
        }

        return Map.of("username", username, "roles", roles);
    }
}
