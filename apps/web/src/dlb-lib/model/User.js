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
 * A User object models the user of this client app: username and roles, as reported by the BFF's
 * {@code /whoami} endpoint. The BFF holds the actual Keycloak access/refresh token server-side
 * (and refreshes it transparently) — this app never sees, or needs to track, a token at all.
 *
 * @author Harm op den Akker
 */
export class User {

    // ---------------------------------------
    // ---------- Constructor(s) -------------
    // ---------------------------------------

    constructor(name, roles) {
        this._name = name;
        this._roles = roles;
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

    // -----------------------------------
    // ---------- Other Methods ----------
    // -----------------------------------

    /**
     * Returns a human readable String representation of this User object.
     * @returns a human readable String representation of this User object.
     */
   toString() {
       var result = "" +
       "\n{" +
       "\n  name: " + this._name +
       "\n  roles: " + this._roles +
       "\n}";
       return result;
   }

}
