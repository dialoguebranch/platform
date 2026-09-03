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

package com.dialoguebranch.web.service.controller.schema;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@code TechnicalInfoPayload} object consolidates technical, operational metadata about this
 * Dialogue Branch Web Service instance, intended for {@code admin}-only diagnostics.
 *
 * @author Harm op den Akker
 */
public class TechnicalInfoPayload {

	@Schema(description = "The number of currently active (in-memory) UserService instances",
			example = "3")
	private int activeUserServiceCount;

	@Schema(description = "Host name this service saw on the incoming request (as observed by the " +
			"service itself; behind a proxy this is the address the proxy connected to, not a " +
			"public URL)", example = "dlb-web-service")
	private String serverName;

	@Schema(description = "Port this service saw on the incoming request", example = "8089")
	private int serverPort;

	@Schema(description = "Scheme this service saw on the incoming request", example = "http")
	private String scheme;

	@Schema(description = "The base URL this service is configured to identify itself by " +
			"(dlb.base-url)", example = "http://localhost:8089/dlb-web-service")
	private String configuredBaseUrl;

	@Schema(description = "Software version of this service", example = "2.0.7")
	private String serviceVersion;

	@Schema(description = "Date & time this service was built", example = "2026-08-12T09:00:00Z")
	private String buildTime;

	@Schema(description = "Keycloak base URL this service uses to validate tokens",
			example = "http://keycloak:8080/")
	private String keycloakBaseUrl;

	@Schema(description = "Keycloak realm this service validates tokens against",
			example = "dialoguebranch")
	private String keycloakRealm;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link TechnicalInfoPayload}.
	 */
	public TechnicalInfoPayload() { }

	/**
	 * Creates an instance of a {@link TechnicalInfoPayload} with the given {@code
	 * activeUserServiceCount}.
	 *
	 * @param activeUserServiceCount the number of currently active (in-memory) UserService
	 *                               instances.
	 */
	public TechnicalInfoPayload(int activeUserServiceCount) {
		this.activeUserServiceCount = activeUserServiceCount;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the number of currently active (in-memory) UserService instances.
	 * @return the number of currently active (in-memory) UserService instances.
	 */
	public int getActiveUserServiceCount() {
		return activeUserServiceCount;
	}

	/**
	 * Sets the number of currently active (in-memory) UserService instances.
	 * @param activeUserServiceCount the number of currently active (in-memory) UserService
	 *                               instances.
	 */
	public void setActiveUserServiceCount(int activeUserServiceCount) {
		this.activeUserServiceCount = activeUserServiceCount;
	}

	/**
	 * Returns the host name this service saw on the incoming request.
	 * @return the host name this service saw on the incoming request.
	 */
	public String getServerName() {
		return serverName;
	}

	/**
	 * Sets the host name this service saw on the incoming request.
	 * @param serverName the host name this service saw on the incoming request.
	 */
	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	/**
	 * Returns the port this service saw on the incoming request.
	 * @return the port this service saw on the incoming request.
	 */
	public int getServerPort() {
		return serverPort;
	}

	/**
	 * Sets the port this service saw on the incoming request.
	 * @param serverPort the port this service saw on the incoming request.
	 */
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	/**
	 * Returns the scheme this service saw on the incoming request.
	 * @return the scheme this service saw on the incoming request.
	 */
	public String getScheme() {
		return scheme;
	}

	/**
	 * Sets the scheme this service saw on the incoming request.
	 * @param scheme the scheme this service saw on the incoming request.
	 */
	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	/**
	 * Returns the base URL this service is configured to identify itself by.
	 * @return the configured base URL.
	 */
	public String getConfiguredBaseUrl() {
		return configuredBaseUrl;
	}

	/**
	 * Sets the base URL this service is configured to identify itself by.
	 * @param configuredBaseUrl the configured base URL.
	 */
	public void setConfiguredBaseUrl(String configuredBaseUrl) {
		this.configuredBaseUrl = configuredBaseUrl;
	}

	/**
	 * Returns the software version of this service.
	 * @return the software version of this service.
	 */
	public String getServiceVersion() {
		return serviceVersion;
	}

	/**
	 * Sets the software version of this service.
	 * @param serviceVersion the software version of this service.
	 */
	public void setServiceVersion(String serviceVersion) {
		this.serviceVersion = serviceVersion;
	}

	/**
	 * Returns the date &amp; time this service was built.
	 * @return the date &amp; time this service was built.
	 */
	public String getBuildTime() {
		return buildTime;
	}

	/**
	 * Sets the date &amp; time this service was built.
	 * @param buildTime the date &amp; time this service was built.
	 */
	public void setBuildTime(String buildTime) {
		this.buildTime = buildTime;
	}

	/**
	 * Returns the Keycloak base URL this service uses to validate tokens.
	 * @return the Keycloak base URL.
	 */
	public String getKeycloakBaseUrl() {
		return keycloakBaseUrl;
	}

	/**
	 * Sets the Keycloak base URL this service uses to validate tokens.
	 * @param keycloakBaseUrl the Keycloak base URL.
	 */
	public void setKeycloakBaseUrl(String keycloakBaseUrl) {
		this.keycloakBaseUrl = keycloakBaseUrl;
	}

	/**
	 * Returns the Keycloak realm this service validates tokens against.
	 * @return the Keycloak realm.
	 */
	public String getKeycloakRealm() {
		return keycloakRealm;
	}

	/**
	 * Sets the Keycloak realm this service validates tokens against.
	 * @param keycloakRealm the Keycloak realm.
	 */
	public void setKeycloakRealm(String keycloakRealm) {
		this.keycloakRealm = keycloakRealm;
	}

}
