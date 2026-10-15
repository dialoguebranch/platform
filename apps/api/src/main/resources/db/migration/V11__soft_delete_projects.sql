-- Deleting a project keeps it as a deleted "shell" (#315): its dialogue definitions are removed,
-- but the row stays, so its users' variables, variable history and dialogue logs keep referring to
-- it. Its slug is renamed on deletion (<slug>__deleted), freeing the original for a new project.
ALTER TABLE projects ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN deleted_at DATETIME NULL;
