--liquibase formatted sql

--changeset curiogo:002-index-expires-at
-- Partial index: only rows that actually expire are indexed, keeping the
-- expiry-sweep query fast without bloating the index with never-expiring links.
CREATE INDEX idx_urls_expires_at ON urls (expires_at) WHERE expires_at IS NOT NULL;

--rollback DROP INDEX idx_urls_expires_at;
