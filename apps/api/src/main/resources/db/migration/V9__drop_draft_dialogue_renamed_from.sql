-- renamed_from (added in V6) tracked a renamed draft dialogue's prior published name, intended to
-- let the next publish know which published entry to drop. That mechanism was never implemented:
-- each publish writes a full, independent snapshot of the currently-live drafts under their
-- current names into a new project version, so there was never anything to "drop" by old name —
-- older versions are kept as-is, untouched, as history. The column was set on rename and cleared
-- on publish, but never read in between.

ALTER TABLE draft_dialogues DROP COLUMN renamed_from;
