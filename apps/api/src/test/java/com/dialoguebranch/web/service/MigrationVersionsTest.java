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

package com.dialoguebranch.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the one thing the other tests cannot see: the integration tests run on H2 with Flyway
 * disabled, so two migrations with the same version (an SQL file and a Java migration both numbered
 * V9, say) pass the whole test suite and the Docker build, and only fail when the Web Service starts
 * against a real database. Flyway migrations are SQL files under {@code db/migration} and compiled
 * Java classes in the {@code db.migration} package; both count towards the version.
 */
class MigrationVersionsTest {

	private static final Pattern MIGRATION = Pattern.compile("^V(\\d+)__[^$]*\\.(sql|class)$");

	private static Map<Integer, List<String>> migrationsByVersion() throws Exception {
		Map<Integer, List<String>> result = new TreeMap<>();
		for (Resource resource : new PathMatchingResourcePatternResolver()
				.getResources("classpath*:db/migration/V*")) {
			Matcher matcher = MIGRATION.matcher(String.valueOf(resource.getFilename()));
			if (matcher.matches()) {
				result.computeIfAbsent(Integer.valueOf(matcher.group(1)), v -> new ArrayList<>())
						.add(resource.getFilename());
			}
		}
		return result;
	}

	@Test
	void everyMigrationVersionIsUsedOnce() throws Exception {
		Map<Integer, List<String>> byVersion = migrationsByVersion();
		assertFalse(byVersion.isEmpty(), "no migrations found on the classpath");
		byVersion.forEach((version, files) -> assertEquals(1, files.size(),
				"Flyway refuses to start with more than one migration for version " + version
						+ ": " + files));
	}

}
