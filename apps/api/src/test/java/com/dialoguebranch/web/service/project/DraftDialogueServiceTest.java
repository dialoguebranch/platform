package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftNode;
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
        // but no draft dialogue row (e.g. its draft was deleted after publishing).
        draftDialogueService.deleteDialogue(dialogue);
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

}
