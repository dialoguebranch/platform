-- Draft columns for project metadata, backfilled to match current published values so no
-- existing project shows a phantom unpublished change right after deploy.
ALTER TABLE projects
    ADD COLUMN draft_display_name VARCHAR(255),
    ADD COLUMN draft_description  TEXT;

UPDATE projects SET draft_display_name = display_name, draft_description = description;

-- Published language registry gains a soft-delete flag. It is never hard-deleted: historical
-- published_translations rows (belonging to past, immutable project versions) must keep a valid
-- foreign key into this table forever, even after the language is removed from the project.
ALTER TABLE project_translation_languages
    ADD COLUMN is_removed BOOLEAN NOT NULL DEFAULT FALSE;

-- New draft language registry, mirroring draft_dialogues' new/changed/deleted soft-delete idiom.
CREATE TABLE draft_translation_languages (
    id                         CHAR(36)     NOT NULL,
    project_id                 CHAR(36)     NOT NULL,
    translation_language_code VARCHAR(16)  NOT NULL,
    translation_language_name VARCHAR(64)  NOT NULL,
    is_new                     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                 DATETIME     NOT NULL,
    updated_at                 DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_draft_translation_languages UNIQUE (project_id, translation_language_code),
    CONSTRAINT fk_draft_translation_languages_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

-- Seed each project's current (non-removed) published languages as its initial, already-in-sync
-- draft state (is_new = FALSE), so no project shows a phantom unpublished language change.
INSERT INTO draft_translation_languages
    (id, project_id, translation_language_code, translation_language_name, is_new, is_deleted,
     created_at, updated_at)
SELECT UUID(), project_id, translation_language_code, translation_language_name, FALSE, FALSE,
       NOW(), NOW()
FROM project_translation_languages
WHERE is_removed = FALSE;

-- draft_translations.language: plain string -> FK into draft_translation_languages.
ALTER TABLE draft_translations
    ADD COLUMN draft_translation_language_id CHAR(36);

UPDATE draft_translations dt
    JOIN draft_dialogues dd ON dt.draft_dialogue_id = dd.id
    JOIN draft_translation_languages dtl
        ON dtl.project_id = dd.project_id AND dtl.translation_language_code = dt.language
    SET dt.draft_translation_language_id = dtl.id;

ALTER TABLE draft_translations
    DROP INDEX uq_draft_translations,
    DROP COLUMN language,
    MODIFY COLUMN draft_translation_language_id CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_draft_translations
        UNIQUE (draft_dialogue_id, draft_translation_language_id),
    ADD CONSTRAINT fk_draft_translations_language
        FOREIGN KEY (draft_translation_language_id) REFERENCES draft_translation_languages (id);

-- published_translations.language: plain string -> FK into project_translation_languages.
ALTER TABLE published_translations
    ADD COLUMN translation_language_id CHAR(36);

UPDATE published_translations pt
    JOIN published_dialogues pd ON pt.published_dialogue_id = pd.id
    JOIN project_versions pv ON pd.version_id = pv.id
    JOIN project_translation_languages ptl
        ON ptl.project_id = pv.project_id AND ptl.translation_language_code = pt.language
    SET pt.translation_language_id = ptl.id;

ALTER TABLE published_translations
    DROP INDEX uq_published_translations,
    DROP COLUMN language,
    MODIFY COLUMN translation_language_id CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_published_translations
        UNIQUE (published_dialogue_id, translation_language_id),
    ADD CONSTRAINT fk_published_translations_language
        FOREIGN KEY (translation_language_id) REFERENCES project_translation_languages (id);
