package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBLanguageSet;
import com.dialoguebranch.web.service.storage.model.DBLanguageSetTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DBLanguageSetTranslationRepository extends JpaRepository<DBLanguageSetTranslation, UUID> {
    List<DBLanguageSetTranslation> findByLanguageSet(DBLanguageSet languageSet);
}
