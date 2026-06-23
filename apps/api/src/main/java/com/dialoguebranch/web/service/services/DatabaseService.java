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

package com.dialoguebranch.web.service.services;

import com.dialoguebranch.web.service.DlbProperties;
import jakarta.persistence.Entity;
import nl.rrd.utils.AppComponents;
import org.hibernate.SessionFactory;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.service.spi.ServiceException;
import org.hibernate.tool.schema.Action;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.List;
import java.util.Set;

/**
 * Spring {@link org.springframework.context.annotation.Configuration} class responsible for
 * creating and providing the Hibernate {@link SessionFactory} bean used by the Dialogue Branch
 * Web Service. It scans for JPA {@link jakarta.persistence.Entity} classes and configures the
 * MariaDB connection from the application properties, retrying the connection on startup.
 *
 * @author Harm op den Akker
 */
@Configuration
public class DatabaseService {

	private final DlbProperties dlbProperties;

	/**
	 * Creates an instance of {@link DatabaseService} with the given application configuration
	 * properties, used to configure the database connection.
	 *
	 * @param dlbProperties the application configuration properties.
	 */
	@Autowired
	public DatabaseService(DlbProperties dlbProperties) {
		this.dlbProperties = dlbProperties;
	}

	/**
	 * Creates and returns the Hibernate {@link SessionFactory} bean for the Dialogue Branch Web
	 * Service. Connects to the configured MariaDB instance, retrying up to 30 times if the
	 * database is not yet available.
	 *
	 * @return the configured Hibernate {@link SessionFactory}.
	 */
	@Bean
	public SessionFactory sessionFactory() {
		DlbProperties.MariaDb cfg = dlbProperties.getMariadb();

		List<? extends Class<?>> entityClasses = findEntityClasses();

		HibernatePersistenceConfiguration hibernateConfig =
				new HibernatePersistenceConfiguration("DialogueBranch");

		for (Class<?> entityClass : entityClasses) {
			hibernateConfig.managedClass(entityClass);
		}

		Logger logger = AppComponents.getLogger(getClass().getSimpleName());
		int retryCount = 0;
		while (true) {
			try {
				return hibernateConfig
						.jdbcUrl("jdbc:mariadb://" + cfg.getHost() + ":" + cfg.getPort() +
								"/" + cfg.getDatabase() + "?createDatabaseIfNotExist=true")
						.jdbcCredentials(cfg.getUser(), cfg.getPassword())
						.schemaToolingAction(Action.UPDATE)
						.createEntityManagerFactory();
			} catch (ServiceException ex) {
				if (retryCount < 30) {
					logger.warn("Failed to connect to database; retrying in 10 seconds ...");
					wait(10000);
				} else {
					throw ex;
				}
				retryCount++;
			}
		}
	}

	private void wait(int ms) {
		long now = System.currentTimeMillis();
		long end = now + ms;
		try {
			while (now < end) {
				Thread.sleep(end - now);
				now = System.currentTimeMillis();
			}
		} catch (InterruptedException ex) {
			throw new RuntimeException(ex);
		}
	}

	private List<? extends Class<?>> findEntityClasses() {
		ClassPathScanningCandidateComponentProvider scanner =
				new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

		Set<BeanDefinition> entities = scanner.findCandidateComponents(
				"com.dialoguebranch.web.service.models");
		return entities.stream().map((entity) -> {
			try {
				return Class.forName(entity.getBeanClassName());
			} catch (ClassNotFoundException ex) {
				throw new RuntimeException("Entity class not found: " + ex.getMessage(), ex);
			}
		}).toList();
	}
}
