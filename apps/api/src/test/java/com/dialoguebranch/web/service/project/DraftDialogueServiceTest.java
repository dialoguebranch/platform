package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftNode;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the node-reference-scanning and rename logic added to {@link DraftDialogueService}
 * for the dialogue editor feature. Uses the same H2 in-memory {@code test} profile as {@link
 * com.dialoguebranch.web.service.ApiIntegrationTest}; each test runs in its own rolled-back
 * transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DraftDialogueServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Autowired
    private PublishService publishService;

    @Test
    void findNodeReferencesFindsInternalReference() {
        DBProject project = projectService.createProject("ref-test-internal", "Ref Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nposition: 0,0", "[[Continue|Middle]]");
        draftDialogueService.createNode(dialogue, "Middle",
                "title: Middle\nposition: 100,0", "Hello.");

        List<DraftDialogueService.NodeReference> refs =
                draftDialogueService.findNodeReferences(project, "main", "Middle");

        assertEquals(1, refs.size());
        assertEquals("main", refs.get(0).getDialogueName());
        assertEquals("Start", refs.get(0).getNodeTitle());
        assertEquals("[[Continue|Middle]]", refs.get(0).getSnippet());
    }

    @Test
    void findNodeReferencesFindsExternalReferenceAcrossDialogues() {
        DBProject project = projectService.createProject("ref-test-external", "Ref Test", "");
        DBDraftDialogue origin = draftDialogueService.createDialogue(project, "folderA/One");
        // A bare (no-slash, no-dot) dialogue reference resolves relative to the origin's own
        // folder — see ExternalNodePointer.getAbsoluteDialogueId — so "dialogueTwo.End" from
        // "folderA/One" resolves to "folderA/dialogueTwo" + node "End".
        draftDialogueService.createNode(origin, "Start",
                "title: Start\nposition: 0,0", "[[Go there|dialogueTwo.End]]");
        DBDraftDialogue target = draftDialogueService.createDialogue(project, "folderA/dialogueTwo");
        draftDialogueService.createNode(target, "End",
                "title: End\nposition: 0,0", "The end.");

        List<DraftDialogueService.NodeReference> refs =
                draftDialogueService.findNodeReferences(project, "folderA/dialogueTwo", "End");

        assertEquals(1, refs.size());
        assertEquals("folderA/One", refs.get(0).getDialogueName());
        assertEquals("Start", refs.get(0).getNodeTitle());
    }

    @Test
    void findNodeReferencesIgnoresUnrelatedReplies() {
        DBProject project = projectService.createProject("ref-test-unrelated", "Ref Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nposition: 0,0", "[[Continue|Other]]");
        draftDialogueService.createNode(dialogue, "Other",
                "title: Other\nposition: 50,0", "Not the target.");
        draftDialogueService.createNode(dialogue, "Middle",
                "title: Middle\nposition: 100,0", "Nobody points here.");

        assertTrue(draftDialogueService.findNodeReferences(project, "main", "Middle").isEmpty());
    }

    @Test
    void renameNodeUpdatesTitleAndRewritesReferencesWhenRequested() throws Exception {
        DBProject project = projectService.createProject("rename-test-rewrite", "Rename Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nposition: 0,0",
                "[[Continue|Middle]]\n[[Loop back|Middle|<<action type=\"x\" value=\"y\">>]]");
        DBDraftNode middle = draftDialogueService.createNode(dialogue, "Middle",
                "title: Middle\nposition: 100,0", "Hello.");

        DraftDialogueService.RenameResult result =
                draftDialogueService.renameNode(project, dialogue, middle, "MiddleRenamed", true);

        assertEquals("MiddleRenamed", result.getNode().getTitle());
        assertTrue(result.getNode().getHeader().contains("title: MiddleRenamed"));
        // Two individual reply links, both from the same referencing node ("Start").
        assertEquals(2, result.getReferencesUpdated());

        DBDraftNode reloadedStart = draftDialogueService.findNode(dialogue, "Start").orElseThrow();
        assertTrue(reloadedStart.getBody().contains("[[Continue|MiddleRenamed]]"));
        assertTrue(reloadedStart.getBody()
                .contains("[[Loop back|MiddleRenamed|<<action type=\"x\" value=\"y\">>]]"));
        assertFalse(reloadedStart.getBody().contains("|Middle]]"));
    }

    @Test
    void renameNodeLeavesReferencesUntouchedWhenNotRequested() throws Exception {
        DBProject project = projectService.createProject("rename-test-no-rewrite", "Rename Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nposition: 0,0", "[[Continue|Middle]]");
        DBDraftNode middle = draftDialogueService.createNode(dialogue, "Middle",
                "title: Middle\nposition: 100,0", "Hello.");

        draftDialogueService.renameNode(project, dialogue, middle, "MiddleRenamed", false);

        DBDraftNode reloadedStart = draftDialogueService.findNode(dialogue, "Start").orElseThrow();
        assertTrue(reloadedStart.getBody().contains("[[Continue|Middle]]"));
    }

    @Test
    void renameNodeRejectsDuplicateTitleWithinDialogue() {
        DBProject project = projectService.createProject("rename-test-duplicate", "Rename Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Start", "title: Start", "");
        DBDraftNode middle = draftDialogueService.createNode(dialogue, "Middle", "title: Middle", "");

        assertThrows(ConflictException.class, () ->
                draftDialogueService.renameNode(project, dialogue, middle, "Start", true));
    }

    @Test
    void renameNodeRejectsInvalidTitleFormat() {
        DBProject project = projectService.createProject("rename-test-invalid", "Rename Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        DBDraftNode node = draftDialogueService.createNode(dialogue, "Start", "title: Start", "");

        assertThrows(BadRequestException.class, () ->
                draftDialogueService.renameNode(project, dialogue, node, "invalid title!", true));
    }

    @Test
    void renameNodeMarksBothDialoguesChanged() throws Exception {
        DBProject project = projectService.createProject("rename-node-flags-test", "Rename Test", "");
        DBDraftDialogue main = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(main, "Start",
                "title: Start\nspeaker: Narrator\nposition: 0,0", "[[Go|other.Target]]");
        DBDraftDialogue other = draftDialogueService.createDialogue(project, "other");
        draftDialogueService.createNode(other, "Start",
                "title: Start\nspeaker: Narrator\nposition: 0,0", "[[Go|Target]]");
        DBDraftNode target = draftDialogueService.createNode(other, "Target",
                "title: Target\nspeaker: Narrator\nposition: 100,0", "");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }
        assertFalse(draftDialogueService.findDialogue(project, "main").orElseThrow().getIsChanged());
        assertFalse(draftDialogueService.findDialogue(project, "other").orElseThrow().getIsChanged());

        draftDialogueService.renameNode(project, other, target, "TargetRenamed", true);

        assertTrue(draftDialogueService.findDialogue(project, "main").orElseThrow().getIsChanged());
        assertTrue(draftDialogueService.findDialogue(project, "other").orElseThrow().getIsChanged());
    }

    @Test
    void renameDialogueUpdatesNameAndRewritesReferencesWhenRequested() throws Exception {
        DBProject project = projectService.createProject("rename-dialogue-rewrite", "Rename Dialogue Test", "");
        DBDraftDialogue main = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(main, "Start",
                "title: Start\nspeaker: Narrator\nposition: 0,0", "[[Go to menu|menu.Start]]");
        DBDraftDialogue menu = draftDialogueService.createDialogue(project, "menu");
        draftDialogueService.createNode(menu, "Start",
                "title: Start\nspeaker: Narrator\nposition: 0,0", "");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        DraftDialogueService.DialogueRenameResult result =
                draftDialogueService.renameDialogue(project, menu, "main-menu", true);

        assertEquals("main-menu", result.getDialogue().getName());
        assertEquals("menu", result.getDialogue().getRenamedFrom());
        assertEquals(1, result.getReferencesUpdated());

        DBDraftNode reloadedStart = draftDialogueService.findNode(main, "Start").orElseThrow();
        assertTrue(reloadedStart.getBody().contains("[[Go to menu|./main-menu.Start]]"));
    }

    @Test
    void renameDialogueLeavesReferencesUntouchedWhenNotRequested() throws Exception {
        DBProject project = projectService.createProject("rename-dialogue-no-rewrite", "Rename Dialogue Test", "");
        DBDraftDialogue main = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(main, "Start",
                "title: Start\nposition: 0,0", "[[Go to menu|menu.Start]]");
        DBDraftDialogue menu = draftDialogueService.createDialogue(project, "menu");
        draftDialogueService.createNode(menu, "Start", "title: Start\nposition: 0,0", "");

        draftDialogueService.renameDialogue(project, menu, "main-menu", false);

        DBDraftNode reloadedStart = draftDialogueService.findNode(main, "Start").orElseThrow();
        assertTrue(reloadedStart.getBody().contains("[[Go to menu|menu.Start]]"));
    }

    @Test
    void renameDialogueRejectsDuplicateNameAndInvalidFormat() {
        DBProject project = projectService.createProject("rename-dialogue-invalid", "Rename Dialogue Test", "");
        DBDraftDialogue main = draftDialogueService.createDialogue(project, "main");
        DBDraftDialogue menu = draftDialogueService.createDialogue(project, "menu");

        assertThrows(ConflictException.class, () ->
                draftDialogueService.renameDialogue(project, menu, "main", true));
        assertThrows(BadRequestException.class, () ->
                draftDialogueService.renameDialogue(project, menu, "invalid name!", true));
    }

    @Test
    void renameDialogueChainKeepsOriginalRenamedFromUntilPublished() throws Exception {
        DBProject project = projectService.createProject("rename-dialogue-chain", "Rename Dialogue Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "a");
        draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Narrator", "");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        draftDialogueService.renameDialogue(project, dialogue, "b", false);
        assertEquals("a", dialogue.getRenamedFrom());

        draftDialogueService.renameDialogue(project, dialogue, "c", false);
        assertEquals("a", dialogue.getRenamedFrom());
    }

    @Test
    void findOrCreateDraftDialogueCopiesFromPublishedWhenNoDraftExists() throws Exception {
        DBProject project = projectService.createProject("auto-draft-test", "Auto Draft Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nspeaker: Narrator\nposition: 0,0", "[[Continue|End]]");
        draftDialogueService.createNode(dialogue, "End",
                "title: End\nspeaker: Narrator\nposition: 100,0", "");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        // Simulate exactly the reported bug scenario: a dialogue that has a published version
        // but no draft dialogue row at all (e.g. one that was hard-deleted as part of an earlier
        // publish's pending-deletion cleanup).
        draftDialogueService.hardDeleteDialogue(dialogue);
        assertTrue(draftDialogueService.findDialogue(project, "basic").isEmpty());

        DBDraftDialogue recreated = draftDialogueService
                .findOrCreateDraftDialogue(project, "basic")
                .orElseThrow();

        List<DBDraftNode> nodes = draftDialogueService.listNodes(recreated);
        assertEquals(2, nodes.size());
        assertEquals("Start", nodes.get(0).getTitle());
        assertTrue(nodes.get(0).getHeader().contains("title: Start"));
        assertTrue(nodes.get(0).getBody().contains("[[Continue|End]]"));
        assertEquals("End", nodes.get(1).getTitle());

        // A second call should just find the now-existing draft rather than copying again.
        DBDraftDialogue again = draftDialogueService.findOrCreateDraftDialogue(project, "basic")
                .orElseThrow();
        assertEquals(recreated.getId(), again.getId());
    }

    @Test
    void findOrCreateDraftDialogueReturnsEmptyWhenDialogueDoesNotExistAtAll() {
        DBProject project = projectService.createProject("auto-draft-missing", "Auto Draft Test", "");

        assertTrue(draftDialogueService.findOrCreateDraftDialogue(project, "nonexistent").isEmpty());
    }

    @Test
    void createDialogueSetsIsNewAndIsChanged() {
        DBProject project = projectService.createProject("flags-test-create", "Flags Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");

        assertTrue(dialogue.getIsNew());
        assertTrue(dialogue.getIsChanged());
        assertFalse(dialogue.getIsDeleted());
    }

    @Test
    void findOrCreateDraftDialogueResetsFlagsWhenCopyingFromPublished() throws Exception {
        DBProject project = projectService.createProject("flags-test-copy", "Flags Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nspeaker: Narrator\nposition: 0,0", "");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        draftDialogueService.hardDeleteDialogue(dialogue);
        DBDraftDialogue recreated = draftDialogueService.findOrCreateDraftDialogue(project, "basic")
                .orElseThrow();

        assertFalse(recreated.getIsNew());
        assertFalse(recreated.getIsChanged());
    }

    @Test
    void nodeMutationsMarkDialogueChanged() throws Exception {
        DBProject project = projectService.createProject("flags-test-node-mutations", "Flags Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        DBDraftNode start = draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nspeaker: Narrator\nposition: 0,0", "");

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }
        DBDraftDialogue reloaded = draftDialogueService.findDialogue(project, "basic").orElseThrow();
        assertFalse(reloaded.getIsChanged());

        draftDialogueService.updateNode(start, start.getHeader(), "Edited after publish.");
        assertTrue(draftDialogueService.findDialogue(project, "basic").orElseThrow().getIsChanged());
    }

    @Test
    void deleteDialogueSoftDeletesAndRestoreReverts() {
        DBProject project = projectService.createProject("soft-delete-test", "Soft Delete Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start", "title: Start", "");

        draftDialogueService.deleteDialogue(dialogue);
        DBDraftDialogue afterDelete = draftDialogueService.findDialogue(project, "basic")
                .orElseThrow();
        assertTrue(afterDelete.getIsDeleted());
        assertEquals(1, draftDialogueService.listNodes(afterDelete).size());

        draftDialogueService.restoreDialogue(afterDelete);
        DBDraftDialogue afterRestore = draftDialogueService.findDialogue(project, "basic")
                .orElseThrow();
        assertFalse(afterRestore.getIsDeleted());
    }

    @Test
    void publishExcludesDeletedDialoguesAndHardDeletesThem() throws Exception {
        DBProject project = projectService.createProject("publish-delete-test", "Publish Delete Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Narrator", "");
        PublishService.PublishResult firstPublish = publishService.publish(project, null);
        if (!firstPublish.isSuccess()) {
            throw new AssertionError("Publish failed: " + firstPublish.getErrors());
        }

        draftDialogueService.deleteDialogue(dialogue);
        PublishService.PublishResult secondPublish = publishService.publish(project, null);
        if (!secondPublish.isSuccess()) {
            throw new AssertionError("Publish failed: " + secondPublish.getErrors());
        }

        assertTrue(draftDialogueService.findDialogue(project, "basic").isEmpty());
        // Once truly gone from the published set, re-opening it for editing should treat it as a
        // brand new dialogue, not resurrect the deleted one.
        assertTrue(draftDialogueService.findOrCreateDraftDialogue(project, "basic").isEmpty());
    }

    @Test
    void publishResetsFlagsForPublishedDialogues() throws Exception {
        DBProject project = projectService.createProject("publish-reset-test", "Publish Reset Test", "");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "basic");
        draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Narrator", "");
        assertTrue(dialogue.getIsNew());
        assertTrue(dialogue.getIsChanged());

        PublishService.PublishResult publishResult = publishService.publish(project, null);
        if (!publishResult.isSuccess()) {
            throw new AssertionError("Publish failed: " + publishResult.getErrors());
        }

        DBDraftDialogue reloaded = draftDialogueService.findDialogue(project, "basic").orElseThrow();
        assertFalse(reloaded.getIsNew());
        assertFalse(reloaded.getIsChanged());
    }

    @Test
    void createDialogueFromScriptSplitsNodesAndSkipsUntitledBlocks() {
        DBProject project = projectService.createProject("from-script-test", "From Script Test", "");
        String script = "title: Start\nspeaker: Narrator\nposition: 0,0\n---\n[[Continue|End]]\n===\n"
                + "title: End\nspeaker: Narrator\nposition: 100,0\n---\n\n===\n";

        DBDraftDialogue dialogue =
                draftDialogueService.createDialogueFromScript(project, "basic", script);

        List<DBDraftNode> nodes = draftDialogueService.listNodes(dialogue);
        assertEquals(2, nodes.size());
        assertEquals("Start", nodes.get(0).getTitle());
        assertTrue(nodes.get(0).getBody().contains("[[Continue|End]]"));
        assertEquals("End", nodes.get(1).getTitle());
    }

    @Test
    void seededProjectHasDraftsAndTranslationsNotJustPublishedContent() {
        // "default-test" is seeded once per test JVM by ProjectSeedService (see
        // src/test/resources/application-test.yml) — this checks that the new
        // seed-drafts-then-publish path actually populated the draft layer (not just the
        // published tables) and carried the seed project's translation files along with it.
        DBProject project = projectService.findBySlug("default-test").orElseThrow();
        DBDraftDialogue dialogue = draftDialogueService.findDialogue(project, "bg1/bg1-menu")
                .orElseThrow();

        assertFalse(draftDialogueService.listNodes(dialogue).isEmpty());
        DBDraftTranslation translation = draftDialogueService
                .findTranslation(dialogue, "nl-NL")
                .orElseThrow();
        assertFalse(translation.getContent().isBlank());
    }

}
