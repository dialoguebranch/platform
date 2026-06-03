/* @license
 *
 *                Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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

export class ServerInfo {

    // ---------------------------------------
    // ---------- Constructor(s) -------------
    // ---------------------------------------

    constructor(serviceVersion, protocolVersion, build, upTime) {
        this._serviceVersion = serviceVersion;
        this._protocolVersion = protocolVersion;
        this._build = build;
        this._upTime = upTime;
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    set serviceVersion(serviceVersion) {
        this._serviceVersion = serviceVersion;
    }

    get serviceVersion() {
        return this._serviceVersion;
    }

    set protocolVersion(protocolVersion) {
        this._protocolVersion = protocolVersion;
    }

    get protocolVersion() {
        return this._protocolVersion;
    }

    set build(build) {
        this._build = build;
    }

    get build() {
        return this._build;
    }

    set upTime(upTime) {
        this._upTime = upTime;
    }

    get upTime() {
        return this._upTime;
    }

}
