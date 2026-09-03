/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
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

package com.dialoguebranch.web.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Small date/time helpers for the web service.
 *
 * @author Harm op den Akker
 */
public class DateTimeUtils {

	/** Utility class — not instantiated. */
	private DateTimeUtils() {}

	/**
	 * Returns the current date/time in the given time zone, truncated to millisecond precision
	 * (sub-millisecond nanoseconds set to 0). Logged interaction timestamps are serialised to and
	 * parsed back from ISO strings, which also carry only millisecond precision, so truncating
	 * here keeps a written-then-read timestamp equal to the original.
	 *
	 * @param timeZone the time zone.
	 * @return the current millisecond-precision {@link ZonedDateTime} in {@code timeZone}.
	 */
	public static ZonedDateTime nowMs(ZoneId timeZone) {
		return ZonedDateTime.now(timeZone).truncatedTo(ChronoUnit.MILLIS);
	}

}
