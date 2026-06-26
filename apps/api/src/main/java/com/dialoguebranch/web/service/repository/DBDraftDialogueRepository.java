package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DBDraftDialogueRepository extends JpaRepository<DBDraftDialogue, UUID> {
    List<DBDraftDialogue> findByProject(DBProject project);
    Optional<DBDraftDialogue> findByProjectAndName(DBProject project, String name);
}
