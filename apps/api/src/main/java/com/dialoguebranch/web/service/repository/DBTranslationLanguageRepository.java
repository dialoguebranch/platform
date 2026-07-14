package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBTranslationLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DBTranslationLanguageRepository extends JpaRepository<DBTranslationLanguage, UUID> {
    List<DBTranslationLanguage> findByProject(DBProject project);
}
