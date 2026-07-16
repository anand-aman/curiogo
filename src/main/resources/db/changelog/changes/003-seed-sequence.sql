--liquibase formatted sql

--changeset curiogo:003-seed-sequence
-- Collision guard (Phase 1 sequence-offset approach): generated codes come from
-- Base62(id). Starting ids at 1_000_000 makes every generated code at least 4
-- Base62 chars long, so a short custom alias can never later collide with a
-- future generated code. BIGSERIAL creates the implicit sequence urls_id_seq.
ALTER SEQUENCE urls_id_seq RESTART WITH 1000000;

--rollback ALTER SEQUENCE urls_id_seq RESTART WITH 1;
