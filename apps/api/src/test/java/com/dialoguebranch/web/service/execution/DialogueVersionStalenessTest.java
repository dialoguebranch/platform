package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.project.PublishService;
import com.dialoguebranch.web.service.storage.ServerLoggedDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the data a logged dialogue is pinned to at start time, and how it compares against a
 * project's current published version after a republish — the mechanism {@link
 * com.dialoguebranch.web.service.controller.DialogueController#getOngoingDialogue} and {@link
 * com.dialoguebranch.web.service.controller.DialogueController#continueDialogue} rely on to
 * refuse to resume a dialogue whose project has since been republished. Also exercises the
 * database-backed {@link com.dialoguebranch.web.service.storage.LoggedDialogueStore} end to end
 * (start a dialogue, read it back as "ongoing" via a real repository query).
 *
 * <p>Uses the same H2 in-memory {@code test} profile as {@link
 * com.dialoguebranch.web.service.ApiIntegrationTest}; each test runs in its own rolled-back
 * transaction.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DialogueVersionStalenessTest {

    @Autowired
    private Application application;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Autowired
    private PublishService publishService;

    @Test
    void ongoingDialogueBecomesStaleAfterRepublish() throws Exception {
        String projectSlug = "staleness-test-" + UUID.randomUUID();
        DBProject project = projectService.createProject(
                projectSlug, "Staleness Test", "", "en", "English");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Farewell",
                "title: Farewell\nspeaker: Agent\nposition: 100,100", "Goodbye.");
        // Start needs a reply to stay ongoing — a node with no replies is treated as terminal and
        // immediately marked completed (see DialogueExecutor.updateLoggedDialogue).
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nspeaker: Agent\nposition: 0,0", "Hello.\n\n[[Continue|Farewell]]");
        PublishService.PublishResult firstPublish = publishService.publish(project, null);
        assertTrue(firstPublish.isSuccess(), "publish failed: " + firstPublish.getErrors());

        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "staleness-test-user-" + UUID.randomUUID();
        UserService userService = applicationManager.getOrCreateActiveUserService(userId);

        userService.startDialogueSession(projectSlug, "main", null, "en",
                "session-" + UUID.randomUUID(), System.currentTimeMillis());

        ServerLoggedDialogue ongoing =
                userService.getLoggedDialogueStore().findLatestOngoingDialogueInProject(projectSlug);
        assertNotNull(ongoing, "the started dialogue should be found as ongoing");

        int versionAtStart = ongoing.getPublishedVersionNumber();
        assertEquals(1, versionAtStart,
                "the dialogue should be pinned to the project's first published version");

        Integer currentVersion = applicationManager.getProjectVersion(projectSlug);
        assertEquals(currentVersion, versionAtStart,
                "immediately after starting, the pinned version should match the current one");

        // Republish the project (add a second node so there's a real content change), simulating
        // an editor publishing new content while the user's dialogue is still in progress.
        draftDialogueService.createNode(dialogue, "Second",
                "title: Second\nspeaker: Agent\nposition: 100,0", "More content.");
        PublishService.PublishResult secondPublish = publishService.publish(project, null);
        assertTrue(secondPublish.isSuccess(), "publish failed: " + secondPublish.getErrors());

        Integer currentVersionAfterRepublish = applicationManager.getProjectVersion(projectSlug);
        assertEquals(2, currentVersionAfterRepublish,
                "the project should now be on its second published version");
        assertNotEquals(currentVersionAfterRepublish, versionAtStart,
                "the current version should have moved past the version the dialogue started on");

        // The logged dialogue itself is untouched by the republish — its pinned version stays
        // fixed at whatever the project was when it started.
        ServerLoggedDialogue stillOngoing =
                userService.getLoggedDialogueStore().findLatestOngoingDialogueInProject(projectSlug);
        assertNotNull(stillOngoing);
        assertEquals(versionAtStart, stillOngoing.getPublishedVersionNumber());

        // This is exactly the comparison DialogueController.doGetOngoingDialogue/doContinueDialogue
        // perform to decide whether to suppress a stale ongoing dialogue.
        assertNotEquals(currentVersionAfterRepublish, stillOngoing.getPublishedVersionNumber(),
                "after a republish, the ongoing dialogue's pinned version must no longer match " +
                        "the project's current version, so it gets treated as stale");
    }

}
