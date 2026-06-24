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

package com.dialoguebranch.web.varservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the Dialogue Branch External Variable Service, bound from
 * {@code application.yml} under the {@code dlb} prefix. Environment variables follow Spring's
 * relaxed binding convention: {@code DLB_AUTH_API_KEY} overrides {@code dlb.auth.api-key}.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
@ConfigurationProperties(prefix = "dlb")
public class DlbVarServiceProperties {

	private String version = "unknown";
	private String buildTime = "";
	private String baseUrl = "http://localhost:8090/dlb-external-var-service";
	private String dataDir = "config";
	private Auth auth = new Auth();

	public String getVersion() { return version; }
	public void setVersion(String version) { this.version = version; }

	public String getBuildTime() { return buildTime; }
	public void setBuildTime(String buildTime) { this.buildTime = buildTime; }

	public String getBaseUrl() { return baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

	public String getDataDir() { return dataDir; }
	public void setDataDir(String dataDir) { this.dataDir = dataDir; }

	public Auth getAuth() { return auth; }
	public void setAuth(Auth auth) { this.auth = auth; }

	public static class Auth {
		private String apiKey = "";

		public String getApiKey() { return apiKey; }
		public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	}
}
