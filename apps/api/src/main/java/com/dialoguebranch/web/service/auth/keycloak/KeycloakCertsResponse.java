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

package com.dialoguebranch.web.service.auth.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import nl.rrd.utils.json.JsonObject;

import java.util.ArrayList;

public class KeycloakCertsResponse extends JsonObject {

    @JsonProperty("keys")
    private ArrayList<KeycloakKey> keys;

    public KeycloakCertsResponse(ArrayList<KeycloakKey> keys) {
        this.keys = keys;
    }

    public KeycloakCertsResponse() { }

    public ArrayList<KeycloakKey> getKeys() {
        return keys;
    }

    public void setKeys(ArrayList<KeycloakKey> keys) {
        this.keys = keys;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("KeycloakCertsResponse{");
        for(KeycloakKey key : keys) {
            result.append(key.toString()).append("\n");
        }
        result.append("}");
        return result.toString();
    }
}
