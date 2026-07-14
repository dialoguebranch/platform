-- Persisted status flags on draft dialogues, replacing the on-the-fly script comparison that
-- used to determine whether a draft had unpublished changes. isNew/isChanged are maintained by
-- every operation that mutates a dialogue's effective content and reconciled on publish;
-- isDeleted marks a dialogue as pending (revertible) deletion; renamed_from remembers the
-- published name of a dialogue that has been renamed since it was last published, so references
-- to the old name can be found and optionally rewritten project-wide.
--
-- Existing rows default to is_new/is_changed = TRUE (safer than silently assuming they're already
-- in sync with their published counterpart) and is_deleted = FALSE.

ALTER TABLE draft_dialogues ADD COLUMN is_new     BOOLEAN      NOT NULL DEFAULT TRUE;
ALTER TABLE draft_dialogues ADD COLUMN is_changed BOOLEAN      NOT NULL DEFAULT TRUE;
ALTER TABLE draft_dialogues ADD COLUMN is_deleted BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE draft_dialogues ADD COLUMN renamed_from VARCHAR(255) NULL;
