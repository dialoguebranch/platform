package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.controller.schema.authoring.AddTranslationLanguagePayload;
import com.dialoguebranch.web.service.controller.schema.authoring.UpdateDraftLanguagePayload;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DraftProjectService}: {@link DraftProjectService#addDraftLanguage} around
 * soft-deleted draft translation languages (re-adding a soft-deleted language's code used to
 * bypass the duplicate check and hit the {@code draft_translation_languages} unique constraint
 * directly instead of failing cleanly), {@link DraftProjectService#restoreDraftLanguage}, and
 * {@link DraftProjectService#updateDraft} — the atomic, validate-first batch endpoint backing the
 * Configure Project window's "Save Draft" action.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DraftProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Autowired
    private DraftProjectService draftProjectService;

    @Autowired
    private PublishService publishService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void addDraftLanguageRejectsCodeStillHeldBySoftDeletedLanguage() throws Exception {
        DBProject project = projectService.createProject("draft-lang-readd-test",
                "Draft Language Re-add Test", "", "en", "English");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Narrator", "");

        DBDraftTranslationLanguage language = draftProjectService.addDraftLanguage(project, "Dutch", "nl-NL");

        // Publish so the language has a published counterpart — removeDraftLanguage soft-deletes
        // (rather than hard-deletes) exactly because of this.
        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        draftProjectService.removeDraftLanguage(language);
        assertTrue(draftProjectService.findDraftLanguage(project, "nl-NL").orElseThrow().getIsDeleted());

        // Must fail cleanly with a ConflictException (not a DataIntegrityViolationException from
        // the unique constraint) — adding is not an implicit revive of a soft-deleted language.
        assertThrows(ConflictException.class,
                () -> draftProjectService.addDraftLanguage(project, "Dutch", "nl-NL"));
    }

    @Test
    void removeDraftLanguageHardDeletesNeverPublishedLanguageWithTranslationContentCleanly()
            throws Exception {
        DBProject project = projectService.createProject("draft-lang-hard-delete-test",
                "Draft Language Hard Delete Test", "", "en", "English");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Narrator", "");
        DBDraftTranslationLanguage language = draftProjectService.addDraftLanguage(project, "Dutch", "nl-NL");

        // Never published, so removeDraftLanguage hard-deletes it — this must not violate the
        // NOT NULL, non-cascading draft_translations -> draft_translation_languages foreign key.
        draftDialogueService.createOrUpdateTranslation(dialogue, language, "{}");

        draftProjectService.removeDraftLanguage(language);

        assertTrue(draftProjectService.findDraftLanguage(project, "nl-NL").isEmpty());
        assertTrue(draftDialogueService.findTranslation(dialogue, language).isEmpty());
    }

    @Test
    void restoreDraftLanguageRevertsAPendingDeletion() throws Exception {
        DBProject project = projectService.createProject("draft-lang-restore-test",
                "Draft Language Restore Test", "", "en", "English");
        DBDraftTranslationLanguage language = draftProjectService.addDraftLanguage(project, "Dutch", "nl-NL");

        // Only a published language is soft- (rather than hard-) deleted, so publish first.
        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        draftProjectService.removeDraftLanguage(language);
        assertTrue(draftProjectService.findDraftLanguage(project, "nl-NL").orElseThrow().getIsDeleted());

        draftProjectService.restoreDraftLanguage(language);
        assertFalse(draftProjectService.findDraftLanguage(project, "nl-NL").orElseThrow().getIsDeleted());

        // Now that it's active again, adding the same code must go back to being a clean conflict
        // (not silently succeed, and not throw a DB constraint error either).
        assertThrows(ConflictException.class,
                () -> draftProjectService.addDraftLanguage(project, "Dutch", "nl-NL"));
    }

    private static AddTranslationLanguagePayload addPayload(String name, String code) {
        AddTranslationLanguagePayload payload = new AddTranslationLanguagePayload();
        payload.setTranslationLanguageName(name);
        payload.setTranslationLanguageCode(code);
        return payload;
    }

    private static UpdateDraftLanguagePayload updatePayload(DBDraftTranslationLanguage language,
            String name, String code) {
        UpdateDraftLanguagePayload payload = new UpdateDraftLanguagePayload();
        payload.setId(language.getId());
        payload.setTranslationLanguageName(name);
        payload.setTranslationLanguageCode(code);
        return payload;
    }

    @Test
    void updateDraftAppliesWholeBatchInOneCall() throws Exception {
        DBProject project = projectService.createProject("update-draft-batch-test",
                "Update Draft Batch Test", "", "en", "English");
        DBDraftTranslationLanguage existing = draftProjectService.addDraftLanguage(project, "Dutch", "nl");

        DBProject updated = draftProjectService.updateDraft(project, "New Name", "New description",
                List.of(), List.of(addPayload("German", "de-DE")),
                List.of(updatePayload(existing, "Dutch (Netherlands)", "nl")));

        assertEquals("New Name", updated.getDraftDisplayName());
        assertEquals("New description", updated.getDraftDescription());
        assertEquals("Dutch (Netherlands)",
                draftProjectService.findDraftLanguage(project, "nl").orElseThrow().getTranslationLanguageName());
        assertTrue(draftProjectService.findDraftLanguage(project, "de-DE").isPresent());
    }

    @Test
    void updateDraftAppliesNothingWhenAnyPartOfTheBatchIsInvalid() {
        DBProject project = projectService.createProject("update-draft-atomic-test",
                "Update Draft Atomic Test", "", "en", "English");

        // Two additions claiming the same code — the batch as a whole must be rejected, and the
        // (valid) display name change and the (valid) first addition must NOT be half-applied.
        assertThrows(BadRequestException.class, () -> draftProjectService.updateDraft(project,
                "Should Not Be Saved", "",
                List.of(),
                List.of(addPayload("Dutch", "nl"), addPayload("Dutch (again)", "nl")),
                List.of()));

        assertNotEquals("Should Not Be Saved",
                projectService.findBySlug("update-draft-atomic-test").orElseThrow().getDraftDisplayName());
        assertTrue(draftProjectService.findDraftLanguage(project, "nl").isEmpty());
    }

    @Test
    void updateDraftAllowsSwappingCodesBetweenTwoLanguagesInOneBatch() throws Exception {
        DBProject project = projectService.createProject("update-draft-swap-test",
                "Update Draft Swap Test", "", "en", "English");
        DBDraftTranslationLanguage languageA = draftProjectService.addDraftLanguage(project, "Dutch", "nl");
        DBDraftTranslationLanguage languageB = draftProjectService.addDraftLanguage(project, "German", "de");

        // Neither rename's target code is free on its own mid-batch (each is still held by the
        // other language) — only the batch's FINAL state has no conflict. A sequential,
        // mutate-and-check validation would incorrectly reject this.
        draftProjectService.updateDraft(project, project.getDraftDisplayName(),
                project.getDraftDescription(), List.of(), List.of(),
                List.of(updatePayload(languageA, "Dutch", "de"), updatePayload(languageB, "German", "nl")));

        assertEquals("de",
                draftProjectService.findById(languageA.getId()).orElseThrow().getTranslationLanguageCode());
        assertEquals("nl",
                draftProjectService.findById(languageB.getId()).orElseThrow().getTranslationLanguageCode());
    }

    @Test
    void updateDraftRejectsALanguageThatIsBothRenamedAndRemovedInTheSameBatch() throws Exception {
        DBProject project = projectService.createProject("update-draft-remove-and-rename-test",
                "Update Draft Remove And Rename Test", "", "en", "English");
        DBDraftTranslationLanguage language = draftProjectService.addDraftLanguage(project, "Dutch", "nl");

        // Contradictory: the same language id is both being removed and renamed. Must be rejected
        // outright rather than guessed at — whether "removed" or "renamed" wins depends on
        // publish history the caller has no reason to reconstruct, so a clean error is safer than
        // either silently dropping the rename or crashing on a remove-then-merge conflict.
        assertThrows(BadRequestException.class, () -> draftProjectService.updateDraft(project,
                project.getDraftDisplayName(), project.getDraftDescription(),
                List.of(language.getId()), List.of(),
                List.of(updatePayload(language, "Dutch (Netherlands)", "nl-NL"))));

        // Nothing applied: the language is untouched, neither removed nor renamed.
        DBDraftTranslationLanguage unchanged = draftProjectService.findById(language.getId()).orElseThrow();
        assertFalse(unchanged.getIsDeleted());
        assertEquals("nl", unchanged.getTranslationLanguageCode());
    }

    @Test
    void publishResponseImmediatelyReflectsPublishedTranslationLanguages() throws Exception {
        DBProject project = projectService.createProject("publish-response-languages-test",
                "Publish Response Languages Test", "", "en", "English");
        draftProjectService.addDraftLanguage(project, "Dutch", "nl");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        // No entityManager.flush()/clear() here — this asserts directly against the same in-memory
        // DBProjectVersion object publish() returns, exactly as PublishController serializes it in
        // the HTTP response within that same request.
        assertTrue(publishResult.getVersion().getPublishedTranslationLanguages().stream()
                .anyMatch(l -> l.getTranslationLanguageCode().equals("nl")));
    }

    @Test
    void updateDraftRenamePublishesUnderNewCodeOnly() throws Exception {
        DBProject project = projectService.createProject("update-draft-rename-test",
                "Update Draft Rename Test", "", "en", "English");
        DBDraftTranslationLanguage language = draftProjectService.addDraftLanguage(project, "Dutch", "nl");
        PublishService.PublishResult firstPublish = publishService.publish(project, null);
        if (!firstPublish.isSuccess()) {
            throw new AssertionError("Publish failed: " + firstPublish.getErrors());
        }

        draftProjectService.updateDraft(project, project.getDraftDisplayName(),
                project.getDraftDescription(), List.of(), List.of(),
                List.of(updatePayload(language, "Dutch", "nl-NL")));

        PublishService.PublishResult secondPublish = publishService.publish(project, null);
        if (!secondPublish.isSuccess()) {
            throw new AssertionError("Publish failed: " + secondPublish.getErrors());
        }

        // Each publish snapshots a fresh set of published translation languages onto its own
        // version — the latest version's snapshot must reflect the new code only. flush() +
        // clear() force a genuinely fresh load below: within this single test's persistence
        // context, the in-memory DBProjectVersion instances already attached to the session (e.g.
        // from publish()'s own save() calls) don't automatically pick up sibling rows inserted via
        // a separate repository call, even via a fresh repository query — a first-level-cache
        // artifact of the test harness (a real, separate HTTP request always gets a fresh session,
        // and always flushes on commit — this is not a production concern).
        entityManager.flush();
        entityManager.clear();
        DBProjectVersion versionTwo = publishService.listVersions(project).stream()
                .filter(v -> v.getVersionNumber() == 2)
                .findFirst().orElseThrow();
        assertTrue(versionTwo.getPublishedTranslationLanguages().stream()
                .noneMatch(l -> l.getTranslationLanguageCode().equals("nl")));
        assertTrue(versionTwo.getPublishedTranslationLanguages().stream()
                .anyMatch(l -> l.getTranslationLanguageCode().equals("nl-NL")));
    }

    @Test
    void updateDraftRenamePreservesEarlierVersionsOriginalLanguageSnapshot() throws Exception {
        DBProject project = projectService.createProject("update-draft-rename-history-test",
                "Update Draft Rename History Test", "", "en", "English");
        DBDraftTranslationLanguage language = draftProjectService.addDraftLanguage(project, "Dutch", "nl");
        PublishService.PublishResult firstPublish = publishService.publish(project, null);
        if (!firstPublish.isSuccess()) {
            throw new AssertionError("Publish failed: " + firstPublish.getErrors());
        }

        draftProjectService.updateDraft(project, project.getDraftDisplayName(),
                project.getDraftDescription(), List.of(), List.of(),
                List.of(updatePayload(language, "Dutch", "nl-NL")));
        PublishService.PublishResult secondPublish = publishService.publish(project, null);
        if (!secondPublish.isSuccess()) {
            throw new AssertionError("Publish failed: " + secondPublish.getErrors());
        }

        // Version 1's own snapshot must still show the original code, proving true historical
        // accuracy — this is the entire point of snapshotting per version instead of upserting
        // into a single, project-wide current-state table. See the flush()+clear() comment in the
        // test above for why this is needed to observe a genuinely fresh load here.
        entityManager.flush();
        entityManager.clear();
        DBProjectVersion versionOne = publishService.listVersions(project).stream()
                .filter(v -> v.getVersionNumber() == 1)
                .findFirst().orElseThrow();
        assertTrue(versionOne.getPublishedTranslationLanguages().stream()
                .anyMatch(l -> l.getTranslationLanguageCode().equals("nl")));
    }

    @Test
    void updateDraftAllowsReusingACodeFreedByRemovingANeverPublishedLanguageInTheSameBatch()
            throws Exception {
        DBProject project = projectService.createProject("update-draft-reuse-code-test",
                "Update Draft Reuse Code Test", "", "en", "English");
        DBDraftTranslationLanguage language = draftProjectService.addDraftLanguage(project, "Dutch", "nl");
        // Never published, so removing it hard-deletes it immediately and genuinely frees "nl".

        DBProject updated = draftProjectService.updateDraft(project, project.getDraftDisplayName(),
                project.getDraftDescription(), List.of(language.getId()),
                List.of(addPayload("Dutch (new)", "nl")), List.of());

        assertTrue(draftProjectService.findDraftLanguage(project, "nl").isPresent());
        assertFalse(draftProjectService.findDraftLanguage(project, "nl").orElseThrow().getIsDeleted());
        assertNotNull(updated);
    }

}
