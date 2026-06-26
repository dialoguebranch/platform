package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DBPublishedDialogueRepository extends JpaRepository<DBPublishedDialogue, UUID> {
    List<DBPublishedDialogue> findByVersion(DBProjectVersion version);
    Optional<DBPublishedDialogue> findByVersionAndName(DBProjectVersion version, String name);
}
