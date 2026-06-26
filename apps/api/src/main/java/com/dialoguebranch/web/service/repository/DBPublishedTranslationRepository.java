package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DBPublishedTranslationRepository extends JpaRepository<DBPublishedTranslation, UUID> {
    List<DBPublishedTranslation> findByPublishedDialogue(DBPublishedDialogue publishedDialogue);
    Optional<DBPublishedTranslation> findByPublishedDialogueAndLanguage(DBPublishedDialogue publishedDialogue, String language);
}
