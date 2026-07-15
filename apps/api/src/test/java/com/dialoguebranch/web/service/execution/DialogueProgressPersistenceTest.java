package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.model.execute.DialogueState;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.project.PublishService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deliberately NOT {@code @Transactional} — each service/repository call below runs in its own
 * transaction, exactly like separate HTTP requests do in production with {@code
 * spring.jpa.open-in-view: false}. This is what catches lazy-loading bugs that a single
 * transaction wrapping the whole test would mask: {@link
 * com.dialoguebranch.web.service.storage.LoggedDialogueStore} previously read {@code
 * DBLoggedDialogue.getUser().getUsername()} when reconstructing a {@link
 * com.dialoguebranch.web.service.storage.ServerLoggedDialogue}, which threw {@link
 * org.hibernate.LazyInitializationException} once the entity's fetch transaction had already
 * closed — reproducing the 500 seen from {@code /dialogue/progress} and {@code
 * /dialogue/continue} in production.
 */
@SpringBootTest
@ActiveProfiles("test")
class DialogueProgressPersistenceTest {

    @Autowired
    private Application application;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Autowired
    private PublishService publishService;

    @Test
    void reconstructingDialogueStateAfterStartDoesNotThrowLazyInitializationException()
            throws Exception {
        String projectSlug = "progress-persistence-test-" + UUID.randomUUID();
        DBProject project = projectService.createProject(
                projectSlug, "Progress Persistence Test", "", "en", "English");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Farewell",
                "title: Farewell\nspeaker: Agent\nposition: 100,100", "Goodbye.");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nspeaker: Agent\nposition: 0,0", "Hello.\n\n[[Continue|Farewell]]");
        PublishService.PublishResult publish = publishService.publish(project, null);
        assertTrue(publish.isSuccess(), "publish failed: " + publish.getErrors());

        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "progress-persistence-test-user-" + UUID.randomUUID();
        UserService userService = applicationManager.getOrCreateActiveUserService(userId);

        ExecuteNodeResult startResult = userService.startDialogueSession(projectSlug, "main", null,
                "en", "session-" + UUID.randomUUID(), System.currentTimeMillis());
        String loggedDialogueId = startResult.loggedDialogue().getId();

        // Mirrors what /dialogue/progress and /dialogue/continue do: reload the logged dialogue
        // from storage (a fresh read, in a new transaction) to reconstruct its DialogueState.
        DialogueState state = userService.getDialogueState(loggedDialogueId, 0);
        assertNotNull(state);
    }

}
