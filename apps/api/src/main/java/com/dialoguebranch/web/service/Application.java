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

import com.dialoguebranch.web.service.auth.jwt.JWTUtils;
import com.dialoguebranch.web.service.exception.DLBServiceConfigurationException;
import com.dialoguebranch.web.service.execution.ApplicationManager;
import jakarta.annotation.PostConstruct;
import nl.rrd.utils.AppComponents;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.system.JavaVersion;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.SpringVersion;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ClassUtils;

import java.time.Instant;

/**
 * The main entry point for the Dialogue Branch Web Service as a Spring Boot Application.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DlbProperties.class)
public class Application extends SpringBootServletInitializer implements
ApplicationListener<ApplicationEvent> {

	private final Logger logger =
			AppComponents.getLogger(ClassUtils.getUserClass(getClass()).getSimpleName());
	@Autowired
	private DlbProperties dlbProperties;
	@Autowired
	private JWTUtils jwtUtils;
	private ApplicationManager applicationManager = null;
	private final Long launchedTime = Instant.now().toEpochMilli();

	@Autowired
	private SessionFactory sessionFactory;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of {@link Application}, registering a default uncaught exception handler
	 * that logs any unhandled exceptions at error level.
	 */
	public Application() {
		Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                logger.error("Uncaught exception: {}", e.getMessage(), e)
		);
	}

	@PostConstruct
	private void initApp() {
		AppComponents.getInstance().addComponent(sessionFactory);

		try {
			applicationManager = new ApplicationManager(dlbProperties);
		} catch(DLBServiceConfigurationException e) {
			logger.error("Unable to initialize DialogueBranch Web Service due to configuration " +
					"errors.");
			System.exit(1);
		}
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

	/**
	 * Returns the {@link DlbProperties} configuration object for this service.
	 *
	 * @return the {@link DlbProperties} configuration object.
	 */
	public DlbProperties getDlbProperties() {
		return dlbProperties;
	}

	/**
	 * Returns the {@link JWTUtils} bean used for generating and validating JWT tokens.
	 *
	 * @return the {@link JWTUtils} instance.
	 */
	public JWTUtils getJwtUtils() {
		return jwtUtils;
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

	/**
	 * Handles Spring application lifecycle events. Logs startup information on
	 * {@link ContextRefreshedEvent} and logs a shutdown message on {@link ContextClosedEvent}.
	 *
	 * @param event the Spring application lifecycle event.
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if(event instanceof ContextClosedEvent) {
			logger.info("Shutdown DialogueBranch Web Service.");
		}

		if(event instanceof ContextRefreshedEvent) {

			logger.info("========== Dialogue Branch Web Service Startup Info ==========");

			logger.info("=== Version: {}", dlbProperties.getVersion());
            logger.info("=== API Version: {}", ProtocolVersion.getLatestVersion().versionName());
            logger.info("=== Build: {}", dlbProperties.getBuildTime());
            logger.info("=== Spring Version: {}", SpringVersion.getVersion());
            logger.info("=== JDK Version: {}", System.getProperty("java.version"));
            logger.info("=== Java Version: {}", JavaVersion.getJavaVersion().toString());

			DlbProperties.Auth auth = dlbProperties.getAuth();
			logger.info("=== Authentication Service: {}", auth.getService());
			if(auth.getService().equals(DlbProperties.AUTH_SERVICE_KEYCLOAK)) {
				logger.info("===== Keycloak URL: {}", auth.getKeycloak().getBaseUrl());
				logger.info("===== Keycloak Realm: {}", auth.getKeycloak().getRealm());
				logger.info("===== Keycloak Client ID: {}", auth.getKeycloak().getClientId());
			} else if(auth.getService().equals(DlbProperties.AUTH_SERVICE_NATIVE)) {
				logger.info("===== JWT Access Token Secret: {}", auth.getJwtAccessTokenSecret());
				logger.info("===== JWT Refresh Token Secret: {}", auth.getJwtRefreshTokenSecret());
			}

			DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
			logger.info("=== External Variable Service Enabled: {}", evs.isEnabled());
			if(evs.isEnabled()) {
				logger.info("===== External Variable Service URL: {}", evs.getUrl());
				logger.info("===== External Variable Service API Version: {}", evs.getApiVersion());
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
