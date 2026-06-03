/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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
 * Configuration of the Dialogue Branch Web Service. This is initialized from resources {@code
 * service.properties} and {@code deployment.properties}. Known property keys are defined as
 * constants in this class.
 *
 * @author Harm op den Akker
 * @author Dennis Hofs
 * @author Tessa Beinema
 */
@AppComponent
public class Configuration extends LinkedHashMap<String,String> {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final Object LOCK = new Object();

	private static Configuration instance = null;

	// -------------------------------------------------------------
	// -------------------- Known property keys --------------------
	// -------------------------------------------------------------

	// ---------- General

	/** Name of the config parameter that defines the Web Service's version (as a String) */
	public static final String VERSION = "version";

	/** Name of the config parameter that defines the 'build time' string */
	public static final String BUILD_TIME = "buildTime";

	/** Name of the config parameter that defines the base URL where this service is running */
	public static final String BASE_URL = "baseUrl";

	/** Name of the config parameter that defines the data directory for the Web Service */
	public static final String DATA_DIR = "dataDir";

	// ---------- Settings

	/**
	 * Name of the config parameter indicating whether the service allows the creation of anonymous
	 * users through its REST API.
	 */
	public static final String ALLOW_ANONYMOUS_USERS = "allowAnonymousUsers";

	// ---------- Database Configuration

	public static final String MARIADB_HOST = "mariadbHost";

	public static final String MARIADB_PORT = "mariadbPort";

	public static final String MARIADB_USER = "mariadbUser";

	public static final String MARIADB_PASSWORD = "mariadbPassword";

	public static final String MARIADB_DATABASE = "mariadbDatabase";

	// ---------- Authentication Configuration

	/**
	 * Name of the config parameter indicating the authentication service to use, which can be set
	 * to either "keycloak", or "native".
	 */
	public static final String AUTH_SERVICE = "authService";

	/**
	 * Constant defining the value of AUTH_SERVICE indicating that Keycloak should be used.
	 */
	public static final String AUTH_SERVICE_KEYCLOAK = "keycloak";

	/**
	 * Constant defining the value of AUTH_SERVICE indicating that the Native auth system should be
	 * used.
	 */
	public static final String AUTH_SERVICE_NATIVE = "native";

	/**
	 * Name of the config parameter to define the Keycloak base url.
	 */
	public static final String AUTH_KEYCLOAK_BASEURL = "authKeycloakBaseUrl";

	/**
	 * Name of the config parameter to define the Keycloak realm.
	 */
	public static final String AUTH_KEYCLOAK_REALM = "authKeycloakRealm";

	/**
	 * Name of the config parameter to define the Keycloak client ID.
	 */
	public static final String AUTH_KEYCLOAK_CLIENT_ID = "authKeycloakClientId";

	/**
	 * Name of the config parameter to define the Keycloak Client Secret.
	 */
	public static final String AUTH_KEYCLOAK_CLIENT_SECRET = "authKeycloakClientSecret";

	/**
	 * Name of the config parameter that defines the JSON Web Token (JWT) 'secret' used to encrypt
	 * and decrypt ACCESS TOKENS.
	 */
	public static final String AUTH_NATIVE_JWT_ACCESS_TOKEN_SECRET = "authNativeJwtAccessTokenSecret";

	/**
	 * Name of the config parameter that indicates how long access tokens should be active.
	 */
	public static final String AUTH_NATIVE_ACCESS_TOKEN_EXPIRATION_SECONDS = "authNativeAccessTokenExpirationSeconds";

	/**
	 * Name of the config parameter that defines the JSON Web Token (JWT) 'secret' used to encrypt
	 * and decrypt REFRESH TOKENS.
	 */
	public static final String AUTH_NATIVE_JWT_REFRESH_TOKEN_SECRET = "authNativeJwtRefreshTokenSecret";

	/**
	 * Name of the config parameter that indicates how long refresh tokens should be active
	 * (typically much longer).
	 */
	public static final String AUTH_NATIVE_REFRESH_TOKEN_EXPIRATION_SECONDS = "authNativeRefreshTokenExpirationSeconds";

	// ---------- External Variable Service

	/**
	 * Name of the config parameter that defines whether an External Variable Service should be used
	 * by this Web Service or not
	 */
	public static final String EXTERNAL_VARIABLE_SERVICE_ENABLED = "externalVariableServiceEnabled";

	/**
	 * Name of the config parameter that defines the base URL where the External Variable Service is
	 * running
	 */
	public static final String EXTERNAL_VARIABLE_SERVICE_URL = "externalVariableServiceUrl";

	/**
	 * Name of the config parameter that defines the API Version that should be used with the
	 * configured External Variable Service
	 */
	public static final String EXTERNAL_VARIABLE_SERVICE_API_VERSION
			= "externalVariableServiceAPIVersion";

	/**
	 * Name of the config parameter that defines the API Key used to authenticate with the External
	 * Variable Service
	 */
	public static final String EXTERNAL_VARIABLE_SERVICE_API_KEY = "externalVariableServiceAPIKey";

	// ---------- Azure Data Lake

	/**
	 * Name of the config parameter that defines whether synchronizing log files to an Azure Data
	 * Lake should be enabled or not (this is an experimental feature).
	 */
	public static final String AZURE_DATA_LAKE_ENABLED = "azureDataLakeEnabled";

	/**
	 * Name of the config parameter that defines which authentication method to use for connecting
	 * to an Azure Data Lake.
	 */
	public static final String AZURE_DATA_LAKE_AUTHENTICATION_METHOD
			= "azureDataLakeAuthenticationMethod";

	/** Name of the config parameter that defines the Azure Data Lake account name */
	public static final String AZURE_DATA_LAKE_ACCOUNT_NAME = "azureDataLakeAccountName";

	/** Name of the config parameter that defines the Azure Data Lake account key */
	public static final String AZURE_DATA_LAKE_ACCOUNT_KEY = "azureDataLakeAccountKey";

	/** Name of the config parameter that defines the Azure Data Lake SAS Account URL */
	public static final String AZURE_DATA_LAKE_SAS_ACCOUNT_URL = "azureDataLakeSASAccountUrl";

	/** Name of the config parameter that defines the Azure Data Lake SAS Token */
	public static final String AZURE_DATA_LAKE_SAS_TOKEN = "azureDataLakeSASToken";

	/** Name of the config parameter that defines the Azure Data Lake File System name */
	public static final String AZURE_DATA_LAKE_FILE_SYSTEM_NAME = "azureDataLakeFileSystemName";

	// --------------------------------------------------------------
	// -------------------- Hardcoded parameters --------------------
	// --------------------------------------------------------------

	/** Hardcoded folder name to use for storing the general Web Service log files */
	public static final String DIRECTORY_NAME_APPLICATION_LOGS = "logs";

	/** Hardcoded folder name for storing ongoing dialogue logs */
	public static final String DIRECTORY_NAME_DIALOGUES = "dialogues";

	/** Hardcoded folder name for storing the user-specific Variable Stores */
	public static final String DIRECTORY_NAME_VARIABLES = "variables";

	// -------------------------------------------------------------------------
	// -------------------- Constructor(s) & Initialization --------------------
	// -------------------------------------------------------------------------

	/**
	 * Returns the configuration. At startup of the service it should be initialized with
	 * {@link #loadProperties(URL) loadProperties()}.
	 * 
	 * @return the configuration.
	 */
	public static Configuration getInstance() {
		synchronized (LOCK) {
			if (instance == null)
				instance = new Configuration();
			return instance;
		}
	}

	/**
	 * This private constructor is used in {@link #getInstance() getInstance()}.
	 */
	private Configuration() { }

	/**
	 * Loads the resource service.properties or deployment.properties into this configuration. This
	 * should only be called once at startup of the service.
	 * 
	 * @param url the {@link URL} of a service.properties or deployment.properties file.
	 * @throws IOException if a reading error occurs.
	 */
	public void loadProperties(URL url) throws IOException {
		Properties props = new Properties();
		try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		for (String name : props.stringPropertyNames()) {
			put(name, props.getProperty(name));
		}
	}

	// ----- Note that this Configuration is a LinkedHashMap, so all parameters can simply be
	// ----- retrieved by using this.get("parameterName") however, using the getters below we can
	// ----- add some robustness (e.g. null checking), and some convenience (readability) for devs

	// ----------------------------------------------------------
	// -------------------- Getters: General --------------------
	// ----------------------------------------------------------

	/**
	 * Returns the application version identifier (e.g. "1.0.0") as a String.
	 * 
	 * @return the application version identifier (e.g. "1.0.0") as a String.
	 */
	public String getVersion() {
		if(get(VERSION) == null) return "";
		else return get(VERSION);
	}

	/**
	 * Returns a date-time {@link String} representing the date and time that this version of the
	 * deployed web service was built.
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

	/**
	 * Returns the location of the data directory used by the web service as a String.
	 *
	 * @return the location of the data directory used by the web service as a String.
	 */
	public String getDataDir() {
		if(get(DATA_DIR) == null) return "";
		else return get(DATA_DIR);
	}

	// ----------------------------------------------------------
	// -------------------- Getters: Settings -------------------
	// ----------------------------------------------------------

	/**
	 * Returns whether this Web Service allows the creation of Anonymous User accounts.
	 *
	 * @return whether this Web Service allows the creation of Anonymous User accounts.
	 */
	public boolean getAllowAnonymousUsers() {
		return Boolean.parseBoolean(get(ALLOW_ANONYMOUS_USERS));
	}

	// -------------------------------------------------------------------------
	// -------------------- Getters: Database Configuration --------------------
	// -------------------------------------------------------------------------

	public String getMariadbHost() {
		if (get(MARIADB_HOST) == null) return "";
		return get(MARIADB_HOST);
	}

	public int getMariadbPort() {
		if (get(MARIADB_PORT) == null) return 3306;
		try {
			return Integer.parseInt(get(MARIADB_PORT));
		} catch (NumberFormatException ex) {
			return 3306;
		}
	}

	public String getMariadbUser() {
		if (get(MARIADB_USER) == null) return "";
		return get(MARIADB_USER);
	}

	public String getMariadbPassword() {
		if (get(MARIADB_PASSWORD) == null) return "";
		return get(MARIADB_PASSWORD);
	}

	public String getMariadbDatabase() {
		if (get(MARIADB_DATABASE) == null) return "";
		return get(MARIADB_DATABASE);
	}

	// ----------------------------------------------------------------- //
	// -------------------- Getters: Authentication -------------------- //
	// ----------------------------------------------------------------- //

	public String getAuthService() {
		if(get(AUTH_SERVICE) == null) return "";
		else return get(AUTH_SERVICE);
	}

	public String getKeycloakBaseUrl() {
		if(get(AUTH_KEYCLOAK_BASEURL) == null) return "";
		else return get(AUTH_KEYCLOAK_BASEURL);
	}

	public String getKeycloakRealm() {
		if(get(AUTH_KEYCLOAK_REALM) == null) return "";
		else return get(AUTH_KEYCLOAK_REALM);
	}

	public String getKeycloakClientId() {
		if(get(AUTH_KEYCLOAK_CLIENT_ID) == null) return "";
		else return get(AUTH_KEYCLOAK_CLIENT_ID);
	}

	public String getKeycloakClientSecret() {
		if(get(AUTH_KEYCLOAK_CLIENT_SECRET) == null) return "";
		else return get(AUTH_KEYCLOAK_CLIENT_SECRET);
	}

	/**
	 * Returns the secret key used for encoding/decoding JWT Access Tokens.
	 *
	 * @return the secret key used for encoding/decoding JWT Access Tokens.
	 */
	public String getJwtAccessTokenSecret() {
		if(get(AUTH_NATIVE_JWT_ACCESS_TOKEN_SECRET) == null) return "";
		else return get(AUTH_NATIVE_JWT_ACCESS_TOKEN_SECRET);
	}

	/**
	 * Returns the number of seconds a newly generated access token should be valid for under the
	 * native authentication service.
	 *
	 * @return the access token expiration time in seconds.
	 */
	public int getAccessTokenExpirationSeconds() {
		if (get(AUTH_NATIVE_ACCESS_TOKEN_EXPIRATION_SECONDS) == null) return 300;
		try {
			return Integer.parseInt(get(AUTH_NATIVE_ACCESS_TOKEN_EXPIRATION_SECONDS));
		} catch (NumberFormatException ex) {
			return 300;
		}
	}

	/**
	 * Returns the secret key used for encoding/decoding JWT Refresh Tokens.
	 *
	 * @return the secret key used for encoding/decoding JWT Refresh Tokens.
	 */
	public String getJwtRefreshTokenSecret() {
		if(get(AUTH_NATIVE_JWT_REFRESH_TOKEN_SECRET) == null) return "";
		else return get(AUTH_NATIVE_JWT_REFRESH_TOKEN_SECRET);
	}

	/**
	 * Returns the number of seconds a newly generated refresh token should be valid for under the
	 * native authentication service.
	 *
	 * @return the refresh token expiration time in seconds.
	 */
	public int getRefreshTokenExpirationSeconds() {
		if (get(AUTH_NATIVE_REFRESH_TOKEN_EXPIRATION_SECONDS) == null) return 1800;
		try {
			return Integer.parseInt(get(AUTH_NATIVE_REFRESH_TOKEN_EXPIRATION_SECONDS));
		} catch (NumberFormatException ex) {
			return 1800;
		}
	}

	// ----------------------------------------------------------------------------
	// -------------------- Getters: External Variable Service --------------------
	// ----------------------------------------------------------------------------

	/**
	 * Returns whether an "External DialogueBranch Variable Service" has been configured to be used.
	 *
	 * @return whether an "External DialogueBranch Variable Service" has been configured to be used.
	 */
	public boolean getExternalVariableServiceEnabled() {
		return Boolean.parseBoolean(get(EXTERNAL_VARIABLE_SERVICE_ENABLED));
	}

	/**
	 * Returns the URL of the External Variable Service, or an empty string if incorrectly
	 * configured.
	 *
	 * @return the URL of the External Variable Service, or an empty string if incorrectly
	 *         configured.
	 */
	public String getExternalVariableServiceURL() {
		if(get(EXTERNAL_VARIABLE_SERVICE_URL) == null) return "";
		else return get(EXTERNAL_VARIABLE_SERVICE_URL);
	}

	/**
	 * Returns the API Version of the External Variable Service as a String, or an empty string if
	 * incorrectly configured.
	 *
	 * @return the API Version of the External Variable Service as a String, or an empty string if
	 *         incorrectly configured.
	 */
	public String getExternalVariableServiceAPIVersion() {
		if(get(EXTERNAL_VARIABLE_SERVICE_API_VERSION) == null) return "";
		else return get(EXTERNAL_VARIABLE_SERVICE_API_VERSION);
	}

	/**
	 * Returns the API Key used to authenticate with the External Variable Service as a String, or
	 * an empty string if incorrectly configured.
	 *
	 * @return the API Key used to authenticate with the External Variable Service as a String, or
	 * an empty string if incorrectly configured.
	 */
	public String getExternalVariableServiceAPIKey() {
		if(get(EXTERNAL_VARIABLE_SERVICE_API_KEY) == null) return "";
		else return get(EXTERNAL_VARIABLE_SERVICE_API_KEY);
	}

	// ------------------------------------------------------------------
	// -------------------- Getters: Azure Data Lake --------------------
	// ------------------------------------------------------------------

	/**
	 * Returns whether the Azure Data Lake is enabled.
	 *
	 * @return {@code true} if the Azure Data Lake is enabled, {@code false} otherwise.
	 */
	public boolean getAzureDataLakeEnabled() {
		return Boolean.parseBoolean(get(AZURE_DATA_LAKE_ENABLED));
	}

	/**
	 * Returns the authentication method that should be used for connecting to the Azure Data Lake
	 * as either "sas-token" or "account-key", or returns the empty string if not configured.
	 *
	 * @return the authentication method that should be used for connecting to the Azure Data Lake.
	 */
	public String getAzureDataLakeAuthenticationMethod() {
		if(get(AZURE_DATA_LAKE_AUTHENTICATION_METHOD) == null) return "";
		else return get(AZURE_DATA_LAKE_AUTHENTICATION_METHOD);
	}

	/**
	 * Returns the Azure Data Lake Account Name, or an empty {@link String} if not configured.
	 *
	 * @return the Azure Data Lake Account Name, or an empty {@link String} if not configured.
	 */
	public String getAzureDataLakeAccountName() {
		if(get(AZURE_DATA_LAKE_ACCOUNT_NAME) == null) return "";
		else return get(AZURE_DATA_LAKE_ACCOUNT_NAME);
	}

	/**
	 * Returns the Azure Data Lake Account Key, or an empty {@link String} if not configured.
	 *
	 * @return the Azure Data Lake Account Key, or an empty {@link String} if not configured.
	 */
	public String getAzureDataLakeAccountKey() {
		if(get(AZURE_DATA_LAKE_ACCOUNT_KEY) == null) return "";
		else return get(AZURE_DATA_LAKE_ACCOUNT_KEY);
	}

	/**
	 * Returns the Azure Storage Account URL used when authenticating with the Azure Data Lake using
	 * an SAS token, or an empty {@link String} if not configured.
	 *
	 * @return the Azure Storage Account URL, or an empty {@link String} if not configured.
	 */
	public String getAzureDataLakeSASAccountUrl() {
		if(get(AZURE_DATA_LAKE_SAS_ACCOUNT_URL) == null) return "";
		else return get(AZURE_DATA_LAKE_SAS_ACCOUNT_URL);
	}

	/**
	 * Returns the Azure SAS Token, or an empty {@link String} if not configured.
	 *
	 * @return the Azure SAS Token, or an empty {@link String} if not configured.
	 */
	public String getAzureDataLakeSASToken() {
		if(get(AZURE_DATA_LAKE_SAS_TOKEN) == null) return "";
		else return get(AZURE_DATA_LAKE_SAS_TOKEN);
	}

	/**
	 * Returns the Azure File System Name, or an empty {@link String} if not configured.
	 *
	 * @return the Azure File System Name, or an empty {@link String} if not configured.
	 */
	public String getAzureDataLakeFileSystemName() {
		if(get(AZURE_DATA_LAKE_FILE_SYSTEM_NAME) == null) return "";
		else return get(AZURE_DATA_LAKE_FILE_SYSTEM_NAME);
	}

	// -----------------------------------------------------------------------
	// -------------------- Getters: Hardcoded parameters --------------------
	// -----------------------------------------------------------------------

	/**
	 * Returns the name of the folder used for storing the application logs.
	 *
	 * @return the name of the folder used for storing the application logs.
	 */
	public String getDirectoryNameApplicationLogs() {
		return DIRECTORY_NAME_APPLICATION_LOGS;
	}

	/**
	 * Returns the name of the folder used for storing dialogue logs.
	 *
	 * @return the name of the folder used for storing dialogue logs.
	 */
	public String getDirectoryNameDialogues() {
		return DIRECTORY_NAME_DIALOGUES;
	}

	/**
	 * Returns the name of the folder used for storing DialogueBranch variable stores.
	 *
	 * @return the name of the folder used for storing DialogueBranch variable stores.
	 */
	public String getDirectoryNameVariables() {
		return DIRECTORY_NAME_VARIABLES;
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
