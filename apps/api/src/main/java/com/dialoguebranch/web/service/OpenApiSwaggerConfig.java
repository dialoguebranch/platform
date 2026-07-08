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

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.Components;
import com.dialoguebranch.web.service.ServiceContext;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring configuration class that builds and customizes the OpenAPI/Swagger documentation for the
 * Dialogue Branch Web Service, including server entries for each supported {@link ProtocolVersion},
 * the API security scheme, and grouped API end-point definitions.
 *
 * @author Harm op den Akker
 */
@Configuration
@OpenAPIDefinition
public class OpenApiSwaggerConfig {

	private static final Object LOCK = new Object();
	private static boolean customizedPaths = false;

	private final DlbProperties dlbProperties;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Instantiation of this class is handled through Spring.
	 *
	 * @param dlbProperties the application configuration properties, used to build the Keycloak
	 *                      OAuth2 authorization/token URLs shown in Swagger UI.
	 */
	public OpenApiSwaggerConfig(DlbProperties dlbProperties) {
		this.dlbProperties = dlbProperties;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Automatic configuration of various OpenAPI parameters.
	 *
	 * @return an {@link OpenAPI} object.
	 */
	@Bean
	public OpenAPI openAPI() {
		OpenAPI openAPI = new OpenAPI();

		// Add a Server + Version for each supported ProtocolVersion
		// Make sure the latest version is added first, so it is the one automatically selected in
		// the Swagger Server selection drop-down menu
		ProtocolVersion[] allVersions = ProtocolVersion.values();
		for(int i=allVersions.length-1; i>=0; i--) {
			Server server = new Server();
			server.url(ServiceContext.getBaseUrl()+"/v"+allVersions[i].versionName());
			openAPI.addServersItem(server);
		}

		// Finally, add the base server path (without version)
		Server server = new Server();
		server.url(ServiceContext.getBaseUrl());
		openAPI.addServersItem(server);

		DlbProperties.Auth.Keycloak keycloak = dlbProperties.getAuth().getKeycloak();
		String browserBaseUrl = keycloak.getBrowserBaseUrl();
		if (!browserBaseUrl.endsWith("/")) browserBaseUrl += "/";
		String realmUrl = browserBaseUrl + "realms/" + keycloak.getRealm() + "/protocol/openid-connect/";

		openAPI.components(new Components()
			.addSecuritySchemes("bearerAuth",
				new SecurityScheme()
					.name("bearerAuth")
					.scheme("bearer")
					.type(SecurityScheme.Type.HTTP)
					.bearerFormat("JWT")
			)
			.addSecuritySchemes("oauth2",
				new SecurityScheme()
					.type(SecurityScheme.Type.OAUTH2)
					.description("Authenticate with Keycloak using the Authorization Code + PKCE " +
							"flow (no client secret required — click Authorize below).")
					.flows(new OAuthFlows()
						.authorizationCode(new OAuthFlow()
							.authorizationUrl(realmUrl + "auth")
							.tokenUrl(realmUrl + "token")
							.scopes(new Scopes().addString("openid", "OpenID Connect scope"))
						)
					)
			));

		openAPI.info(
			new Info()
				.title("DialogueBranch Web Service API")
				.description("The DialogueBranch Web Service API gives authorized clients the " +
						"ability to start-, and sequentially execute DialogueBranch dialogues as " +
						"well to access DialogueBranch Variable data.")
				.version(ServiceContext.getCurrentVersion())
				.contact(new Contact().email("info@dialoguebranch.com")
						.name("DialogueBranch Platform Support"))
				.license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));

		return openAPI;
	}

	/**
	 * Creates a set of end-points that excludes the end-points requiring a specific version number
	 * for use in the Swagger UI.
	 * @return a {@link GroupedOpenApi} that excludes all version-dependent end-points.
	 */
	@Bean
	public GroupedOpenApi withoutVersioning() {
		return GroupedOpenApi.builder().group("API End-Points without Versioning")
			.pathsToExclude("/v{version}/info/*",
					"/v{version}/variables/*",
					"/v{version}/dialogue/*",
					"/v{version}/auth/*",
					"/v{version}/log/*",
					"/v{version}/admin/*",
					"/v{version}/authoring/**",
					"/v{version}/project/*",
					"/v{version}/publish/*")
			.build();
	}

	private void customiseApi(OpenAPI api) {
		transformPaths(api.getPaths());
	}

	private void transformPaths(Paths paths) {
		synchronized (LOCK) {
			if (customizedPaths)
				return;
			customizedPaths = true;
			List<String> keys = new ArrayList<>(paths.keySet());
			String versionPath = "/v{version}";
			Map<String, PathItem> unorderedMap = new HashMap<>();
			for (String key : keys) {
				PathItem item = paths.remove(key);
				if (!key.startsWith(versionPath))
					continue;
				String operationPath = key.substring(versionPath.length());
				unorderedMap.put(operationPath, item);
			}
			List<String> orderedKeys = new ArrayList<>(unorderedMap.keySet());
			orderedKeys.sort(null);
			for (String key : orderedKeys) {
				paths.put(key, unorderedMap.get(key));
			}
		}
	}

}
