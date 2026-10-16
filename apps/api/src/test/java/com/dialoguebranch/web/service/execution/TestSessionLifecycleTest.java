package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.web.service.DateTimeUtils;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the lifecycle of test sessions: one request at a time per session (#215), a session ends
 * with its dialogue, and idle sessions and unused copies of the variables are evicted (#218).
 */
@SpringBootTest
@ActiveProfiles("test")
class TestSessionLifecycleTest {

	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final long TIMEOUT = TimeUnit.MINUTES.toMillis(30);

	@Autowired
	private ProjectService projectService;

	@Autowired
	private DraftDialogueService draftDialogueService;

	@Autowired
	private TestSessionService testSessionService;

	private record Started(DialogueBranchUserId tester, DBProject project, TestSession session,
			ExecuteNodeResult start) {
	}

	/** Starts a test of a two-node dialogue: Start, then End after its one reply. */
	private Started start() throws Exception {
		DBProject project = projectService.createProject(
				"test-session-life-" + UUID.randomUUID(), "Lifecycle", "", "en", "English");
		DBDraftDialogue main = draftDialogueService.createDialogue(project, "main");
		draftDialogueService.createNode(main, "Start", "title: Start\nspeaker: Agent",
				"Hello.\n[[Next|Second]]");
		draftDialogueService.createNode(main, "Second", "title: Second\nspeaker: Agent", "Bye.");
		DialogueBranchUserId tester = DialogueBranchUserId.withoutUsername(
				"https://test/realms/test", "test-session-life-" + UUID.randomUUID());
		TestSessionService.StartResult result = testSessionService.startSession(tester, tester,
				false, project, main, "en", null, UTC);
		return new Started(tester, project, testSessionService.getSession(result.sessionId(),
				tester), result.executeNodeResult());
	}

	private static int replyId(ExecuteNodeResult result) {
		Reply reply = result.node().getBody().getReplies().get(0);
		return reply.getReplyId();
	}

	@Test
	void aSessionHandlesOneRequestAtATime() throws Exception {
		Started started = start();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread holder = new Thread(() -> {
			synchronized (started.session()) {
				locked.countDown();
				try {
					release.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
		holder.start();
		locked.await();

		CompletableFuture<ExecuteNodeResult> progress = CompletableFuture.supplyAsync(() -> {
			try {
				return testSessionService.progressSession(started.session(),
						replyId(started.start()), null, DateTimeUtils.nowMs(UTC));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		assertThrows(TimeoutException.class, () -> progress.get(300, TimeUnit.MILLISECONDS),
				"a request must wait while another one holds the session");

		release.countDown();
		assertNotNull(progress.get(5, TimeUnit.SECONDS));
		holder.join();
	}

	@Test
	void aSessionEndsWithItsDialogue() throws Exception {
		Started started = start();

		// "Second" has no replies: the dialogue ends there.
		ExecuteNodeResult second = testSessionService.progressSession(started.session(),
				replyId(started.start()), null, DateTimeUtils.nowMs(UTC));

		assertTrue(second.node().getBody().getReplies().isEmpty());
		assertThrows(NotFoundException.class, () -> testSessionService.getSession(
				started.session().getId(), started.tester()));
	}

	@Test
	void idleSessionsAndTheirUnusedCopiesAreEvicted() throws Exception {
		Started started = start();
		long later = System.currentTimeMillis() + TIMEOUT + 1000;

		TestSessionService.Evicted evicted = testSessionService.removeIdle(TIMEOUT, later);

		assertTrue(evicted.sessions() >= 1);
		assertTrue(evicted.copies() >= 1);
		assertThrows(NotFoundException.class, () -> testSessionService.getSession(
				started.session().getId(), started.tester()));
	}

	@Test
	void activeSessionsAndTheCopiesTheyUseAreKept() throws Exception {
		Started started = start();
		var copy = testSessionService.getCopy(started.tester(), started.tester(), false,
				started.project(), UTC);

		testSessionService.removeIdle(TIMEOUT, System.currentTimeMillis());

		assertDoesNotThrow(() -> testSessionService.getSession(started.session().getId(),
				started.tester()));
		assertSame(copy, testSessionService.getCopy(started.tester(), started.tester(), false,
				started.project(), UTC));
	}
}
