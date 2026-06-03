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

/* eslint-disable no-unused-vars */

/** 
 * The following constants may be used throughout the web app for logging purposes.
 */
export const LOG_LEVEL_INFO = 0;
export const LOG_LEVEL_DEBUG = 1;
export const LOG_LEVEL_NAMES = [
    "INFO",
    "DEBUG"
];

export class AbstractLogger {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    constructor(logLevel) {
        if (this.constructor == AbstractLogger) {
            throw new Error("Abstract class AbstractLogger can not be instantiated.");
        }
        this._logLevel = logLevel;
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    /**
     * Sets the level at which log messages should be displayed.
     * @param {number} logLevel the log level.
     */
    set logLevel(logLevel) {
        this._logLevel = logLevel;
    }

    get logLevel() {
        return this._logLevel;
    }

    // -----------------------------------
    // ---------- Other Methods ----------
    // -----------------------------------

    info(logtag, message) {
        this.writeLogEntry("INFO",logtag,message);
    }

    warn(logtag, message) {
        this.writeLogEntry("WARN",logtag,message);
    }

    error(logtag, message) {
        this.writeLogEntry("ERROR",logtag,message);
    }

    debug(logtag, message) {
        if(this._logLevel >= LOG_LEVEL_DEBUG) {
            this.writeLogEntry("DEBUG",logtag,message);
        }
    }

    // --------------------------------------
    // ---------- Abstract Methods ----------
    // --------------------------------------

    writeLogEntry(level, logtag, message) {
        throw new Error("Method 'writeLogEntry' must be implemented by a subclass.");
    }

}
