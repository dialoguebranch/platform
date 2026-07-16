package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import nl.rrd.utils.datetime.DateTimeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A draft test session's reply can point to a node in a sibling dialogue (a
 * {@code [[Reply Text.|otherDialogue.NodeId]]} link) exactly as a published dialogue's can — this
 * used to be rejected with a 400 ("draft test mode" did not support cross-dialogue links), even
 * though {@link DraftExecutionService#startSession} already parses the whole project (every draft
 * dialogue, not just the one under test) so that such references resolve.
 */
@SpringBootTest
@ActiveProfiles("test")
class DraftExecutionServiceCrossDialogueLinkTest {

    @Autowired
    private Application application;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Autowired
    private DraftExecutionService draftExecutionService;

    @Test
    void replyLinkingToSiblingDialogueSwitchesTheSession() throws Exception {
        DBProject project = projectService.createProject(
                "draft-cross-dialogue-" + UUID.randomUUID(), "Draft Cross Dialogue", "", "en",
                "English");

        DBDraftDialogue main = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(main, "Start",
                "title: Start\nspeaker: Agent\nposition: 0,0",
                "Hi there.\n\n[[Go to the other dialogue.|other.Start]]");

        DBDraftDialogue other = draftDialogueService.createDialogue(project, "other");
        draftDialogueService.createNode(other, "Start",
                "title: Start\nspeaker: Agent\nposition: 0,0",
                "You made it to the other dialogue.");

        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "draft-cross-dialogue-user-" + UUID.randomUUID();
        UserService userService = applicationManager.getOrCreateActiveUserService(userId);

        DraftExecutionService.StartResult startResult =
                draftExecutionService.startSession(userService, project, main, "en", null);
        assertNotNull(startResult);

        DraftTestSession session = draftExecutionService.getSession(startResult.sessionId());
        assertEquals("main", session.getDialogueDefinition().getDialogueName(),
                "the session should start on the requested dialogue");

        Reply reply = startResult.executeNodeResult().node().getBody().getReplies().get(0);

        ZonedDateTime eventTime =
                DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone());
        ExecuteNodeResult nextResult = draftExecutionService.progressSession(
                session, reply.getReplyId(), null, eventTime);

        assertNotNull(nextResult, "following the cross-dialogue link should not end the session");
        assertEquals("other", nextResult.dialogue().getDialogueName(),
                "the reply should switch execution to the target dialogue");
        assertEquals("Start", nextResult.node().getTitle(),
                "the reply should land on the target dialogue's linked node");
        assertEquals("other", session.getDialogueDefinition().getDialogueName(),
                "the session itself should now reflect the target dialogue");
    }

}
