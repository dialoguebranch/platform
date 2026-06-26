-- --------------------
-- Projects
-- --------------------
CREATE TABLE projects (
    id                 CHAR(36)     NOT NULL,
    name               VARCHAR(255) NOT NULL,
    display_name       VARCHAR(255),
    description        TEXT,
    latest_version_id  CHAR(36)     NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_projects_name UNIQUE (name)
);

CREATE TABLE project_language_mappings (
    id                       CHAR(36)     NOT NULL,
    project_id               CHAR(36)     NOT NULL,
    source_language_name     VARCHAR(64)  NOT NULL,
    source_language_code     VARCHAR(16)  NOT NULL,
    translation_language_name VARCHAR(64) NOT NULL,
    translation_language_code VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_project_language_mappings UNIQUE (project_id, source_language_code, translation_language_code),
    CONSTRAINT fk_language_mappings_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

-- --------------------
-- Draft
-- --------------------
CREATE TABLE draft_dialogues (
    id          CHAR(36)     NOT NULL,
    project_id  CHAR(36)     NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_draft_dialogues UNIQUE (project_id, name),
    CONSTRAINT fk_draft_dialogues_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE TABLE draft_nodes (
    id                 CHAR(36)     NOT NULL,
    draft_dialogue_id  CHAR(36)     NOT NULL,
    title              VARCHAR(255) NOT NULL,
    header             TEXT,
    body               TEXT,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_draft_nodes UNIQUE (draft_dialogue_id, title),
    CONSTRAINT fk_draft_nodes_dialogue FOREIGN KEY (draft_dialogue_id) REFERENCES draft_dialogues (id)
);

CREATE TABLE draft_translations (
    id                 CHAR(36)     NOT NULL,
    draft_dialogue_id  CHAR(36)     NOT NULL,
    language           VARCHAR(16)  NOT NULL,
    content            TEXT,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_draft_translations UNIQUE (draft_dialogue_id, language),
    CONSTRAINT fk_draft_translations_dialogue FOREIGN KEY (draft_dialogue_id) REFERENCES draft_dialogues (id)
);

-- --------------------
-- Published
-- --------------------
CREATE TABLE project_versions (
    id              CHAR(36)     NOT NULL,
    project_id      CHAR(36)     NOT NULL,
    version_number  INT          NOT NULL,
    published_at    DATETIME     NOT NULL,
    published_by    CHAR(36)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_project_versions UNIQUE (project_id, version_number),
    CONSTRAINT fk_project_versions_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_versions_user FOREIGN KEY (published_by) REFERENCES users (id)
);

CREATE TABLE published_dialogues (
    id          CHAR(36)     NOT NULL,
    version_id  CHAR(36)     NOT NULL,
    name        VARCHAR(255) NOT NULL,
    content     TEXT,
    PRIMARY KEY (id),
    CONSTRAINT uq_published_dialogues UNIQUE (version_id, name),
    CONSTRAINT fk_published_dialogues_version FOREIGN KEY (version_id) REFERENCES project_versions (id)
);

CREATE TABLE published_translations (
    id                      CHAR(36)     NOT NULL,
    published_dialogue_id   CHAR(36)     NOT NULL,
    language                VARCHAR(16)  NOT NULL,
    content                 TEXT,
    PRIMARY KEY (id),
    CONSTRAINT uq_published_translations UNIQUE (published_dialogue_id, language),
    CONSTRAINT fk_published_translations_dialogue FOREIGN KEY (published_dialogue_id) REFERENCES published_dialogues (id)
);

-- --------------------
-- Forward reference: projects.latest_version_id → project_versions
-- --------------------
ALTER TABLE projects
    ADD CONSTRAINT fk_projects_latest_version FOREIGN KEY (latest_version_id) REFERENCES project_versions (id);
