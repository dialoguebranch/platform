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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Proxies every call under {@code /api} from the Dialogue Branch web client to the Dialogue
 * Branch Web Service, attaching the current session's OAuth2 access token — obtained, and
 * transparently refreshed, from server-side storage the browser never sees.
 *
 * <p>One path, {@code GET /api/v1/info/all}, is reachable without a session at all (see
 * SecurityConfig's {@code permitAll} rule for it — the web client's pre-login reachability check
 * calls it): {@link #accessTokenOrNull} forwards that request with no {@code Authorization}
 * header, matching how the Web Service itself already treats that one endpoint as public.</p>
 *
 * @author Dennis Hofs
 */
@RestController
public class ApiProxyController {

    private final RestClient apiRestClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    /**
     * Instances of this class are constructed through Spring.
     *
     * @param apiRestClient the client used to actually call the Dialogue Branch Web Service.
     * @param authorizedClientManager fetches (and refreshes) the current session's access token.
     */
    public ApiProxyController(RestClient apiRestClient, OAuth2AuthorizedClientManager authorizedClientManager) {
        this.apiRestClient = apiRestClient;
        this.authorizedClientManager = authorizedClientManager;
    }

    /**
     * Forwards the request to the Dialogue Branch Web Service, relaying its status code,
     * headers (minus hop-by-hop ones), and body verbatim.
     *
     * @param request the incoming request.
     * @param authentication the current session's authentication, used to look up its
     *                       authorized client (and access token).
     * @param body the request body, if any.
     * @return the downstream response, relayed as-is.
     */
    @RequestMapping(value = "/api/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE })
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication,
                                         @RequestBody(required = false) byte[] body) {
        String accessToken = accessTokenOrNull(authentication);
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String downstreamPath = request.getRequestURI().substring(contextPath.length())
                .replaceFirst("^/api", "");

        RestClient.RequestBodySpec requestSpec = apiRestClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(uriBuilder -> {
                    var withPath = uriBuilder.path(downstreamPath);
                    var withQuery = request.getQueryString() != null
                            ? withPath.query(request.getQueryString()) : withPath;
                    return withQuery.build();
                })
                .headers(headers -> {
                    String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
                    if (contentType != null) headers.set(HttpHeaders.CONTENT_TYPE, contentType);
                    String accept = request.getHeader(HttpHeaders.ACCEPT);
                    if (accept != null) headers.set(HttpHeaders.ACCEPT, accept);
                    if (accessToken != null) headers.setBearerAuth(accessToken);
                });

        RestClient.RequestHeadersSpec<?> finalSpec =
                (body != null && body.length > 0) ? requestSpec.body(body) : requestSpec;

        return finalSpec.exchange((clientRequest, downstreamResponse) -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.addAll(downstreamResponse.getHeaders());
            responseHeaders.remove(HttpHeaders.TRANSFER_ENCODING);
            responseHeaders.remove(HttpHeaders.CONNECTION);
            return ResponseEntity.status(downstreamResponse.getStatusCode())
                    .headers(responseHeaders)
                    .body(downstreamResponse.getBody().readAllBytes());
        });
    }

    /**
     * @param authentication the current request's authentication — an {@link
     *                        AnonymousAuthenticationToken} for the one {@code permitAll} path
     *                        this controller serves (see the class Javadoc), a real OAuth2
     *                        authentication for every other, authenticated-only path.
     * @return the session's access token, or {@code null} for an anonymous request to the public
     * path.
     */
    private String accessTokenOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("keycloak")
                .principal(authentication)
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new IllegalStateException("No authorized client for the current session");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
