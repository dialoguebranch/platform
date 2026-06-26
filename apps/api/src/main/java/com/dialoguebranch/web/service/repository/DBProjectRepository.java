package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DBProjectRepository extends JpaRepository<DBProject, UUID> {
    Optional<DBProject> findByName(String name);
}
