-- Scope Dialogue Branch Variables to the project (issue #86). Projects on one Web Service
-- instance are isolated tenants, so a user who touches two projects must not share one variable
-- namespace. The flat UNIQUE(user_id, name) is a leftover from the single-project era.
--
-- No production deployment exists on this schema, so existing rows are dropped rather than
-- backfilled (there is no per-project attribution to recover). #164 D9 / #86.

DELETE FROM variables;

ALTER TABLE variables
    ADD COLUMN project_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_variables_project FOREIGN KEY (project_id) REFERENCES projects (id);

-- Add the replacement uniqueness BEFORE dropping the old one: its leftmost column is
-- user_id, so the foreign key on variables.user_id keeps a covering index at every step.
-- MariaDB refuses to drop user_name while it is the only index that covers that FK
-- (error 1553 "needed in a foreign key constraint"); H2's MariaDB mode does not, which is
-- why this only shows up outside the test suite.
ALTER TABLE variables ADD CONSTRAINT uq_variables_user_project_name
    UNIQUE (user_id, project_id, name);
ALTER TABLE variables DROP INDEX user_name;
