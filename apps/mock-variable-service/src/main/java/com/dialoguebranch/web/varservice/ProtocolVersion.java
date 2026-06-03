/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

package com.dialoguebranch.web.varservice;

/**
 * All endpoints will be available at {base_path}/v{protocol_version} whereby
 * the {protocol_version} is defined by the last available item in this {@code enum}.
 */
public enum ProtocolVersion {

	/** Definition of Protocol Version V1 as a String */
	V1("1");

	/** The protocol version as a String */
	private final String versionName;

	/**
	 * Creates an instance of a ProtocolVersion with the given {@code versionName}.
	 * @param versionName the version as a string.
	 */
	ProtocolVersion(String versionName) {
		this.versionName = versionName;
	}

	/**
	 * Returns the version name.
	 * @return the version name.
	 */
	public String versionName() {
		return versionName;
	}

	/**
	 * Creates an instance of a ProtocolVersion for a given (valid) versionName. If the versionName
	 * is not recognized, this method will throw an exception.
	 *
	 * @param versionName the name of the version.
	 * @return the corresponding {@link ProtocolVersion} object.
	 * @throws IllegalArgumentException in case the versionName is not recognized.
	 */
	public static ProtocolVersion forVersionName(String versionName)
			throws IllegalArgumentException {
		for (ProtocolVersion value : ProtocolVersion.values()) {
			if (value.versionName.equals(versionName))
				return value;
		}
		throw new IllegalArgumentException("Version not found: " + versionName);
	}

	/**
	 * Returns the latest known protocol version as a {@link ProtocolVersion} object.
	 * @return the latest known protocol version as a {@link ProtocolVersion} object.
	 */
	public static ProtocolVersion getLatestVersion() {
		return ProtocolVersion.values()[ProtocolVersion.values().length-1];
	}

}
