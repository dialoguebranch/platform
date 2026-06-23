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

    // --------------------------------------------------------- //
    // -------------------- Nested: MariaDB -------------------- //
    // --------------------------------------------------------- //

    /**
     * Holds the MariaDB connection configuration used by the Dialogue Branch Web Service.
     *
     * @author Harm op den Akker
     */
    public static class MariaDb {
        private String host = "localhost";
        private int port = 3306;
        private String user = "root";
        private String password = "";
        private String database = "dialoguebranch";

        /** Returns the database host name. @return the host name. */
        public String getHost() { return host; }
        /** Sets the database host name. @param host the host name. */
        public void setHost(String host) { this.host = host; }

        /** Returns the database port number. @return the port number. */
        public int getPort() { return port; }
        /** Sets the database port number. @param port the port number. */
        public void setPort(int port) { this.port = port; }

        /** Returns the database user name. @return the user name. */
        public String getUser() { return user; }
        /** Sets the database user name. @param user the user name. */
        public void setUser(String user) { this.user = user; }

        /** Returns the database password. @return the password. */
        public String getPassword() { return password; }
        /** Sets the database password. @param password the password. */
        public void setPassword(String password) { this.password = password; }

        /** Returns the database name. @return the database name. */
        public String getDatabase() { return database; }
        /** Sets the database name. @param database the database name. */
        public void setDatabase(String database) { this.database = database; }
    }

    // --------------------------------------------------------- //
    // -------------------- Nested: Auth ----------------------- //
    // --------------------------------------------------------- //

    /**
     * Holds the authentication configuration for the Dialogue Branch Web Service.
     *
     * @author Harm op den Akker
     */
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

        /** Returns the authentication service name. @return the service name. */
        public String getService() { return service; }
        /** Sets the authentication service name. @param service the service name. */
        public void setService(String service) { this.service = service; }

        /** Returns the Keycloak configuration. @return the {@link Keycloak} configuration. */
        public Keycloak getKeycloak() { return keycloak; }
        /** Sets the Keycloak configuration. @param keycloak the {@link Keycloak} configuration. */
        public void setKeycloak(Keycloak keycloak) { this.keycloak = keycloak; }

        /** Returns the JWT access token secret. @return the JWT access token secret. */
        public String getJwtAccessTokenSecret() { return jwtAccessTokenSecret; }
        /** Sets the JWT access token secret. @param s the JWT access token secret. */
        public void setJwtAccessTokenSecret(String s) { this.jwtAccessTokenSecret = s; }

        /** Returns the access token lifetime in seconds. @return the expiration in seconds. */
        public int getAccessTokenExpirationSeconds() { return accessTokenExpirationSeconds; }
        /** Sets the access token lifetime in seconds. @param s the expiration in seconds. */
        public void setAccessTokenExpirationSeconds(int s) { this.accessTokenExpirationSeconds = s; }

        /** Returns the JWT refresh token secret. @return the JWT refresh token secret. */
        public String getJwtRefreshTokenSecret() { return jwtRefreshTokenSecret; }
        /** Sets the JWT refresh token secret. @param s the JWT refresh token secret. */
        public void setJwtRefreshTokenSecret(String s) { this.jwtRefreshTokenSecret = s; }

        /** Returns the refresh token lifetime in seconds. @return the expiration in seconds. */
        public int getRefreshTokenExpirationSeconds() { return refreshTokenExpirationSeconds; }
        /** Sets the refresh token lifetime in seconds. @param s the expiration in seconds. */
        public void setRefreshTokenExpirationSeconds(int s) { this.refreshTokenExpirationSeconds = s; }

        // -------------------- Nested: Keycloak -------------------- //

        /**
         * Holds the Keycloak-specific connection and client configuration.
         *
         * @author Harm op den Akker
         */
        public static class Keycloak {
            private String baseUrl = "http://keycloak:8080/";
            private String realm = "dialoguebranch";
            private String clientId = "dlb-web-service";
            private String clientSecret = "";

            /** Returns the Keycloak base URL. @return the base URL. */
            public String getBaseUrl() { return baseUrl; }
            /** Sets the Keycloak base URL. @param baseUrl the base URL. */
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

            /** Returns the Keycloak realm name. @return the realm name. */
            public String getRealm() { return realm; }
            /** Sets the Keycloak realm name. @param realm the realm name. */
            public void setRealm(String realm) { this.realm = realm; }

            /** Returns the Keycloak client ID. @return the client ID. */
            public String getClientId() { return clientId; }
            /** Sets the Keycloak client ID. @param clientId the client ID. */
            public void setClientId(String clientId) { this.clientId = clientId; }

            /** Returns the Keycloak client secret. @return the client secret. */
            public String getClientSecret() { return clientSecret; }
            /** Sets the Keycloak client secret. @param clientSecret the client secret. */
            public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        }
    }

    // ------------------------------------------------------------------ //
    // -------------------- Nested: ExternalVariableService ------------- //
    // ------------------------------------------------------------------ //

    /**
     * Holds configuration for the optional external Dialogue Branch Variable Service integration.
     *
     * @author Harm op den Akker
     */
    public static class ExternalVariableService {
        private boolean enabled = false;
        private String url = "";
        private int apiVersion = 1;
        private String apiKey = "";

        /** Returns whether the external variable service is enabled. @return {@code true} if enabled. */
        public boolean isEnabled() { return enabled; }
        /** Sets whether the external variable service is enabled. @param enabled {@code true} to enable. */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        /** Returns the base URL of the external variable service. @return the URL. */
        public String getUrl() { return url; }
        /** Sets the base URL of the external variable service. @param url the URL. */
        public void setUrl(String url) { this.url = url; }

        /** Returns the API version to use when calling the external variable service. @return the API version. */
        public int getApiVersion() { return apiVersion; }
        /** Sets the API version to use when calling the external variable service. @param apiVersion the API version. */
        public void setApiVersion(int apiVersion) { this.apiVersion = apiVersion; }

        /** Returns the API key used to authenticate with the external variable service. @return the API key. */
        public String getApiKey() { return apiKey; }
        /** Sets the API key used to authenticate with the external variable service. @param apiKey the API key. */
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    // --------------------------------------------------------- //
    // -------------------- Top-level getters/setters ---------- //
    // --------------------------------------------------------- //

    /** Returns the application version string. @return the version string. */
    public String getVersion() { return version; }
    /** Sets the application version string. @param version the version string. */
    public void setVersion(String version) { this.version = version; }

    /** Returns the build timestamp string. @return the build timestamp. */
    public String getBuildTime() { return buildTime; }
    /** Sets the build timestamp string. @param buildTime the build timestamp. */
    public void setBuildTime(String buildTime) { this.buildTime = buildTime; }

    /** Returns the public base URL of this service instance. @return the base URL. */
    public String getBaseUrl() { return baseUrl; }
    /** Sets the public base URL of this service instance. @param baseUrl the base URL. */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /** Returns the filesystem path to the service data directory. @return the data directory path. */
    public String getDataDir() { return dataDir; }
    /** Sets the filesystem path to the service data directory. @param dataDir the data directory path. */
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }

    /** Returns whether the service allows creation of anonymous user accounts. @return {@code true} if allowed. */
    public boolean isAllowAnonymousUsers() { return allowAnonymousUsers; }
    /** Sets whether the service allows creation of anonymous user accounts. @param allowAnonymousUsers {@code true} to allow. */
    public void setAllowAnonymousUsers(boolean allowAnonymousUsers) {
        this.allowAnonymousUsers = allowAnonymousUsers;
    }

    /** Returns the MariaDB connection configuration. @return the {@link MariaDb} configuration. */
    public MariaDb getMariadb() { return mariadb; }
    /** Sets the MariaDB connection configuration. @param mariadb the {@link MariaDb} configuration. */
    public void setMariadb(MariaDb mariadb) { this.mariadb = mariadb; }

    /** Returns the authentication configuration. @return the {@link Auth} configuration. */
    public Auth getAuth() { return auth; }
    /** Sets the authentication configuration. @param auth the {@link Auth} configuration. */
    public void setAuth(Auth auth) { this.auth = auth; }

    /** Returns the external variable service configuration. @return the {@link ExternalVariableService} configuration. */
    public ExternalVariableService getExternalVariableService() { return externalVariableService; }
    /** Sets the external variable service configuration. @param s the {@link ExternalVariableService} configuration. */
    public void setExternalVariableService(ExternalVariableService s) {
        this.externalVariableService = s;
    }
}
