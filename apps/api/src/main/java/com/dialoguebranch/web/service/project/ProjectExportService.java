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

import com.dialoguebranch.editing.writer.ProjectMetaDataWriter;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.repository.DBProjectVersionRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslationLanguage;
import nl.rrd.utils.xml.XMLWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports a project's currently published content as a {@code .zip} archive laid out exactly like
 * a seed project directory ({@code dlb-project.xml} at the root, one sub-directory per language,
 * {@code .dlb}/{@code .json} files inside) — the same shape {@link ProjectSeedService} and {@code
 * ProjectImportService} read from, so an exported archive can be re-imported directly.
 *
 * <p>Exports the <em>published</em> snapshot ({@link DBProjectVersion}), not the draft — a project
 * that has never been published has nothing to export.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class ProjectExportService {

	private static final String PROJECT_MARKER_FILE = "dlb-project.xml";

	private final DBProjectVersionRepository versionRepository;

	/**
	 * Creates a new {@link ProjectExportService}.
	 *
	 * @param versionRepository repository used to re-fetch a project's latest version as a managed
	 *                          entity, so its lazily-fetched published dialogues/translations can be
	 *                          read within this method's own transaction.
	 */
	public ProjectExportService(DBProjectVersionRepository versionRepository) {
		this.versionRepository = versionRepository;
	}

	/**
	 * Builds the export archive for the given project's currently published version.
	 *
	 * @param project the project to export.
	 * @return the archive content as a byte array.
	 * @throws BadRequestException if {@code project} has never been published.
	 * @throws NotFoundException   if {@code project}'s latest version has disappeared between being
	 * loaded and this method running (should not normally happen).
	 */
	@Transactional(readOnly = true)
	public byte[] exportProject(DBProject project) throws BadRequestException, NotFoundException {
		if (project.getLatestVersion() == null) {
			throw new BadRequestException("Project '" + project.getSlug() +
					"' has not been published yet — there is nothing to export.");
		}
		// `project` (and its EAGER latestVersion) were loaded by the caller's own query, outside
		// this method's transaction — re-fetch the version by id so it's a managed entity attached
		// to *this* session, letting its LAZY publishedDialogues/publishedTranslationLanguages (and
		// each dialogue's LAZY translations) actually load below.
		DBProjectVersion version = versionRepository.findById(project.getLatestVersion().getId())
				.orElseThrow(() -> new NotFoundException(
						"Project version not found: " + project.getLatestVersion().getId()));

		ProjectMetaData metaData = new ProjectMetaData();
		metaData.setSlug(project.getSlug());
		metaData.setName(version.getDisplayName());
		metaData.setDescription(version.getDescription());
		metaData.setVersion(String.valueOf(version.getVersionNumber()));

		Language source = new Language(project.getSourceLanguageName(), project.getSourceLanguageCode());
		LanguageMap languageMap = new LanguageMap(source);
		for (DBPublishedTranslationLanguage language : version.getPublishedTranslationLanguages()) {
			languageMap.addTranslationLanguage(new Language(language.getTranslationLanguageName(),
					language.getTranslationLanguageCode()));
		}
		metaData.setLanguageMap(languageMap);

		ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(archiveBytes)) {
			writeMetaDataEntry(zip, metaData);

			String sourceLanguageCode = project.getSourceLanguageCode();
			for (DBPublishedDialogue dialogue : version.getPublishedDialogues()) {
				writeTextEntry(zip, sourceLanguageCode + "/" + dialogue.getName() +
						DialogueBranchConstants.DLB_SCRIPT_FILE_EXTENSION, dialogue.getContent());
				for (DBPublishedTranslation translation : dialogue.getTranslations()) {
					String languageCode =
							translation.getTranslationLanguage().getTranslationLanguageCode();
					writeTextEntry(zip, languageCode + "/" + dialogue.getName() +
							DialogueBranchConstants.DLB_TRANSLATION_FILE_EXTENSION,
							translation.getContent());
				}
			}
		} catch (IOException e) {
			// Writing to an in-memory ByteArrayOutputStream-backed zip cannot realistically fail —
			// treated as unexpected, resulting in a 500 (see QueryRunner's catch-all).
			throw new UncheckedIOException("Failed to build export archive for project '" +
					project.getSlug() + "'.", e);
		}
		return archiveBytes.toByteArray();
	}

	/**
	 * Writes {@code dlb-project.xml} as the first entry of the archive.
	 *
	 * @param zip      the archive to write to.
	 * @param metaData the project metadata to serialise.
	 * @throws IOException if writing fails.
	 */
	private void writeMetaDataEntry(ZipOutputStream zip, ProjectMetaData metaData)
			throws IOException {
		ByteArrayOutputStream xmlBytes = new ByteArrayOutputStream();
		// ProjectMetaDataWriter.writeToXMLFile closes the XMLWriter (and, transitively, this
		// ByteArrayOutputStream — a harmless no-op for that class) before returning.
		XMLWriter writer = new XMLWriter(xmlBytes);
		ProjectMetaDataWriter.writeToXMLFile(writer, metaData);
		zip.putNextEntry(new ZipEntry(PROJECT_MARKER_FILE));
		zip.write(xmlBytes.toByteArray());
		zip.closeEntry();
	}

	/**
	 * Writes a single UTF-8 text entry to the archive.
	 *
	 * @param zip       the archive to write to.
	 * @param entryName the archive-relative path of the entry (e.g. {@code "en/intro.dlb"}).
	 * @param content   the entry's text content ({@code null} is written as an empty file).
	 * @throws IOException if writing fails.
	 */
	private void writeTextEntry(ZipOutputStream zip, String entryName, String content)
			throws IOException {
		zip.putNextEntry(new ZipEntry(entryName));
		zip.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

}
