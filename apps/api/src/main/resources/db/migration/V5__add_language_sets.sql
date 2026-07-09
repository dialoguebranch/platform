-- Introduces the LanguageSet model: a project has one or more LanguageSets, each with exactly
-- one source language and zero or more translation languages, and exactly one LanguageSet
-- designated as the project's default. This coexists with the older project_language_mappings
-- table for now — /project/add-language-mapping and /project/remove-language-mapping still read
-- and write that table and will be migrated to the new model separately.

CREATE TABLE language_sets (
    id                    CHAR(36)     NOT NULL,
    project_id            CHAR(36)     NOT NULL,
    source_language_code  VARCHAR(16)  NOT NULL,
    source_language_name  VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_language_sets UNIQUE (project_id, source_language_code),
    CONSTRAINT fk_language_sets_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE TABLE language_set_translations (
    id                         CHAR(36)     NOT NULL,
    language_set_id            CHAR(36)     NOT NULL,
    translation_language_code  VARCHAR(16)  NOT NULL,
    translation_language_name  VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_language_set_translations UNIQUE (language_set_id, translation_language_code),
    CONSTRAINT fk_language_set_translations_set FOREIGN KEY (language_set_id) REFERENCES language_sets (id)
);

ALTER TABLE projects ADD COLUMN default_language_set_id CHAR(36) NULL;
ALTER TABLE projects ADD CONSTRAINT fk_projects_default_language_set
    FOREIGN KEY (default_language_set_id) REFERENCES language_sets (id);

-- Backfill: for every project that already has rows in project_language_mappings, group them by
-- source language into language_sets, carry over their translations, and mark one set default.
INSERT INTO language_sets (id, project_id, source_language_code, source_language_name)
SELECT UUID(), project_id, source_language_code, MIN(source_language_name)
FROM project_language_mappings
GROUP BY project_id, source_language_code;

INSERT INTO language_set_translations (id, language_set_id, translation_language_code, translation_language_name)
SELECT UUID(), ls.id, m.translation_language_code, m.translation_language_name
FROM project_language_mappings m
JOIN language_sets ls
    ON ls.project_id = m.project_id AND ls.source_language_code = m.source_language_code;

UPDATE projects p
SET default_language_set_id = (
    SELECT ls.id FROM language_sets ls
    WHERE ls.project_id = p.id
    ORDER BY ls.source_language_code
    LIMIT 1
)
WHERE default_language_set_id IS NULL;
