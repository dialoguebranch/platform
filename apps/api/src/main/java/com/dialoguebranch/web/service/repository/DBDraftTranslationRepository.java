package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DBDraftTranslationRepository extends JpaRepository<DBDraftTranslation, UUID> {
    List<DBDraftTranslation> findByDraftDialogue(DBDraftDialogue draftDialogue);
    Optional<DBDraftTranslation> findByDraftDialogueAndLanguage(DBDraftDialogue draftDialogue, String language);
}
