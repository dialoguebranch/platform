-- Key Web Service user identity on (issuer, subject) instead of the JWT preferred_username
-- (issue #128). preferred_username is not realm-qualified (one Web Service instance trusts
-- tokens from multiple Keycloak realms) and is mutable / reusable, so two people could collapse
-- onto one users row or have their data orphaned on a rename.
--
-- The OIDC `sub` was never captured, so there is nothing to backfill. Per the #164 design
-- decision the user-scoped data is wiped outright rather than reconciled — this is dev-only
-- data, no production deployment exists on this schema. Published project content is kept; only
-- the stale publisher link is cleared.

-- Wipe user-scoped data in FK-safe order.
UPDATE project_versions SET published_by = NULL;
DELETE FROM variables;
DELETE FROM logged_dialogues;
DELETE FROM users;

-- users is now empty, so the NOT NULL columns can be added directly.
ALTER TABLE users
    ADD COLUMN issuer  VARCHAR(512) NOT NULL,
    ADD COLUMN subject VARCHAR(255) NOT NULL;

-- Replace the username-based uniqueness with (issuer, subject). username stays as a mutable,
-- non-key display/log attribute (refreshed from the token's preferred_username on login).
ALTER TABLE users DROP INDEX username;
ALTER TABLE users MODIFY COLUMN username VARCHAR(255) NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_issuer_subject UNIQUE (issuer, subject);
