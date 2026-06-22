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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Typed configuration for the Dialogue Branch Web Service, bound from {@code application.yml} (and
 * environment variable overrides) via Spring Boot's {@code @ConfigurationProperties} mechanism.
 *
 * <p>All properties live under the {@code dlb} prefix. Environment variables follow the standard
 * Spring Boot relaxed-binding convention: upper-case with {@code _} as separator, prefixed with
 * {@code DLB_}. Examples:</p>
 * <ul>
 *   <li>{@code dlb.base-url} → {@code DLB_BASE_URL}</li>
 *   <li>{@code dlb.mariadb.host} → {@code DLB_MARIADB_HOST}</li>
 *   <li>{@code dlb.auth.keycloak.client-secret} → {@code DLB_AUTH_KEYCLOAK_CLIENT_SECRET}</li>
 * </ul>
 *
 * @author Harm op den Akker
 */
@ConfigurationProperties(prefix = "dlb")
public class DlbProperties {

    // ------------------------------------------------------------- //
    // -------------------- Shared String Constants ---------------- //
    // ------------------------------------------------------------- //

    public static final String AUTH_SERVICE_KEYCLOAK = "keycloak";
    public static final String AUTH_SERVICE_NATIVE = "native";

    public static final String DIRECTORY_NAME_APPLICATION_LOGS = "logs";
    public static final String DIRECTORY_NAME_DIALOGUES = "dialogues";
    public static final String DIRECTORY_NAME_VARIABLES = "variables";

    // --------------------------------------------------------- //
    // -------------------- Top-level fields ------------------- //
    // --------------------------------------------------------- //

    /** Application version (set from deployment.properties at build time). */
    private String version = "";

    /** Build timestamp string (set from deployment.properties at build time). */
    private String buildTime = "";

    /** Public base URL of this service instance, e.g. {@code http://localhost:8089/dlb-web-service}. */
    private String baseUrl = "http://localhost:8089/dlb-web-service";

    /** Filesystem path to the service data directory. */
    private String dataDir = "/usr/local/dialogue-branch/data/dlb-web-service";

    /** Whether the service allows creation of anonymous user accounts via the REST API. */
    private boolean allowAnonymousUsers = false;

    @NestedConfigurationProperty
    private MariaDb mariadb = new MariaDb();

    @NestedConfigurationProperty
    private Auth auth = new Auth();

    @NestedConfigurationProperty
    private ExternalVariableService externalVariableService = new ExternalVariableService();

    @NestedConfigurationProperty
    private AzureDataLake azureDataLake = new AzureDataLake();

    // --------------------------------------------------------- //
    // -------------------- Nested: MariaDB -------------------- //
    // --------------------------------------------------------- //

    public static class MariaDb {
        private String host = "localhost";
        private int port = 3306;
        private String user = "root";
        private String password = "";
        private String database = "dialoguebranch";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
    }

    // --------------------------------------------------------- //
    // -------------------- Nested: Auth ----------------------- //
    // --------------------------------------------------------- //

    public static class Auth {

        /** Which authentication backend to use: {@code "native"} or {@code "keycloak"}. */
        private String service = AUTH_SERVICE_NATIVE;

        @NestedConfigurationProperty
        private Keycloak keycloak = new Keycloak();

        /** JWT access token secret (required when service = native). */
        private String jwtAccessTokenSecret = "";

        /** Access token lifetime in seconds (default: 300). */
        private int accessTokenExpirationSeconds = 300;

        /** JWT refresh token secret (required when service = native). */
        private String jwtRefreshTokenSecret = "";

        /** Refresh token lifetime in seconds (default: 1800). */
        private int refreshTokenExpirationSeconds = 1800;

        public String getService() { return service; }
        public void setService(String service) { this.service = service; }

        public Keycloak getKeycloak() { return keycloak; }
        public void setKeycloak(Keycloak keycloak) { this.keycloak = keycloak; }

        public String getJwtAccessTokenSecret() { return jwtAccessTokenSecret; }
        public void setJwtAccessTokenSecret(String s) { this.jwtAccessTokenSecret = s; }

        public int getAccessTokenExpirationSeconds() { return accessTokenExpirationSeconds; }
        public void setAccessTokenExpirationSeconds(int s) { this.accessTokenExpirationSeconds = s; }

        public String getJwtRefreshTokenSecret() { return jwtRefreshTokenSecret; }
        public void setJwtRefreshTokenSecret(String s) { this.jwtRefreshTokenSecret = s; }

        public int getRefreshTokenExpirationSeconds() { return refreshTokenExpirationSeconds; }
        public void setRefreshTokenExpirationSeconds(int s) { this.refreshTokenExpirationSeconds = s; }

        // -------------------- Nested: Keycloak -------------------- //

        public static class Keycloak {
            private String baseUrl = "http://keycloak:8080/";
            private String realm = "dialoguebranch";
            private String clientId = "dlb-web-service";
            private String clientSecret = "";

            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

            public String getRealm() { return realm; }
            public void setRealm(String realm) { this.realm = realm; }

            public String getClientId() { return clientId; }
            public void setClientId(String clientId) { this.clientId = clientId; }

            public String getClientSecret() { return clientSecret; }
            public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        }
    }

    // ------------------------------------------------------------------ //
    // -------------------- Nested: ExternalVariableService ------------- //
    // ------------------------------------------------------------------ //

    public static class ExternalVariableService {
        private boolean enabled = false;
        private String url = "";
        private int apiVersion = 1;
        private String apiKey = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getApiVersion() { return apiVersion; }
        public void setApiVersion(int apiVersion) { this.apiVersion = apiVersion; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    // ----------------------------------------------------------- //
    // -------------------- Nested: AzureDataLake ---------------- //
    // ----------------------------------------------------------- //

    public static class AzureDataLake {
        private boolean enabled = false;
        private String authenticationMethod = "";
        private String accountName = "";
        private String accountKey = "";
        private String sasAccountUrl = "";
        private String sasToken = "";
        private String fileSystemName = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getAuthenticationMethod() { return authenticationMethod; }
        public void setAuthenticationMethod(String m) { this.authenticationMethod = m; }

        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }

        public String getAccountKey() { return accountKey; }
        public void setAccountKey(String accountKey) { this.accountKey = accountKey; }

        public String getSasAccountUrl() { return sasAccountUrl; }
        public void setSasAccountUrl(String sasAccountUrl) { this.sasAccountUrl = sasAccountUrl; }

        public String getSasToken() { return sasToken; }
        public void setSasToken(String sasToken) { this.sasToken = sasToken; }

        public String getFileSystemName() { return fileSystemName; }
        public void setFileSystemName(String fileSystemName) { this.fileSystemName = fileSystemName; }
    }

    // --------------------------------------------------------- //
    // -------------------- Top-level getters/setters ---------- //
    // --------------------------------------------------------- //

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getBuildTime() { return buildTime; }
    public void setBuildTime(String buildTime) { this.buildTime = buildTime; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }

    public boolean isAllowAnonymousUsers() { return allowAnonymousUsers; }
    public void setAllowAnonymousUsers(boolean allowAnonymousUsers) {
        this.allowAnonymousUsers = allowAnonymousUsers;
    }

    public MariaDb getMariadb() { return mariadb; }
    public void setMariadb(MariaDb mariadb) { this.mariadb = mariadb; }

    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }

    public ExternalVariableService getExternalVariableService() { return externalVariableService; }
    public void setExternalVariableService(ExternalVariableService s) {
        this.externalVariableService = s;
    }

    public AzureDataLake getAzureDataLake() { return azureDataLake; }
    public void setAzureDataLake(AzureDataLake azureDataLake) { this.azureDataLake = azureDataLake; }
}
