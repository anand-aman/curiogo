--liquibase formatted sql

--changeset curiogo:001-create-urls
CREATE TABLE urls (
                      id              BIGSERIAL       PRIMARY KEY,
                      short_code      VARCHAR(64)     NOT NULL UNIQUE,
                      original_url    TEXT            NOT NULL,
                      is_custom       BOOLEAN         NOT NULL DEFAULT FALSE,
                      expires_at      TIMESTAMPTZ     NULL,       -- NULL = never expires
                      click_count     BIGINT          NOT NULL DEFAULT 0,
                      created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

--rollback DROP TABLE urls;
