package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ProjectService}, in particular {@link ProjectService#deleteProject}, which
 * must permanently remove every draft dialogue row before the project row itself is deleted (a
 * regression once {@link DraftDialogueService#deleteDialogue} became a revertible soft-delete —
 * see {@link DraftDialogueService#hardDeleteDialogue}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DraftDialogueService draftDialogueService;

    @Test
    void deleteProjectRemovesSoftDeletedDraftDialoguesTooWithoutForeignKeyViolation() {
        DBProject project = projectService.createProject("delete-project-test", "Delete Project Test", "", "en", "English");
        DBDraftDialogue kept = draftDialogueService.createDialogue(project, "kept");
        draftDialogueService.createNode(kept, "Start", "title: Start\nspeaker: Narrator", "");
        DBDraftDialogue pendingDeletion = draftDialogueService.createDialogue(project, "pending-deletion");
        draftDialogueService.createNode(pendingDeletion, "Start", "title: Start\nspeaker: Narrator", "");

        // Soft-delete one of the two — its row is deliberately left behind (revertible) rather
        // than removed, unlike the other, still-active dialogue.
        draftDialogueService.deleteDialogue(pendingDeletion);

        // Must not throw (e.g. a foreign key violation from draft dialogue rows still
        // referencing this project when the project row itself is deleted).
        projectService.deleteProject(project);

        assertTrue(draftDialogueService.listDialogues(project).isEmpty());
        assertTrue(projectService.findBySlug("delete-project-test").isEmpty());
    }

}
