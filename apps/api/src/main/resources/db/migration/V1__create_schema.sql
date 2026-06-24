CREATE TABLE users (
    id   CHAR(36)     NOT NULL,
    username VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT username UNIQUE (username)
);

CREATE TABLE variables (
    id      CHAR(36)     NOT NULL,
    user_id CHAR(36),
    name    VARCHAR(255),
    value   VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT user_name UNIQUE (user_id, name),
    CONSTRAINT fk_variables_user FOREIGN KEY (user_id) REFERENCES users (id)
);
