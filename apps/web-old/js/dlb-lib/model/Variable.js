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
 * A Variable models a DialogueBranch Variable object, including its name, value, lastUpdatedTime
 * and updatedTimeZone.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
export class Variable {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    constructor(name, value, updatedTime, updatedTimeZone) {
        this._name = name;
        this._value = value;
        this._updatedTime = updatedTime;
        this._updatedTimeZone = updatedTimeZone;
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    /**
     * Returns the unique name of this variable.
     * @returns the unique name of this variable.
     */
    get name() {
        return this._name;
    }

    /**
     * Sets the unique name of this variable.
     * @param {String} name the unique name of this variable.
     */
    set name(name) {
        this._name = name;
    }

    /**
     * Returns the value of this variable as an Object.
     * @returns the value of this variable as an Object.
     */
    get value() {
        return this._value;
    }

    /**
     * Sets the value of this variable as an Object.
     * @param {Object} name the value of this variable as an Object.
     */
    set value(value) {
        this._value = value;
    }

    /**
     * Returns the epoch timestamp when this variable was last updated in milliseconds.
     * @returns the epoch timestamp when this variable was last updated in milliseconds.
     */
    get updatedTime() {
        return this._updatedTime;
    }

    /**
     * Sets the epoch timestamp when this variable was last updated in milliseconds.
     * @param {Number} name the epoch timestamp when this variable was last updated in milliseconds.
     */
    set updatedTime(updatedTime) {
        this._updatedTime = updatedTime;
    }

    /**
     * Returns the time zone in which this variable was last updated (as IANA code, e.g. "Europe/Lisbon").
     * @returns the time zone in which this variable was last updated (as IANA code, e.g. "Europe/Lisbon").
     */
    get updatedTimeZone() {
        return this._updatedTimeZone;
    }

     /**
     * Sets the time zone in which this variable was last updated (as IANA code, e.g. "Europe/Lisbon").
     * @param {String} updatedTimeZone the time zone in which this variable was last updated (as IANA code, e.g. "Europe/Lisbon").
     */
    set updatedTimeZone(updatedTimeZone) {
        this._updatedTimeZone = updatedTimeZone;
    }

    // ---------------------------------------
    // ---------- Other Methods --------------
    // ---------------------------------------

    /**
     * Returns a human-readable string representation of how long ago this variable was updated,
     * e.g. "just now", or "5 days ago".
     */
    getReadableTimeSinceLastUpdate() {
        var secondsAgo = Math.floor((new Date() - new Date(this._updatedTime)) / 1000);
    
        const years = Math.floor(secondsAgo / 31536000);

        if (years > 1) {
            return years + " years ago";
        }

        if (years === 1) {
            return years + " year ago";
        }

        const months = Math.floor(secondsAgo / 2628000);
        if (months > 1) {
            return months + " months ago";
        }
        if (months === 1) {
            return months + " month ago";
        }

        const days = Math.floor(secondsAgo / 86400);
        if (days > 1) {
            return days + " days ago";
        }
        if (days === 1) {
            return days + " day ago";
        }

        const hours = Math.floor(secondsAgo / 3600);
        if (hours > 1) {
            return hours + " hours ago";
        }
        if (hours === 1) {
            return hours + " hour ago";
        }

        const minutes = Math.floor(secondsAgo / 60);
        if (minutes > 1) {
            return minutes + " minutes ago";
        }
        if (minutes === 1) {
            return minutes + " minute ago";
        }

        return "Just now";
        
    }

}
