-- Supports renaming a draft translation language's code, mirroring draft_dialogues'
-- previous_published_name column: if a language with a published counterpart has its code
-- changed, the code its published counterpart is still known by is remembered here until the
-- next publish reconciles it (retires the old published code, publishes the new one).
ALTER TABLE draft_translation_languages
    ADD COLUMN previous_published_code VARCHAR(16) NULL;
