package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.models.DBUser;
import com.dialoguebranch.web.service.models.DBVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DBVariableRepository extends JpaRepository<DBVariable, UUID> {
    List<DBVariable> findByUser(DBUser user);
    Optional<DBVariable> findByUserAndName(DBUser user, String name);
}
