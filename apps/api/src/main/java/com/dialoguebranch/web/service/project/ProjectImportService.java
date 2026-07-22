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

package com.dialoguebranch.web.service.project;

import com.dialoguebranch.execution.parser.ProjectMetaDataParser;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.InternalServerErrorException;
import com.dialoguebranch.web.service.execution.FileSystemScriptLoader;
import com.dialoguebranch.web.service.storage.model.DBProject;
import nl.rrd.utils.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports a project from a {@code .zip} archive previously produced by {@link
 * ProjectExportService} — extracts it into a temporary directory, validates it, and seeds/publishes
 * it via {@link ProjectSeedService#createAndPublishProject}, the same path classpath-seeded and
 * hand-authored projects take.
 *
 * @author Harm op den Akker
 */
@Service
public class ProjectImportService {

	private static final String PROJECT_MARKER_FILE = "dlb-project.xml";

	/** Compressed upload size cap — generous for a Dialogue Branch project's text-only content. */
	private static final long MAX_ARCHIVE_SIZE_BYTES = 50L * 1024 * 1024;

	/** Uncompressed size cap, guarding against a small, maliciously crafted "zip bomb" upload. */
	private static final long MAX_UNCOMPRESSED_SIZE_BYTES = 200L * 1024 * 1024;

	/** Entry count cap, guarding against an archive with an excessive number of tiny entries. */
	private static final int MAX_ENTRY_COUNT = 20_000;

	private static final Logger logger = LoggerFactory.getLogger(ProjectImportService.class);

	private final ProjectService projectService;
	private final ProjectSeedService projectSeedService;

	/**
	 * Creates a new {@link ProjectImportService}.
	 *
	 * @param projectService     service used to check whether the archive's slug is already taken.
	 * @param projectSeedService service used to create, seed, and publish the imported project once
	 *                           the archive has been validated.
	 */
	public ProjectImportService(ProjectService projectService,
			ProjectSeedService projectSeedService) {
		this.projectService = projectService;
		this.projectSeedService = projectSeedService;
	}

	/**
	 * Imports a project from the given uploaded {@code .zip} archive.
	 *
	 * @param archive the uploaded archive.
	 * @return the newly created and published project.
	 * @throws BadRequestException if the archive is missing, empty, too large, not a well-formed
	 * {@code .zip}, has no {@code dlb-project.xml} at its root, that file has no {@code slug}
	 * attribute (i.e. it was not produced by {@link ProjectExportService}), or its content fails
	 * project validation.
	 * @throws ConflictException   if a project with the archive's slug already exists.
	 */
	public DBProject importProject(MultipartFile archive) throws HttpException {
		if (archive == null || archive.isEmpty()) {
			throw new BadRequestException("No file was uploaded.");
		}
		if (archive.getSize() > MAX_ARCHIVE_SIZE_BYTES) {
			throw new BadRequestException("Uploaded archive exceeds the maximum allowed size of " +
					(MAX_ARCHIVE_SIZE_BYTES / (1024 * 1024)) + " MB.");
		}

		Path tempDir;
		try {
			tempDir = Files.createTempDirectory("dlb-import-");
		} catch (IOException e) {
			throw new InternalServerErrorException();
		}

		try {
			extractArchive(archive, tempDir);

			File markerFile = new File(tempDir.toFile(), PROJECT_MARKER_FILE);
			if (!markerFile.isFile()) {
				throw new BadRequestException("Archive does not contain a " + PROJECT_MARKER_FILE +
						" at its root.");
			}

			ProjectMetaData metaData;
			try {
				metaData = ProjectMetaDataParser.parse(markerFile);
			} catch (ParseException e) {
				throw new BadRequestException("Failed to parse " + PROJECT_MARKER_FILE + ": " +
						e.getMessage());
			} catch (IOException e) {
				throw new BadRequestException("Failed to read " + PROJECT_MARKER_FILE + ": " +
						e.getMessage());
			}

			String slug = metaData.getSlug();
			if (slug == null || slug.isBlank()) {
				throw new BadRequestException("This archive's " + PROJECT_MARKER_FILE +
						" has no 'slug' attribute — it was not produced by the Export Project " +
						"feature and cannot be imported.");
			}
			if (projectService.findBySlug(slug).isPresent()) {
				throw new ConflictException("A project with slug '" + slug + "' already exists.");
			}

			FileSystemScriptLoader scriptLoader = new FileSystemScriptLoader(tempDir.toFile());
			ProjectParserResult parserResult;
			try {
				parserResult = new ProjectParser(scriptLoader).parse();
			} catch (IOException e) {
				throw new BadRequestException("Failed to read archive contents: " + e.getMessage());
			}
			if (!parserResult.getParseErrors().isEmpty()) {
				StringBuilder message = new StringBuilder("Archive content failed validation:");
				parserResult.getParseErrors().forEach((path, errors) -> errors.forEach(
						err -> message.append(" [").append(path).append("] ")
								.append(err.getMessage())));
				throw new BadRequestException(message.toString());
			}

			try {
				DBProject project = projectSeedService.createAndPublishProject(slug, metaData,
						scriptLoader);
				logger.info("Imported project '{}' from uploaded archive.", slug);
				return project;
			} catch (IOException e) {
				throw new InternalServerErrorException(
						"Failed to import project: " + e.getMessage());
			} catch (IllegalStateException e) {
				throw new BadRequestException(e.getMessage());
			}
		} finally {
			deleteRecursively(tempDir);
		}
	}

	/**
	 * Extracts {@code archive} into {@code targetDir}, rejecting any entry whose resolved path
	 * would escape {@code targetDir} ("zip slip") and enforcing the entry-count and
	 * uncompressed-size caps declared above.
	 *
	 * @param archive   the uploaded archive.
	 * @param targetDir the (already-created, empty) directory to extract into.
	 * @throws BadRequestException if the archive cannot be read, contains a path-traversal entry,
	 * or exceeds the entry-count/uncompressed-size caps.
	 */
	private void extractArchive(MultipartFile archive, Path targetDir) throws BadRequestException {
		long totalUncompressed = 0;
		int entryCount = 0;
		try (InputStream in = archive.getInputStream();
				ZipInputStream zip = new ZipInputStream(in)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entryCount++;
				if (entryCount > MAX_ENTRY_COUNT) {
					throw new BadRequestException("Archive contains too many entries (limit " +
							MAX_ENTRY_COUNT + ").");
				}

				Path resolved = targetDir.resolve(entry.getName()).normalize();
				if (!resolved.startsWith(targetDir)) {
					throw new BadRequestException(
							"Archive contains an entry with an invalid path: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(resolved);
					continue;
				}

				Files.createDirectories(resolved.getParent());
				try (OutputStream out = Files.newOutputStream(resolved)) {
					byte[] buffer = new byte[8192];
					int read;
					while ((read = zip.read(buffer)) != -1) {
						totalUncompressed += read;
						if (totalUncompressed > MAX_UNCOMPRESSED_SIZE_BYTES) {
							throw new BadRequestException("Archive is too large once decompressed " +
									"(limit " + (MAX_UNCOMPRESSED_SIZE_BYTES / (1024 * 1024)) +
									" MB).");
						}
						out.write(buffer, 0, read);
					}
				}
				zip.closeEntry();
			}
		} catch (IOException e) {
			throw new BadRequestException("Failed to read uploaded archive — is it a valid .zip " +
					"file? (" + e.getMessage() + ")");
		}
	}

	/**
	 * Recursively deletes {@code dir} and everything in it, best-effort — used to clean up the
	 * temporary extraction directory regardless of whether the import succeeded or failed.
	 *
	 * @param dir the directory to delete.
	 */
	private void deleteRecursively(Path dir) {
		if (dir == null) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					logger.warn("Failed to delete temporary import file '{}': {}", path,
							e.getMessage());
				}
			});
		} catch (IOException e) {
			logger.warn("Failed to clean up temporary import directory '{}': {}", dir,
					e.getMessage());
		}
	}

}
