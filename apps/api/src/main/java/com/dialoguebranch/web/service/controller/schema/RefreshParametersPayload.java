/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

import com.dialoguebranch.web.service.controller.AuthController;
import nl.rrd.utils.json.JsonObject;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@link RefreshParametersPayload} object models the information that is sent in the request body
 * of a call to the /auth/refresh end-point as handled by the {@link AuthController}, which can be
 * serialized / deserialized to the following JSON Format:
 *
 * <pre>
 * {
 *   "refresh_token": "string"
 * }</pre>
 *
 * @author Harm op den Akker
 */
public class RefreshParametersPayload extends JsonObject {

    @Schema(description = "A refresh token to obtain a new access token",
            example = "See https://jwt.io/")
    private String refresh_token = null;

    // --------------------------------------------------------
    // -------------------- Constructor(s) --------------------
    // --------------------------------------------------------

    /**
     * Creates an instance of an empty {@link RefreshParametersPayload}.
     */
    public RefreshParametersPayload() { }

    /**
     * Creates an instance of a {@link RefreshParametersPayload} with the given {@code
     * refresh_token}.
     *
     * @param refresh_token the refresh token.
     */
    public RefreshParametersPayload(String refresh_token) {
        this.refresh_token = refresh_token;
    }

    // -----------------------------------------------------------
    // -------------------- Getters & Setters --------------------
    // -----------------------------------------------------------

    /**
     * Returns the refresh token.
     *
     * @return the refresh token.
     */
    public String getRefreshToken() {
        return this.refresh_token;
    }

    /**
     * Sets the refresh token.
     *
     * @param refresh_token the refresh token.
     */
    public void setRefreshToken(String refresh_token) {
        this.refresh_token = refresh_token;
    }

}
