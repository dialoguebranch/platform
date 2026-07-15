package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Draft test sessions ({@code /draft/start}) should resolve languages the same way published
 * dialogues do ({@link UserService#findExactLanguageMatch}): an exact match if the dialogue has
 * content in the requested language, otherwise a silent fall back to the source language — not a
 * 400. This covers both "no draft translation exists for this dialogue yet" and "the requested
 * code isn't even one of the project's declared languages" (which used to be rejected by {@code
 * DBProject.validateLanguageCode} before that check was removed for parity with the published
 * path, which never enforced it either).
 */
@SpringBootTest
@ActiveProfiles("test")
class DraftExecutionServiceLanguageFallbackTest {

    @Autowired
    private Application application;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Autowired
    private DraftExecutionService draftExecutionService;

    @Test
    void requestingUntranslatedLanguageFallsBackToSourceLanguage() throws Exception {
        DBProject project = projectService.createProject(
                "draft-lang-fallback-" + UUID.randomUUID(), "Draft Lang Fallback", "", "en",
                "English");
        DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
        draftDialogueService.createNode(dialogue, "Start",
                "title: Start\nspeaker: Agent\nposition: 0,0", "Hello.");

        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "draft-lang-fallback-user-" + UUID.randomUUID();
        UserService userService = applicationManager.getOrCreateActiveUserService(userId);

        DraftExecutionService.StartResult result =
                draftExecutionService.startSession(userService, project, dialogue, "nl-NL", null);

        assertNotNull(result);
        DraftTestSession session = draftExecutionService.getSession(result.sessionId());
        assertEquals("en", session.getActiveDialogue().getDialogueFileDescription().getLanguage(),
                "requesting a language this dialogue has no draft translation for should fall " +
                        "back to the source language rather than failing the request");
    }

}
