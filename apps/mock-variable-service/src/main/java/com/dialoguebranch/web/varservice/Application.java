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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.SpringVersion;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

/**
 * The main entry point for the External Variable Service Dummy as a Spring Boot Application.
 *
 * @author Harm op den Akker
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DlbVarServiceProperties.class)
public class Application implements ApplicationListener<ApplicationEvent> {

	private static final Logger logger = LoggerFactory.getLogger(Application.class);

	private final DlbVarServiceProperties properties;
	private final Long launchedTime = Instant.now().toEpochMilli();

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Constructs a new application instance. Spring injects the configuration properties.
	 *
	 * @param properties the bound configuration properties
	 */
	public Application(DlbVarServiceProperties properties) {
		this.properties = properties;
		Thread.setDefaultUncaughtExceptionHandler((t, e) ->
				logger.error("Uncaught exception: {}", e.getMessage(), e));
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
	 * Returns the bound configuration properties for this service.
	 *
	 * @return the bound configuration properties for this service.
	 */
	public DlbVarServiceProperties getProperties() {
		return properties;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Run the application with the provided arguments.
	 * @param args optional run arguments (not supported).
	 */
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextClosedEvent) {
			logger.info("Shutdown DialogueBranch External Variable Service Dummy.");
		}

		if (event instanceof ContextRefreshedEvent) {
			logger.info("========== DialogueBranch External Variable Service Dummy Startup Info ==========");
			logger.info("=== Version: {}", properties.getVersion());
			logger.info("=== API Version: {}", ProtocolVersion.getLatestVersion().versionName());
			logger.info("=== Build: {}", properties.getBuildTime());
			logger.info("=== Spring Version: {}", SpringVersion.getVersion());
			logger.info("=== JDK Version: {}", System.getProperty("java.version"));
			logger.info("=== Java Version: {}", JavaVersion.getJavaVersion().toString());
			logger.info("=======================================================================");
		}
	}

}
