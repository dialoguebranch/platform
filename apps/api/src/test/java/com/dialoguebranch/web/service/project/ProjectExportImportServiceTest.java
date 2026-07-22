package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ProjectExportService} and {@link ProjectImportService}: a round trip of a
 * published project through export and re-import, and the validation/security guards import
 * enforces — rejecting an already-taken slug, an archive missing {@code dlb-project.xml} or whose
 * marker file has no {@code slug} attribute, and a zip-slip path-traversal entry — plus export's
 * guard against a project that has never been published.
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

}
