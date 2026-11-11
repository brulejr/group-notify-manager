-- Notifications, their deliveries, and the audit trail. Every table is in the schema
-- notify.database.schema names, which Flyway creates; the SQL names none.

CREATE TABLE notifications (
    id            uuid        PRIMARY KEY,
    -- The OIDC group it belongs to: only its members, and admins, can see it.
    group_name    text        NOT NULL,
    title         text        NOT NULL,
    subject       text        NOT NULL,
    body          text        NOT NULL,
    body_format   text        NOT NULL,
    schedule_kind text        NOT NULL,
    run_at        timestamptz,
    cron          text,
    zone          text        NOT NULL,
    until         timestamptz,
    status        text        NOT NULL,
    -- Null while paused or once completed.
    next_run_at   timestamptz,
    last_run_at   timestamptz,
    created_by    text        NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_by    text        NOT NULL,
    updated_at    timestamptz NOT NULL
);

-- What the scheduler asks every poll.
CREATE INDEX notifications_due ON notifications (next_run_at) WHERE status = 'ACTIVE';
CREATE INDEX notifications_group ON notifications (group_name);

CREATE TABLE deliveries (
    id              uuid    PRIMARY KEY,
    notification_id uuid    NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    position        integer NOT NULL,
    plugin          text    NOT NULL,
    label           text    NOT NULL DEFAULT '',
    enabled         boolean NOT NULL DEFAULT true,
    -- The plugin's field values, secrets included.
    target          jsonb   NOT NULL
);

CREATE INDEX deliveries_notification ON deliveries (notification_id, position);

-- Kept after a notification is deleted, so it names the notification rather than
-- referring to it.
CREATE TABLE audit_events (
    id                 bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at        timestamptz NOT NULL,
    type               text        NOT NULL,
    actor              text        NOT NULL,
    notification_id    uuid,
    notification_title text        NOT NULL,
    group_name         text        NOT NULL,
    delivery_id        uuid,
    plugin             text,
    target             text,
    trigger            text,
    detail             text        NOT NULL DEFAULT '',
    reference          text,
    attempts           integer
);

CREATE INDEX audit_events_group ON audit_events (group_name, id DESC);
CREATE INDEX audit_events_notification ON audit_events (notification_id, id DESC);
CREATE INDEX audit_events_occurred ON audit_events (occurred_at);
