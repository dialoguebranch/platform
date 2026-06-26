package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DBDraftNodeRepository extends JpaRepository<DBDraftNode, UUID> {
    List<DBDraftNode> findByDraftDialogueOrderByCreatedAt(DBDraftDialogue draftDialogue);
    Optional<DBDraftNode> findByDraftDialogueAndTitle(DBDraftDialogue draftDialogue, String title);
}
