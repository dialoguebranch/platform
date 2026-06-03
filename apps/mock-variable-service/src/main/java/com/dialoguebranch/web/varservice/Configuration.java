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

import nl.rrd.utils.AppComponent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serial;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration of the Dialogue Branch External Variable Service Dummy. This is initialized from
 * resources service.properties and deployment.properties. Known property keys are defined as
 * constants in this class.
 *
 * <p>This class is an implementation of a {@link LinkedHashMap}, so any configuration property
 * can simply be retrieved using that super-class's {@link LinkedHashMap#get} method.</p>
 *
 * <p>Any configured value from service.properties and deployment.properties may be overridden by
 * using an environment variable. Whether you are using the simple {@link LinkedHashMap#get} methods
 * or the provided convenience methods (e.g. {@link Configuration#getBaseUrl}), this configuration
 * will always first check the existence of an environment variable, before returning the value
 * as defined in the .properties files.</p>
 * 
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
@AppComponent
public class Configuration extends LinkedHashMap<String,String> {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final Object LOCK = new Object();

	private static Configuration instance = null;

	// ------------------------------------------------------------- //
	// -------------------- Known property keys -------------------- //
	// ------------------------------------------------------------- //

	// ---------- General

	/** Key name for the "version" parameter. */
	public static final String VERSION = "version";

	/** Key name for the "buildTime" parameter. */
	public static final String BUILD_TIME = "buildTime";

	/** Key name for the "baseUrl" parameter. */
	public static final String BASE_URL = "baseUrl";

	/** Key name for the "dataDir" parameter. */
	public static final String DATA_DIR = "dataDir";

	// ---------- Authentication Configuration

	/**
	 * Name of the config parameter that defines the API Key for authentication between Web Service
	 * and this External Variable Service.
	 */
	public static final String AUTH_API_KEY = "authAPIKey";

	// ------------------------------------------------------------------------- //
	// -------------------- Constructor(s) & Initialization -------------------- //
	// ------------------------------------------------------------------------- //

	/**
	 * Returns the configuration. At startup of the service it should be initialized with {@link
	 * #loadProperties(URL) loadProperties()}.
	 * 
	 * @return the configuration
	 */
	public static Configuration getInstance() {
		synchronized (LOCK) {
			if (instance == null)
				instance = new Configuration();
			return instance;
		}
	}

	/**
	 * This private constructor is used in {@link #getInstance()
	 * getInstance()}.
	 */
	private Configuration() { }

	// ---------------------------------------------------------- //
	// -------------------- Getters: General -------------------- //
	// ---------------------------------------------------------- //

	/**
	 * Returns a date-time {@link String} representing the date and time that this version of the
	 * deployed external variable service was built.
	 *
	 * @return the build-time as a date-time {@link String}.
	 */
	public String getBuildTime() {
		if(get(BUILD_TIME) == null) return "";
		else return get(BUILD_TIME);
	}

	/**
	 * Returns the configured Base URL for the DialogueBranch Web Service.
	 *
	 * @return the configured Base URL for the DialogueBranch Web Service.
	 */
	public String getBaseUrl() {
		if(get(BASE_URL) == null) return "";
		else return get(BASE_URL);
	}

	// ----------------------------------------------------------------- //
	// -------------------- Getters: Authentication -------------------- //
	// ----------------------------------------------------------------- //

	/**
	 * Returns the API Key for authentication between Web Service and this External Variable Service.
	 *
	 * @return the API Key for authentication between Web Service and this External Variable Service.
	 */
	public String getAuthAPIKey() {
		if(get(AUTH_API_KEY) == null) return "";
		else return get(AUTH_API_KEY);
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Loads the resource service.properties or deployment.properties into this configuration. This
	 * should only be called once at startup of the service.
	 *
	 * @param url the URL of service.properties or deployment.properties
	 * @throws IOException if a reading error occurs
	 */
	public void loadProperties(URL url) throws IOException {
		Properties props = new Properties();
		if(url == null) throw new IOException("Cannot load properties file from null.");
		try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		for (String name : props.stringPropertyNames()) {
			put(name, props.getProperty(name));
		}
	}

	@Override
	public String get(Object key) {
		// first try to find the key in the environment variables
		if (key instanceof String stringKey) {
			Map<String, String> env = System.getenv();
			for (String envKey : env.keySet()) {
				if (envKey.equalsIgnoreCase(stringKey)) {
					return env.get(envKey);
				}
			}
		}
		return super.get(key);
	}
}
