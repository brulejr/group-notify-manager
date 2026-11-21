-- Who uses the system, for the admins' Users page: as Authelia last described each
-- person, and when they were last seen. Nothing here grants anything: a person's groups
-- come from Authelia on every request.
CREATE TABLE users (
    username         text        PRIMARY KEY,
    display_name     text        NOT NULL,
    email            text,
    -- Every group Authelia last listed, as a JSON array.
    groups           jsonb       NOT NULL DEFAULT '[]',
    admin            boolean     NOT NULL DEFAULT false,
    first_seen       timestamptz NOT NULL,
    last_seen        timestamptz NOT NULL,
    -- Requests no further apart than notify.users.visit-gap make one visit.
    visits           integer     NOT NULL DEFAULT 1,
    visit_started_at timestamptz NOT NULL,
    last_address     text,
    last_agent       text
);

CREATE INDEX users_last_seen ON users (last_seen DESC);
