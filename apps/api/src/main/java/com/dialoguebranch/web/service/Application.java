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

import com.dialoguebranch.web.service.exception.DLBServiceConfigurationException;
import com.dialoguebranch.web.service.execution.ApplicationManager;
import com.dialoguebranch.parser.ResourceFileLoader;
import jakarta.annotation.PostConstruct;
import nl.rrd.utils.AppComponents;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.system.JavaVersion;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.SpringVersion;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ClassUtils;

import java.net.URL;
import java.time.Instant;

/**
 * The main entry point for the DialogueBranch Web Service as a Spring Boot Application.
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */

@SpringBootApplication
@EnableScheduling
public class Application extends SpringBootServletInitializer implements
ApplicationListener<ApplicationEvent> {

	private final Logger logger =
			AppComponents.getLogger(ClassUtils.getUserClass(getClass()).getSimpleName());
	private final Configuration config;
	private ApplicationManager applicationManager = null;
	private final Long launchedTime = Instant.now().toEpochMilli();

	@Autowired
	private SessionFactory sessionFactory;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Constructs a new application. It reads service.properties and initializes the {@link
	 * Configuration} and the {@link ApplicationManager}.
	 *
	 * @throws Exception if the application can't be initialised.
	 */
	public Application() throws Exception {

		// Initialize a Configuration object
		config = AppComponents.get(Configuration.class);

		// Load the values from service.properties into the Configuration
		URL resourcePropertiesUrl = getClass().getClassLoader().getResource(
				"service.properties");
		if (resourcePropertiesUrl == null) {
			throw new Exception("Can't find resource service.properties. " +
					"Did you run gradlew updateConfig?");
		}
		config.loadProperties(resourcePropertiesUrl);

		// Load the values from deployment.properties into the Configuration (app version number)
		URL deploymentPropertiesUrl = getClass().getClassLoader().getResource(
				"deployment.properties");
		if(deploymentPropertiesUrl != null)
			config.loadProperties(deploymentPropertiesUrl);

		// By default, log uncaught exceptions to this logger
		Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                logger.error("Uncaught exception: {}", e.getMessage(), e)
		);

		try {
			applicationManager = new ApplicationManager(
					new ResourceFileLoader("dialogues"));
		} catch(DLBServiceConfigurationException e) {
			logger.error("Unable to initialize DialogueBranch Web Service due to configuration " +
					"errors.");
			System.exit(1);
		}
	}

	@PostConstruct
	private void initApp() {
		AppComponents.getInstance().addComponent(sessionFactory);
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Return the UTC timestamp of when this service was first launched.
	 *
	 * @return the UTC timestamp of when this service was first launched.
	 */
	public Long getLaunchedTime() {
		return launchedTime;
	}

	public Configuration getConfiguration() {
		return config;
	}

	/**
	 * Returns a pointer to the {@link ApplicationManager} that is used to manage application-wide
	 * processes.
	 *
	 * @return the {@link ApplicationManager} associated with this Dialogue Branch Web Service.
	 */
	public ApplicationManager getApplicationManager() {
		return applicationManager;
	}

	// -------------------------------------------------------- //
	// -------------------- App Management -------------------- //
	// -------------------------------------------------------- //

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if(event instanceof ContextClosedEvent) {
			logger.info("Shutdown DialogueBranch Web Service.");
		}

		if(event instanceof ContextRefreshedEvent) {

			logger.info("========== Dialogue Branch Web Service Startup Info ==========");

			logger.info("=== Version: {}", config.get(Configuration.VERSION));
            logger.info("=== API Version: {}", ProtocolVersion.getLatestVersion().versionName());
            logger.info("=== Build: {}", config.getBuildTime());
            logger.info("=== Spring Version: {}", SpringVersion.getVersion());
            logger.info("=== JDK Version: {}", System.getProperty("java.version"));
            logger.info("=== Java Version: {}", JavaVersion.getJavaVersion().toString());

			logger.info("=== Authentication Service: {}", config.getAuthService());
			if(config.getAuthService().equals(Configuration.AUTH_SERVICE_KEYCLOAK)) {
				logger.info("===== Keycloak URL: {}", config.getKeycloakBaseUrl());
				logger.info("===== Keycloak Realm: {}", config.getKeycloakRealm());
				logger.info("===== Keycloak Client ID: {}", config.getKeycloakClientId());
			} else if(config.getAuthService().equals(Configuration.AUTH_SERVICE_NATIVE)) {
				logger.info("===== JWT Access Token Secret: {}", config.getJwtAccessTokenSecret());
				logger.info("===== JWT Refresh Token Secret: {}", config.getJwtRefreshTokenSecret());
			}

			logger.info("=== External Variable Service Enabled: {}", config.getExternalVariableServiceEnabled());
			if(config.getExternalVariableServiceEnabled()) {
				logger.info("===== External Variable Service URL: {}", config.getExternalVariableServiceURL());
				logger.info("===== External Variable Service API Version: {}", config.getExternalVariableServiceAPIVersion());
			}

			logger.info("=== Azure Data Lake Storage Enabled: {}", config.getAzureDataLakeEnabled());
			if(config.getAzureDataLakeEnabled()) {
				logger.info("===== Azure Data Lake Account URL: {}", config.getAzureDataLakeSASAccountUrl());
				logger.info("===== Azure Data Lake Filesystem: {}", config.getAzureDataLakeFileSystemName());
			}

			logger.info("===================================================");
		}
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(Application.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
