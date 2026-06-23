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
package com.dialoguebranch.web.service.controller.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import nl.rrd.utils.json.JsonObject;

/**
 * Represents the JSON response body returned by the Keycloak token endpoint after a successful
 * login or token refresh, containing the access token, refresh token, and their respective
 * expiration times.
 *
 * @author Harm op den Akker
 */
public class KeycloakTokenResponse extends JsonObject {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("refresh_expires_in")
    private Integer refreshExpiresIn;

    @JsonProperty("not-before-policy")
    private Integer notBeforePolicy;

    @JsonProperty("session_state")
    private String sessionState;

    @JsonProperty("scope")
    private String scope;

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /**
     * Creates an instance of {@link KeycloakTokenResponse} with all fields populated.
     *
     * @param accessToken the JWT access token.
     * @param tokenType the token type, typically {@code "Bearer"}.
     * @param expiresIn the access token lifetime in seconds.
     * @param refreshToken the JWT refresh token.
     * @param refreshExpiresIn the refresh token lifetime in seconds.
     * @param notBeforePolicy the not-before policy timestamp.
     * @param sessionState the Keycloak session state identifier.
     * @param scope the granted OAuth scopes.
     */
    public KeycloakTokenResponse(String accessToken, String tokenType, Integer expiresIn,
                                 String refreshToken, Integer refreshExpiresIn,
                                 Integer notBeforePolicy, String sessionState, String scope) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.refreshExpiresIn = refreshExpiresIn;
        this.notBeforePolicy = notBeforePolicy;
        this.sessionState = sessionState;
        this.scope = scope;
    }

    // ----------------------------------------------------------- //
    // -------------------- Getters & Setters -------------------- //
    // ----------------------------------------------------------- //

    /** Returns the JWT access token. @return the access token. */
    public String getAccessToken() {
        return accessToken;
    }

    /** Sets the JWT access token. @param accessToken the access token. */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /** Returns the token type (typically {@code "Bearer"}). @return the token type. */
    public String getTokenType() {
        return tokenType;
    }

    /** Sets the token type. @param tokenType the token type. */
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    /** Returns the access token lifetime in seconds. @return the expiration in seconds. */
    public Integer getExpiresIn() {
        return expiresIn;
    }

    /** Sets the access token lifetime in seconds. @param expiresIn the expiration in seconds. */
    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    /** Returns the JWT refresh token. @return the refresh token. */
    public String getRefreshToken() {
        return refreshToken;
    }

    /** Sets the JWT refresh token. @param refreshToken the refresh token. */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /** Returns the refresh token lifetime in seconds. @return the expiration in seconds. */
    public Integer getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    /** Sets the refresh token lifetime in seconds. @param refreshExpiresIn the expiration in seconds. */
    public void setRefreshExpiresIn(Integer refreshExpiresIn) {
        this.refreshExpiresIn = refreshExpiresIn;
    }

    /** Returns the not-before policy timestamp. @return the not-before policy. */
    public Integer getNotBeforePolicy() {
        return notBeforePolicy;
    }

    /** Sets the not-before policy timestamp. @param notBeforePolicy the not-before policy. */
    public void setNotBeforePolicy(Integer notBeforePolicy) {
        this.notBeforePolicy = notBeforePolicy;
    }

    /** Returns the Keycloak session state identifier. @return the session state. */
    public String getSessionState() {
        return sessionState;
    }

    /** Sets the Keycloak session state identifier. @param sessionState the session state. */
    public void setSessionState(String sessionState) {
        this.sessionState = sessionState;
    }

    /** Returns the granted OAuth scopes. @return the scopes string. */
    public String getScope() {
        return scope;
    }

    /** Sets the granted OAuth scopes. @param scope the scopes string. */
    public void setScope(String scope) {
        this.scope = scope;
    }
}
