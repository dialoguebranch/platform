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
