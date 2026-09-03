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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provides static access to a small number of shared service-context values. Initialized by Spring
 * at startup via constructor injection so that its static methods remain callable from non-Spring
 * code without modification.
 */
@Component
public class ServiceContext {

	private static DlbProperties dlbProperties;

	/**
	 * Creates an instance of {@link ServiceContext} and stores the given {@link DlbProperties}
	 * in a static field so that the static accessor methods remain usable from non-Spring code.
	 *
	 * @param dlbProperties the application configuration properties.
	 */
	@Autowired
	public ServiceContext(DlbProperties dlbProperties) {
		ServiceContext.dlbProperties = dlbProperties;
	}

	/**
	 * Returns the public base URL of this service instance as configured in {@link DlbProperties}.
	 *
	 * @return the base URL string.
	 */
	public static String getBaseUrl() {
		return dlbProperties.getBaseUrl();
	}

	/**
	 * Returns the URL path component of this service's base URL.
	 *
	 * @return the base path string, e.g. {@code "/dlb-web-service"}.
	 */
	public static String getBasePath() {
		String url = getBaseUrl();
		try {
			return new URI(url).getPath();
		} catch (URISyntaxException ex) {
			throw new RuntimeException("Invalid base URL: " + url + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * Returns the version name of the latest supported {@link ProtocolVersion}.
	 *
	 * @return the latest version name string, e.g. {@code "1"}.
	 */
	public static String getCurrentVersion() {
		ProtocolVersion[] versions = ProtocolVersion.values();
		return versions[versions.length - 1].versionName();
	}
}
