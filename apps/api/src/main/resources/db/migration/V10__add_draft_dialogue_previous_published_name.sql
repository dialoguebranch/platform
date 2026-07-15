-- Re-introduces per-dialogue rename tracking (previously renamed_from, dropped in V9 for being
-- unread) under a clearer name: previous_published_name remembers the name a draft dialogue was
-- last published under, if it has been renamed since. Unlike before, this is now actually read —
-- the dialogue list merges a draft against this name instead of its current name, so a rename no
-- longer produces two entries (a stale published one under the old name, and a new draft one under
-- the new name). Cleared on publish, since the new version now reflects the current name exactly.

ALTER TABLE draft_dialogues ADD COLUMN previous_published_name VARCHAR(255) NULL;
