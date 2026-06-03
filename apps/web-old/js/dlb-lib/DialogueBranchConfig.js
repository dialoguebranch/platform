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

/**
 * A DialogueBranchConfig object contains configurable information that can be loaded from a
 * config.json file in the root directory of the project.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class DialogueBranchConfig {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    /**
     * Creates an empty instance of a DialogueBranchConfig.
     * 
     * @constructor
     */
    constructor() { 
        this._logLevel = null;
        this._baseUrl = null;
    }

    // ------------------------------------
    // ---------- Initialization ----------
    // ------------------------------------

    /**
     * Reads configuration values into this DialogueBranchConfig object in an asynchronous way.
     */
    async loadFromFile() {
        await fetch('config.json')
        .then((response) => {
            return response.json();
        })
        .then((data) => {
            this._baseUrl = data.baseUrl;
            this._logLevel = data.logLevel;
        })
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    /**
     * Returns the logging level to be used while executing. Currently supports the following log levels as defined in AbstractLogger:
     * - LOG_LEVEL_INFO = 0
     * - LOG_LEVEL_DEBUG = 1
     *
     * @returns {number} the logging level to be used while executing.
     */
    get logLevel() {
        return this._logLevel;
    }

    /**
     * Sets the logging level to be used while executing. Currently supports the following log levels as defined in AbstractLogger:
     * - LOG_LEVEL_INFO = 0
     * - LOG_LEVEL_DEBUG = 1
     * 
     * @param {number} logLevel the logging level.
     */
    set logLevel(logLevel) {
        this._logLevel = logLevel;
    }

    /**
     * Returns the common part of the url to be used in all API calls, including the API version to
     * use (e.g. localhost:8080/dlb-web-service/v1).
     *
     * @returns the common part of the url to be used in all API calls.
     */
    get baseUrl() {
        return this._baseUrl;
    }

    /**
     * Sets the common part of the url to be used in all API calls, including the API version to
     * use (e.g. localhost:8080/dlb-web-service/v1).
     *
     * @param {String} baseUrl the common part of the url to be used in all API calls.
     */
    set baseUrl(baseUrl) {
        this._baseUrl = baseUrl;
    }

}
