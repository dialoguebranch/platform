-- Per-version metadata snapshot, replacing the single mutable projects.display_name/description
-- pair. Historical versions never recorded this under the old model, so every existing version for
-- a project is backfilled with that project's CURRENT display name/description as a best-effort
-- approximation — same spirit as V2's backfills (this is dev-only data; no production deployment
-- exists yet).
ALTER TABLE project_versions
    ADD COLUMN display_name VARCHAR(255),
    ADD COLUMN description  TEXT;

UPDATE project_versions pv
    JOIN projects p ON p.id = pv.project_id
    SET pv.display_name = p.display_name, pv.description = p.description;

-- Per-version published translation-language registry, replacing the single current-state
-- project_translation_languages table (which had to carry an is_removed soft-delete flag shared by
-- every published version, plus a previous_published_code rename-tracking hack on the draft side).
CREATE TABLE published_translation_languages (
    id                         CHAR(36)     NOT NULL,
    version_id                 CHAR(36)     NOT NULL,
    translation_language_code  VARCHAR(16)  NOT NULL,
    translation_language_name  VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_published_translation_languages UNIQUE (version_id, translation_language_code),
    CONSTRAINT fk_published_translation_languages_version
        FOREIGN KEY (version_id) REFERENCES project_versions (id)
);

-- Best-effort backfill: one row per (version, code) pair actually referenced by existing
-- published_translations, using the code/name currently in project_translation_languages.
INSERT INTO published_translation_languages
    (id, version_id, translation_language_code, translation_language_name)
SELECT DISTINCT UUID(), pd.version_id, ptl.translation_language_code, ptl.translation_language_name
FROM published_translations pt
    JOIN published_dialogues pd ON pt.published_dialogue_id = pd.id
    JOIN project_translation_languages ptl ON ptl.id = pt.translation_language_id;

-- Repoint published_translations at the new per-version table.
ALTER TABLE published_translations
    ADD COLUMN published_translation_language_id CHAR(36);

UPDATE published_translations pt
    JOIN published_dialogues pd ON pt.published_dialogue_id = pd.id
    JOIN project_translation_languages ptl ON ptl.id = pt.translation_language_id
    JOIN published_translation_languages new_ptl
        ON new_ptl.version_id = pd.version_id
        AND new_ptl.translation_language_code = ptl.translation_language_code
    SET pt.published_translation_language_id = new_ptl.id;

-- One combined ALTER TABLE (not split): the old uq_published_translations index also backs
-- fk_published_translations_dialogue (published_dialogue_id is its leftmost column), so dropping
-- it in isolation fails with errno 1553 "needed in a foreign key constraint" — the replacement
-- unique index must be added in the SAME statement so InnoDB's rebuild sees it as covered
-- throughout. The new constraints are also given fresh names (not reused from the dropped ones) —
-- reusing an identical name for both the dropped and newly-added constraint in one multi-clause
-- ALTER trips a separate MariaDB errno 121 "Duplicate key on write or update" during that rebuild.
ALTER TABLE published_translations
    DROP FOREIGN KEY fk_published_translations_language,
    DROP INDEX uq_published_translations,
    DROP COLUMN translation_language_id,
    MODIFY COLUMN published_translation_language_id CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_published_translations_language
        UNIQUE (published_dialogue_id, published_translation_language_id),
    ADD CONSTRAINT fk_published_translations_published_language
        FOREIGN KEY (published_translation_language_id) REFERENCES published_translation_languages (id);

-- Drop the now-unused single current-state published registry, its draft-side rename-tracking
-- hack (no longer needed — a rename now just changes what the next publish snapshots), and the
-- published display name/description columns on projects itself (draft_display_name/
-- draft_description stay, untouched).
ALTER TABLE draft_translation_languages
    DROP COLUMN previous_published_code;

ALTER TABLE projects
    DROP COLUMN display_name,
    DROP COLUMN description;

DROP TABLE project_translation_languages;
