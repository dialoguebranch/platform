package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DBUserRepository extends JpaRepository<DBUser, UUID> {
    Optional<DBUser> findByUsername(String username);
}
