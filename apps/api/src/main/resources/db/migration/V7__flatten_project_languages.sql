-- Collapses the LanguageSet model (introduced in V5) back into something simpler: a project has
-- exactly one source language (now columns directly on projects, not a separate entity) and zero
-- or more translation languages (project_translation_languages, replacing the older
-- project_language_mappings, which paired every translation with a redundant copy of the source
-- language on every row). Multiple LanguageSets per project was never actually used anywhere in
-- application code — every project has always had at most one.

ALTER TABLE projects ADD COLUMN source_language_code VARCHAR(16) NULL;
ALTER TABLE projects ADD COLUMN source_language_name VARCHAR(64) NULL;

-- Backfill from the existing default_language_set_id, so any already-seeded project (e.g.
-- default-test) keeps its real source language without requiring a reseed.
UPDATE projects p
JOIN language_sets ls ON ls.id = p.default_language_set_id
SET p.source_language_code = ls.source_language_code,
    p.source_language_name = ls.source_language_name;

-- Defensive fallback for any project that never had a default_language_set_id at all (e.g.
-- created via the old language-less project-creation path), so the NOT NULL constraint below
-- doesn't fail on legacy rows.
UPDATE projects SET source_language_code = 'en', source_language_name = 'English'
WHERE source_language_code IS NULL;

ALTER TABLE projects MODIFY COLUMN source_language_code VARCHAR(16) NOT NULL;
ALTER TABLE projects MODIFY COLUMN source_language_name VARCHAR(64) NOT NULL;

ALTER TABLE projects DROP FOREIGN KEY fk_projects_default_language_set;
ALTER TABLE projects DROP COLUMN default_language_set_id;

DROP TABLE language_set_translations;
DROP TABLE language_sets;

RENAME TABLE project_language_mappings TO project_translation_languages;

-- Add the new unique index before dropping the old one — the old one still backs the table's
-- foreign key on project_id (which the new index also covers, as its leading column), so MariaDB
-- refuses to drop it while it's the only qualifying index.
ALTER TABLE project_translation_languages ADD CONSTRAINT uq_project_translation_languages
    UNIQUE (project_id, translation_language_code);
ALTER TABLE project_translation_languages DROP INDEX uq_project_language_mappings;
ALTER TABLE project_translation_languages DROP COLUMN source_language_name;
ALTER TABLE project_translation_languages DROP COLUMN source_language_code;
