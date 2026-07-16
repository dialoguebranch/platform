-- --------------------
-- Users & variables
-- --------------------
CREATE TABLE users (
    id       CHAR(36)     NOT NULL,
    username VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT username UNIQUE (username)
);

CREATE TABLE variables (
    id                CHAR(36)     NOT NULL,
    user_id           CHAR(36),
    name              VARCHAR(255),
    value             VARCHAR(255),
    updated_time      BIGINT       NULL,
    updated_time_zone VARCHAR(64)  NULL,
    updated_source    VARCHAR(32)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT user_name UNIQUE (user_id, name),
    CONSTRAINT fk_variables_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- --------------------
-- Projects
-- --------------------
CREATE TABLE projects (
    id                    CHAR(36)     NOT NULL,
    slug                  VARCHAR(255) NOT NULL,
    display_name          VARCHAR(255),
    description           TEXT,
    source_language_code  VARCHAR(16)  NOT NULL,
    source_language_name  VARCHAR(64)  NOT NULL,
    latest_version_id     CHAR(36)     NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_projects_slug UNIQUE (slug)
);

CREATE TABLE project_translation_languages (
    id                         CHAR(36)     NOT NULL,
    project_id                 CHAR(36)     NOT NULL,
    translation_language_code VARCHAR(16)  NOT NULL,
    translation_language_name VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_project_translation_languages UNIQUE (project_id, translation_language_code),
    CONSTRAINT fk_project_translation_languages_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

-- --------------------
-- Draft
-- --------------------
CREATE TABLE draft_dialogues (
    id                       CHAR(36)     NOT NULL,
    project_id               CHAR(36)     NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    is_new                   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_changed               BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    previous_published_name  VARCHAR(255) NULL,
    created_at               DATETIME     NOT NULL,
    updated_at               DATETIME     NOT NULL,
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

-- --------------------
-- Dialogue logs
-- --------------------
CREATE TABLE logged_dialogues (
    id                            CHAR(32)     NOT NULL,
    user_id                       CHAR(36)     NOT NULL,
    session_id                    VARCHAR(255) NOT NULL,
    session_start_time            BIGINT       NOT NULL,
    local_time                    VARCHAR(32)  NOT NULL,
    utc_time_ms                   BIGINT       NOT NULL,
    timezone                      VARCHAR(64)  NOT NULL,
    project_slug                  VARCHAR(255) NOT NULL,
    dialogue_name                 VARCHAR(255) NOT NULL,
    language                      VARCHAR(32)  NOT NULL,
    published_version_number      INT          NOT NULL,
    completed                     BOOLEAN      NOT NULL DEFAULT FALSE,
    cancelled                     BOOLEAN      NOT NULL DEFAULT FALSE,
    latest_interaction_timestamp  BIGINT       NOT NULL,
    interactions                  TEXT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_logged_dialogues_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_logged_dialogues_ongoing
    ON logged_dialogues (user_id, project_slug, dialogue_name, completed, cancelled,
                          latest_interaction_timestamp);
