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

import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;
import com.dialoguebranch.model.execute.LanguageSet;
import com.dialoguebranch.web.service.repository.DBLanguageSetRepository;
import com.dialoguebranch.web.service.repository.DBProjectLanguageMappingRepository;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.storage.model.DBLanguageSet;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectLanguageMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing {@link DBProject} records and their associated language mappings. Handles
 * creation, retrieval, update and deletion of projects, and provides conversion between the
 * flat {@link DBProjectLanguageMapping} rows and the structured {@link LanguageMap} domain model.
 *
 * @author Harm op den Akker
 */
@Service
public class ProjectService {

	private final DBProjectRepository projectRepository;
	private final DBProjectLanguageMappingRepository languageMappingRepository;
	private final DBLanguageSetRepository languageSetRepository;

	public ProjectService(DBProjectRepository projectRepository,
						  DBProjectLanguageMappingRepository languageMappingRepository,
						  DBLanguageSetRepository languageSetRepository) {
		this.projectRepository = projectRepository;
		this.languageMappingRepository = languageMappingRepository;
		this.languageSetRepository = languageSetRepository;
	}

	// ------------------------------------------------------------ //
	// -------------------- Project Management -------------------- //
	// ------------------------------------------------------------ //

	/**
	 * Returns all projects.
	 *
	 * @return list of all {@link DBProject}s.
	 */
	public List<DBProject> listProjects() {
		return projectRepository.findAll();
	}

	/**
	 * Returns the project with the given {@code slug}, or {@link Optional#empty()} if not found.
	 *
	 * @param slug the unique project slug.
	 * @return the matching project, or empty.
	 */
	public Optional<DBProject> findBySlug(String slug) {
		return projectRepository.findBySlug(slug);
	}

	/**
	 * Returns the project with the given {@code id}, or {@link Optional#empty()} if not found.
	 *
	 * @param id the project UUID.
	 * @return the matching project, or empty.
	 */
	public Optional<DBProject> findById(UUID id) {
		return projectRepository.findById(id);
	}

	/**
	 * Creates and persists a new project with the given attributes and no default language. Only
	 * used for seed projects, which define their own language map separately; every project
	 * created through the API must have a default language (see
	 * {@link #createProject(String, String, String, String, String)}).
	 *
	 * @param slug        the unique project slug.
	 * @param displayName the human-readable display name.
	 * @param description the project description.
	 * @return the newly created {@link DBProject}.
	 */
	public DBProject createProject(String slug, String displayName, String description) {
		Instant now = Instant.now();
		DBProject project = new DBProject();
		project.setSlug(slug);
		project.setDisplayName(displayName);
		project.setDescription(description);
		project.setCreatedAt(now);
		project.setUpdatedAt(now);
		return projectRepository.save(project);
	}

	/**
	 * Creates and persists a new project with the given attributes, along with its first
	 * {@link DBLanguageSet} — using the given source language, with no translations yet — which
	 * is immediately marked as the project's default language set. A project always has exactly
	 * one default language set among one or more defined language sets.
	 *
	 * @param slug                   the unique project slug.
	 * @param displayName            the human-readable display name.
	 * @param description            the project description.
	 * @param defaultLanguageCode    the ISO code of the project's default (source) language.
	 * @param defaultLanguageName    the human-readable name of the project's default language.
	 * @return the newly created {@link DBProject}.
	 */
	@Transactional
	public DBProject createProject(String slug, String displayName, String description,
									String defaultLanguageCode, String defaultLanguageName) {
		DBProject project = createProject(slug, displayName, description);

		DBLanguageSet languageSet = new DBLanguageSet();
		languageSet.setProject(project);
		languageSet.setSourceLanguageCode(defaultLanguageCode);
		languageSet.setSourceLanguageName(defaultLanguageName);
		languageSet = languageSetRepository.save(languageSet);

		project.setDefaultLanguageSet(languageSet);
		project.setUpdatedAt(Instant.now());
		return projectRepository.save(project);
	}

	/**
	 * Updates the display name and description of the given project.
	 *
	 * @param project     the project to update.
	 * @param displayName the new display name.
	 * @param description the new description.
	 * @return the updated {@link DBProject}.
	 */
	public DBProject updateProject(DBProject project, String displayName, String description) {
		project.setDisplayName(displayName);
		project.setDescription(description);
		project.setUpdatedAt(Instant.now());
		return projectRepository.save(project);
	}

	/**
	 * Saves the given project, persisting any changes made to it.
	 *
	 * @param project the project to save.
	 * @return the saved {@link DBProject}.
	 */
	public DBProject save(DBProject project) {
		return projectRepository.save(project);
	}

	/**
	 * Deletes the given project and all its associated data.
	 *
	 * @param project the project to delete.
	 */
	public void deleteProject(DBProject project) {
		projectRepository.delete(project);
	}

	// ----------------------------------------------------------------------- //
	// -------------------- Language Mapping Management -------------------- //
	// ----------------------------------------------------------------------- //

	/**
	 * Adds a source-to-translation language mapping to the given project.
	 *
	 * @param project             the owning project.
	 * @param sourceLanguage      the source {@link Language} (name + code).
	 * @param translationLanguage the translation {@link Language} (name + code).
	 * @return the newly created {@link DBProjectLanguageMapping}.
	 */
	public DBProjectLanguageMapping addLanguageMapping(DBProject project, Language sourceLanguage,
													   Language translationLanguage) {
		DBProjectLanguageMapping mapping = new DBProjectLanguageMapping();
		mapping.setProject(project);
		mapping.setSourceLanguageName(sourceLanguage.getName());
		mapping.setSourceLanguageCode(sourceLanguage.getCode());
		mapping.setTranslationLanguageName(translationLanguage.getName());
		mapping.setTranslationLanguageCode(translationLanguage.getCode());
		return languageMappingRepository.save(mapping);
	}

	/**
	 * Removes the language mapping with the given {@code id}.
	 *
	 * @param mappingId the UUID of the mapping to remove.
	 */
	public void removeLanguageMapping(UUID mappingId) {
		languageMappingRepository.deleteById(mappingId);
	}

	/**
	 * Builds a {@link LanguageMap} from all {@link DBProjectLanguageMapping} rows for the given
	 * project. Rows sharing the same {@code sourceLanguage} are grouped into a single
	 * {@link LanguageSet}.
	 *
	 * @param project the project whose language mappings to convert.
	 * @return the reconstructed {@link LanguageMap}.
	 */
	public LanguageMap getLanguageMap(DBProject project) {
		List<DBProjectLanguageMapping> mappings = languageMappingRepository.findByProject(project);

		Map<String, LanguageSet> setsBySource = new LinkedHashMap<>();
		for (DBProjectLanguageMapping mapping : mappings) {
			setsBySource.computeIfAbsent(
					mapping.getSourceLanguageCode(),
					src -> new LanguageSet(new Language(mapping.getSourceLanguageName(),
							mapping.getSourceLanguageCode()))
			).addTranslationLanguage(new Language(mapping.getTranslationLanguageName(),
					mapping.getTranslationLanguageCode()));
		}

		return new LanguageMap(new ArrayList<>(setsBySource.values()));
	}

}
