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

package com.dialoguebranch.web.service;

/**
 * All endpoints will be available at {base_path}/{protocol_version} whereby
 * the {protocol_version} is defined by the last available item in this {@code enum}.
 */
public enum ProtocolVersion {

	/** Protocol version 1 — the first and currently latest API version. */
	V1("1");
	
	private final String versionName;
	
	ProtocolVersion(String versionName) {
		this.versionName = versionName;
	}
	
	/**
	 * Returns the version name string for this protocol version (e.g. {@code "1"}).
	 *
	 * @return the version name string.
	 */
	public String versionName() {
		return versionName;
	}
	
	/**
	 * Returns the {@link ProtocolVersion} matching the given version name string.
	 *
	 * @param versionName the version name string to look up, e.g. {@code "1"}.
	 * @return the matching {@link ProtocolVersion}.
	 * @throws IllegalArgumentException if no matching version is found.
	 */
	public static ProtocolVersion forVersionName(String versionName)
			throws IllegalArgumentException {
		for (ProtocolVersion value : ProtocolVersion.values()) {
			if (value.versionName.equals(versionName))
				return value;
		}
		throw new IllegalArgumentException("Version not found: " +
				versionName);
	}

	/**
	 * Returns the latest supported {@link ProtocolVersion} (the last-defined enum constant).
	 *
	 * @return the latest {@link ProtocolVersion}.
	 */
	public static ProtocolVersion getLatestVersion() {
		return ProtocolVersion.values()[ProtocolVersion.values().length-1];
	}
}
