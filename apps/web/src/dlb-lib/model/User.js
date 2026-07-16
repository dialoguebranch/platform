/* @license
 *
 *                Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
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

/**
 * A User object models the user of this client app, based on the information decoded from the
 * Keycloak access token obtained via the Authorization Code + PKCE flow (i.e., username, roles,
 * the access token itself and its expiration time). Keycloak manages token refresh internally
 * (see src/keycloak.js), so this model has no notion of a refresh token.
 *
 * @author Harm op den Akker
 */
export class User {

    // ---------------------------------------
    // ---------- Constructor(s) -------------
    // ---------------------------------------

    constructor(name, roles, accessToken, accessTokenExpirationSeconds) {
        this._name = name;
        this._roles = roles;
        this._accessToken = accessToken;
        this._accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this._createdAt = Date.now();
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    get name() {
        return this._name;
    }

    get roles() {
        return this._roles;
    }

    get accessToken() {
        return this._accessToken;
    }

    get accessTokenExpirationSeconds() {
        return this._accessTokenExpirationSeconds;
    }

    get createdAt() {
        return this._createdAt;
    }

    // -----------------------------------
    // ---------- Other Methods ----------
    // -----------------------------------

    /**
     * Returns the number of seconds that the access token has left to live. If this number is 0 or less, it has expired.
     * 
     * @returns the number of seconds that the access token has left to live (can be negative).
     */
    get accessTokenSecondsToLive() {
        return ((this._createdAt + (this._accessTokenExpirationSeconds * 1000)) - Date.now()) / 1000;
    }

    /**
     * Returns a human readable String representation of this User object.
     * @returns a human readable String representation of this User object.
     */
   toString() {
       var result = "" +
       "\n{" + 
       "\n  name: " + this._name +
       "\n  roles: " + this._roles +
       "\n  accessToken: " + this._accessToken +
       "\n  accessTokenExpirationSeconds: " + this._accessTokenExpirationSeconds +
       "\n  createdAt: " + this._createdAt +
       "\n}";
       return result;
   }

}
