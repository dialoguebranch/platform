package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBLanguageSet;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DBLanguageSetRepository extends JpaRepository<DBLanguageSet, UUID> {
    List<DBLanguageSet> findByProject(DBProject project);
}
