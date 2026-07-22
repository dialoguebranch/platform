package com.dialoguebranch.web.service.project;

import com.dialoguebranch.execution.parser.ProjectMetaDataParser;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.execution.SpringResourceScriptLoader;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ProjectExportService} and {@link ProjectImportService}: a round trip of a
 * published project through export and re-import, a file-by-file comparison of the classpath-seeded
 * "default-test" project's own source files against its export archive, and the validation/security
 * guards import enforces — rejecting an already-taken slug, an archive missing {@code
 * dlb-project.xml} or whose marker file has no {@code slug} attribute, and a zip-slip path-traversal
 * entry — plus export's guard against a project that has never been published.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectExportImportServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Autowired
    private DraftProjectService draftProjectService;

    @Autowired
    private PublishService publishService;

    @Autowired
    private ProjectExportService projectExportService;

    @Autowired
    private ProjectImportService projectImportService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void exportThenImportRoundTripsSlugDialoguesAndTranslations() throws Exception {
        DBProject original = createAndPublishSimpleProject("export-roundtrip-test");

        byte[] archive = projectExportService.exportProject(original);

        // Free up the slug so re-importing this exact archive doesn't hit the already-exists
        // conflict, which is covered by its own test below.
        projectService.deleteProject(original);

        DBProject imported = projectImportService.importProject(zipMultipartFile(archive,
                "export-roundtrip-test.zip"));
        assertEquals("export-roundtrip-test", imported.getSlug());

        // The publish inside ProjectSeedService.createAndPublishProject (called by importProject)
        // leaves the newly created version's published-dialogues collection stale in this test's
        // single persistence context — a real HTTP request always gets a fresh session and always
        // sees it correctly (see the flush()+clear() comments in DraftProjectServiceTest for the
        // same, pre-existing test-only artifact). A fresh reload is needed to actually observe it.
        entityManager.flush();
        entityManager.clear();
        DBProject reloaded = projectService.findBySlug("export-roundtrip-test").orElseThrow();
        DBProjectVersion version = reloaded.getLatestVersion();
        assertNotNull(version);
        assertEquals(1, version.getPublishedDialogues().size());
        DBPublishedDialogue publishedDialogue = version.getPublishedDialogues().iterator().next();
        assertEquals("basic", publishedDialogue.getName());
        assertTrue(publishedDialogue.getContent().contains("title: Start"));
        assertTrue(publishedDialogue.getContent().contains("Hello there!"));

        assertEquals(1, publishedDialogue.getTranslations().size());
        DBPublishedTranslation translation = publishedDialogue.getTranslations().iterator().next();
        assertEquals("nl-NL", translation.getTranslationLanguage().getTranslationLanguageCode());
        assertEquals("{\"Hello there!\":\"Hallo daar!\"}", translation.getContent());

        // The import must also seed a draft copy, not just the published snapshot — the same
        // guarantee ProjectSeedService.createAndPublishProject gives classpath-seeded projects.
        assertTrue(draftDialogueService.findDialogue(reloaded, "basic").isPresent());
    }

    @Test
    void importPreservesExportedVersionNumberInsteadOfRestartingAtOne() throws Exception {
        DBProject project = createAndPublishSimpleProject("import-version-test");

        // Publish a second time (no draft changes needed) to advance the project to version 2 —
        // publish always writes a fresh snapshot of the current draft state regardless of whether
        // anything actually changed (see PublishService.publish's own Javadoc).
        PublishService.PublishResult secondPublish = publishService.publish(project, null);
        if (!secondPublish.isSuccess()) {
            throw new AssertionError("Second publish failed: " + secondPublish.getErrors());
        }
        entityManager.flush();
        entityManager.clear();
        project = projectService.findBySlug("import-version-test").orElseThrow();
        assertEquals(2, project.getLatestVersion().getVersionNumber());

        byte[] archive = projectExportService.exportProject(project);
        projectService.deleteProject(project);

        DBProject imported = projectImportService.importProject(
                zipMultipartFile(archive, "import-version-test.zip"));
        assertEquals("import-version-test", imported.getSlug());

        // Same flush()+clear()+reload as the round-trip test above, for consistency with the
        // established pattern in this file.
        entityManager.flush();
        entityManager.clear();
        DBProject reloaded = projectService.findBySlug("import-version-test").orElseThrow();
        assertEquals(2, reloaded.getLatestVersion().getVersionNumber(),
                "Import should preserve the exported archive's stated version number (2), not " +
                        "restart the new project at version 1");
    }

    @Test
    void exportedArchiveMatchesSeedSourceFileByFile() throws Exception {
        // "default-test" is seeded once per test JVM by ProjectSeedService at application startup
        // (see the same reliance in DraftDialogueServiceTest) — not seeded by this test itself.
        DBProject project = projectService.findBySlug("default-test").orElseThrow();

        byte[] archive = projectExportService.exportProject(project);
        Map<String, byte[]> exportedEntries = readZipEntries(archive);

        // Read the seed project's own source files the exact same way ProjectSeedService does
        // when seeding from the classpath, so this test walks the real seed layout (including its
        // "bg1"/"bg1/test/scripts" sub-folders) rather than a hand-maintained file list.
        SpringResourceScriptLoader seedLoader =
                new SpringResourceScriptLoader("projects-seed/default-test");
        List<ResourcePointer> seedFiles = seedLoader.listDialogueBranchFiles();
        assertFalse(seedFiles.isEmpty(), "Seed project 'default-test' should have source files");

        // dlb-project.xml is compared separately below (semantically, not byte-for-byte) — every
        // other seed file must appear in the export with byte-identical content.
        assertEquals(seedFiles.size() + 1, exportedEntries.size(),
                "Exported archive should contain exactly the seed's dialogue/translation files " +
                        "plus dlb-project.xml — no extra or missing entries");

        for (ResourcePointer pointer : seedFiles) {
            String extension = pointer.getResourceType() == ResourceType.SCRIPT
                    ? ".dlb" : ".json";
            String entryName = pointer.getLanguage() + "/" + pointer.getDialogueName() + extension;

            byte[] expectedContent = readAllBytes(seedLoader.openFile(pointer));
            byte[] actualContent = exportedEntries.get(entryName);
            assertNotNull(actualContent, "Export archive is missing entry: " + entryName);
            assertArrayEquals(expectedContent, actualContent,
                    "Content of '" + entryName + "' differs between seed source and export");
        }

        // dlb-project.xml itself is regenerated by ProjectExportService (ProjectMetaDataWriter),
        // not copied verbatim — its exact XML formatting (and the added `slug` attribute) is
        // expected to differ from the hand-authored seed file, so compare parsed metadata instead
        // of raw bytes.
        ProjectMetaData seedMetaData = parseSeedProjectMetaData();
        ProjectMetaData exportedMetaData =
                parseExportedProjectMetaData(exportedEntries.get("dlb-project.xml"));

        assertEquals(seedMetaData.getName(), exportedMetaData.getName());
        assertEquals(seedMetaData.getDescription(), exportedMetaData.getDescription());
        assertEquals("default-test", exportedMetaData.getSlug());
        assertEquals(seedMetaData.getLanguageMap().getSourceLanguage().getCode(),
                exportedMetaData.getLanguageMap().getSourceLanguage().getCode());
        assertEquals(seedMetaData.getLanguageMap().getSourceLanguage().getName(),
                exportedMetaData.getLanguageMap().getSourceLanguage().getName());
        assertEquals(languageCodesToNames(seedMetaData.getLanguageMap().getTranslationLanguages()),
                languageCodesToNames(exportedMetaData.getLanguageMap().getTranslationLanguages()));
    }

    @Test
    void importRejectsSlugThatAlreadyExists() throws Exception {
        DBProject existing = createAndPublishSimpleProject("import-conflict-test");
        byte[] archive = projectExportService.exportProject(existing);

        MockMultipartFile upload = zipMultipartFile(archive, "import-conflict-test.zip");

        assertThrows(ConflictException.class, () -> projectImportService.importProject(upload));
    }

    @Test
    void importRejectsArchiveWithoutProjectMarkerFileAtRoot() {
        byte[] archive = buildZip(new String[] {"readme.txt", "not a dlb project"});
        MockMultipartFile upload = zipMultipartFile(archive, "no-marker.zip");

        assertThrows(BadRequestException.class, () -> projectImportService.importProject(upload));
    }

    @Test
    void importRejectsProjectMarkerFileWithoutSlugAttribute() {
        String xmlWithoutSlug = """
                <?xml version="1.0" encoding="UTF-8"?>
                <dlb-project name="No Slug" version="1">
                  <description>No slug attribute.</description>
                  <language-map>
                    <source-language name="English" code="en"/>
                  </language-map>
                </dlb-project>
                """;
        byte[] archive = buildZip(new String[] {"dlb-project.xml", xmlWithoutSlug});
        MockMultipartFile upload = zipMultipartFile(archive, "no-slug.zip");

        assertThrows(BadRequestException.class, () -> projectImportService.importProject(upload));
    }

    @Test
    void importRejectsZipSlipEntryWithoutWritingOutsideExtractionDirectory() throws Exception {
        byte[] archive = buildZip(
                new String[] {"../../dlb-import-zip-slip-marker.txt", "escaped!"});
        MockMultipartFile upload = zipMultipartFile(archive, "zip-slip.zip");

        assertThrows(BadRequestException.class, () -> projectImportService.importProject(upload));

        // ProjectImportService always extracts into a fresh Files.createTempDirectory("dlb-import-
        // ", ...) directory living directly inside java.io.tmpdir, so an entry named
        // "../../<name>" — if it were ever written instead of rejected — would land exactly here,
        // two levels up from that temp directory.
        Path javaTmpDir = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        Path escapedFile = javaTmpDir.getParent().resolve("dlb-import-zip-slip-marker.txt");
        assertFalse(Files.exists(escapedFile),
                "Zip-slip entry must not have been written outside the temp extraction directory");
    }

    @Test
    void exportRejectsProjectWithNoPublishedVersion() {
        DBProject unpublished = projectService.createProject("export-unpublished-test",
                "Export Unpublished Test", "", "en", "English");

        assertThrows(BadRequestException.class,
                () -> projectExportService.exportProject(unpublished));
    }

    // --------------------------------------------------------------- //
    // -------------------- Private Helper Methods -------------------- //
    // --------------------------------------------------------------- //

    /**
     * Creates, populates (one dialogue with one node, one translation language and translation),
     * and publishes a minimal project, ready to be exported.
     *
     * @param slug the unique slug for the new project.
     * @return the published project, freshly reloaded after the publish (see the flush()+clear()
     * comment inside for why that reload is necessary).
     */
    private DBProject createAndPublishSimpleProject(String slug) throws Exception {
        DBProject project = projectService.createProject(slug, "Export Test " + slug,
                "A test project.", "en", "English");
        DBDraftTranslationLanguage dutch =
                draftProjectService.addDraftLanguage(project, "Dutch", "nl-NL");
        var dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Narrator",
                "Hello there!");
        draftDialogueService.createOrUpdateTranslation(dialogue, dutch,
                "{\"Hello there!\":\"Hallo daar!\"}");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        // publish()'s newly created DBProjectVersion never has its published-dialogues collection
        // populated in-memory (only the owning FK side is written) — reading it back within this
        // same persistence context would see a stale, spuriously-empty collection. Reloading after
        // flush()+clear() forces a genuinely fresh read, exactly as ProjectExportService's own
        // re-fetch does across separate HTTP requests in production.
        entityManager.flush();
        entityManager.clear();
        return projectService.findBySlug(slug).orElseThrow();
    }

    private static MockMultipartFile zipMultipartFile(byte[] content, String filename) {
        return new MockMultipartFile("file", filename, "application/zip", content);
    }

    /**
     * Builds an in-memory {@code .zip} archive with one entry per {@code [name, content]} pair.
     *
     * @param entries the entries to write, each as a {@code {name, content}} pair.
     * @return the archive content as a byte array.
     */
    private static byte[] buildZip(String[]... entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * Unzips an in-memory archive into a map of entry name to raw content bytes.
     *
     * @param archive the archive content.
     * @return the archive's entries, keyed by entry name.
     */
    private static Map<String, byte[]> readZipEntries(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), readAllBytes(zip));
            }
        }
        return entries;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    private static byte[] readAllBytes(Reader reader) throws IOException {
        return readerToString(reader).getBytes(StandardCharsets.UTF_8);
    }

    private static String readerToString(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
        return sb.toString();
    }

    /**
     * Parses the seed project's own {@code dlb-project.xml} from the classpath, the same source
     * {@link ProjectSeedService} reads when seeding "default-test" at application startup.
     *
     * @return the parsed seed metadata.
     */
    private static ProjectMetaData parseSeedProjectMetaData() throws Exception {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();
        Resource marker = resolver.getResource(
                "classpath:projects-seed/default-test/dlb-project.xml");
        File tempFile = Files.createTempFile("seed-dlb-project-", ".xml").toFile();
        tempFile.deleteOnExit();
        try (InputStream in = marker.getInputStream();
             OutputStream out = Files.newOutputStream(tempFile.toPath())) {
            in.transferTo(out);
        }
        return ProjectMetaDataParser.parse(tempFile);
    }

    /**
     * Parses an exported archive's {@code dlb-project.xml} entry content.
     *
     * @param entryContent the raw bytes of the {@code dlb-project.xml} entry.
     * @return the parsed exported metadata.
     */
    private static ProjectMetaData parseExportedProjectMetaData(byte[] entryContent)
            throws Exception {
        File tempFile = Files.createTempFile("exported-dlb-project-", ".xml").toFile();
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), entryContent);
        return ProjectMetaDataParser.parse(tempFile);
    }

    /**
     * Reduces a list of {@link Language}s to a code-to-name map, for order-independent comparison
     * (translation-language order isn't guaranteed to match between the seed's XML file order and
     * the published registry's own iteration order).
     */
    private static Map<String, String> languageCodesToNames(List<Language> languages) {
        Map<String, String> byCode = new HashMap<>();
        for (Language language : languages) {
            byCode.put(language.getCode(), language.getName());
        }
        return byCode;
    }

}
