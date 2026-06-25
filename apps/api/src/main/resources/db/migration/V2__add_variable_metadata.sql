ALTER TABLE variables
    ADD COLUMN updated_time      BIGINT       NULL,
    ADD COLUMN updated_time_zone VARCHAR(64)  NULL,
    ADD COLUMN updated_source    VARCHAR(32)  NULL;
