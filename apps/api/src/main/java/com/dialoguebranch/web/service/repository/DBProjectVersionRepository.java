package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DBProjectVersionRepository extends JpaRepository<DBProjectVersion, UUID> {
    List<DBProjectVersion> findByProjectOrderByVersionNumberDesc(DBProject project);
}
