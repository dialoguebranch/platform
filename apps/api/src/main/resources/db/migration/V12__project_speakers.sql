-- A project's speakers (#393, #394): the draft list per project, edited in Authoring Mode, and a
-- snapshot per published version. A speaker has a name, a gender (male, female or undefined) and
-- whether it is the default for new nodes; position keeps the order they were defined in.
CREATE TABLE draft_speakers (
    id          CHAR(36)     NOT NULL,
    project_id  CHAR(36)     NOT NULL,
    name        VARCHAR(255) NOT NULL,
    gender      VARCHAR(16)  NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    position    INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_draft_speakers UNIQUE (project_id, name),
    CONSTRAINT fk_draft_speakers_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE TABLE published_speakers (
    id          CHAR(36)     NOT NULL,
    version_id  CHAR(36)     NOT NULL,
    name        VARCHAR(255) NOT NULL,
    gender      VARCHAR(16)  NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    position    INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_published_speakers UNIQUE (version_id, name),
    CONSTRAINT fk_published_speakers_version FOREIGN KEY (version_id) REFERENCES project_versions (id)
);
