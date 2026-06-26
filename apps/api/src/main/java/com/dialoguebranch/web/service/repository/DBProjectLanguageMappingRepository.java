package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectLanguageMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DBProjectLanguageMappingRepository extends JpaRepository<DBProjectLanguageMapping, UUID> {
    List<DBProjectLanguageMapping> findByProject(DBProject project);
}
