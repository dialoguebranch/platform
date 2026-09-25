package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.model.execute.DialogueState;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
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

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 *
 * <p>Same reasoning applies one level up: reconstructing a {@link DialogueState} without ever
 * actually progressing it (as {@link
 * #reconstructingDialogueStateAfterStartDoesNotThrowLazyInitializationException()} below does)
 * only proves the reconstruction itself doesn't throw — it doesn't prove a client can actually
 * use the result. {@link #progressDialogueSessionAfterStateReconstructionSucceeds()} and {@link
 * #startProgressGetOngoingResumeProgressFlowSucceeds()} close that gap: they drive the
 * reconstructed state through an actual {@code progressDialogueSession()}/{@code
 * continueDialogueSession()} call, which is exactly the sequence that threw {@code
 * ClassCastException: NodeBody cannot be cast to ResolvedNodeBody} in production for every
 * dialogue interaction past the first node (found via manual testing, not by any existing test —
 * see #311).</p>
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
		DialogueBranchUserId userId = new DialogueBranchUserId(
				"https://test/realms/test", "progress-persistence-test-user-" + UUID.randomUUID(), null);
		UserService userService = applicationManager.getOrCreateActiveUserService(userId);

		ExecuteNodeResult startResult = userService.startDialogueSession(projectSlug, "main", null,
				"en", "session-" + UUID.randomUUID(), System.currentTimeMillis());
		String loggedDialogueId = startResult.loggedDialogue().getId();

		// Mirrors what /dialogue/progress and /dialogue/continue do: reload the logged dialogue
		// from storage (a fresh read, in a new transaction) to reconstruct its DialogueState.
		DialogueState state = userService.getDialogueState(loggedDialogueId, 0);
		assertNotNull(state);
	}

	@Test
	void progressDialogueSessionAfterStateReconstructionSucceeds() throws Exception {
		String projectSlug = "progress-session-test-" + UUID.randomUUID();
		DBProject project = projectService.createProject(
				projectSlug, "Progress Session Test", "", "en", "English");
		DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
		draftDialogueService.createNode(dialogue, "Farewell",
				"title: Farewell\nspeaker: Agent\nposition: 100,100", "Goodbye.");
		draftDialogueService.createNode(dialogue, "Start",
				"title: Start\nspeaker: Agent\nposition: 0,0", "Hello.\n\n[[Continue|Farewell]]");
		PublishService.PublishResult publish = publishService.publish(project, null);
		assertTrue(publish.isSuccess(), "publish failed: " + publish.getErrors());

		ApplicationManager applicationManager = application.getApplicationManager();
		DialogueBranchUserId userId = new DialogueBranchUserId(
				"https://test/realms/test", "progress-session-test-user-" + UUID.randomUUID(),
				null);
		UserService userService = applicationManager.getOrCreateActiveUserService(userId);

		ExecuteNodeResult startResult = userService.startDialogueSession(projectSlug, "main", null,
				"en", "session-" + UUID.randomUUID(), System.currentTimeMillis());
		String loggedDialogueId = startResult.loggedDialogue().getId();

		// Reconstruct state exactly like /dialogue/progress does, then actually progress it —
		// the one extra step reconstructingDialogueStateAfterStartDoesNotThrowLazyInitializationException()
		// above stops short of, and the exact call sequence that threw ClassCastException in
		// production before NodeContent gained findReplyById().
		DialogueState state = userService.getDialogueState(loggedDialogueId, 0);
		ExecuteNodeResult progressResult = userService.progressDialogueSession(state, 1);
		assertEquals("Farewell", progressResult.node().getTitle());
	}

	@Test
	void startProgressGetOngoingResumeProgressFlowSucceeds() throws Exception {
		String projectSlug = "progress-flow-test-" + UUID.randomUUID();
		DBProject project = projectService.createProject(
				projectSlug, "Progress Flow Test", "", "en", "English");
		DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
		draftDialogueService.createNode(dialogue, "Farewell",
				"title: Farewell\nspeaker: Agent\nposition: 200,100", "Goodbye.");
		draftDialogueService.createNode(dialogue, "Middle",
				"title: Middle\nspeaker: Agent\nposition: 100,100",
				"In the middle.\n\n[[Continue|Farewell]]");
		draftDialogueService.createNode(dialogue, "Start",
				"title: Start\nspeaker: Agent\nposition: 0,0", "Hello.\n\n[[Continue|Middle]]");
		PublishService.PublishResult publish = publishService.publish(project, null);
		assertTrue(publish.isSuccess(), "publish failed: " + publish.getErrors());

		ApplicationManager applicationManager = application.getApplicationManager();
		DialogueBranchUserId userId = new DialogueBranchUserId(
				"https://test/realms/test", "progress-flow-test-user-" + UUID.randomUUID(), null);
		UserService userService = applicationManager.getOrCreateActiveUserService(userId);

		// start: /dialogue/start
		ExecuteNodeResult startResult = userService.startDialogueSession(projectSlug, "main", null,
				"en", "session-" + UUID.randomUUID(), System.currentTimeMillis());
		String loggedDialogueId = startResult.loggedDialogue().getId();
		assertEquals("Start", startResult.node().getTitle());

		// progress: /dialogue/progress, Start -> Middle
		DialogueState progressState = userService.getDialogueState(loggedDialogueId, 0);
		ExecuteNodeResult progressResult = userService.progressDialogueSession(progressState, 1);
		assertEquals("Middle", progressResult.node().getTitle());

		// get-ongoing: /dialogue/get-ongoing
		ServerLoggedDialogue ongoing = userService.getLoggedDialogueStore()
				.findLatestOngoingDialogueInProject(projectSlug);
		assertNotNull(ongoing);
		assertEquals(loggedDialogueId, ongoing.getId());

		// resume: /dialogue/continue — reconstructs state again (a third ActiveDialogue instance
		// for this same logged dialogue) and re-executes the current node without progressing.
		DialogueState resumeState = userService.getDialogueState(ongoing,
				ongoing.getInteractionList().size() - 1);
		ExecuteNodeResult resumeResult = userService.continueDialogueSession(resumeState,
				ZonedDateTime.now());
		assertEquals("Middle", resumeResult.node().getTitle());

		// progress again: /dialogue/progress, Middle -> Farewell, from a state reconstructed
		// after the resume step — the same crash reproduced via a different lead-up.
		DialogueState finalProgressState = userService.getDialogueState(loggedDialogueId,
				resumeResult.interactionIndex());
		ExecuteNodeResult finalResult = userService.progressDialogueSession(finalProgressState, 1);
		assertEquals("Farewell", finalResult.node().getTitle());
	}

}
