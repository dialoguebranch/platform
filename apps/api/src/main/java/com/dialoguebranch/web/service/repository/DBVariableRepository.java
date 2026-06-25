package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBUser;
import com.dialoguebranch.web.service.storage.model.DBVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DBVariableRepository extends JpaRepository<DBVariable, UUID> {
    List<DBVariable> findByUser(DBUser user);
    Optional<DBVariable> findByUserAndName(DBUser user, String name);
}
