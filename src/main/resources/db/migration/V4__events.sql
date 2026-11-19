-- Events: a dated or recurring happening of a group, with properties its messages can
-- use, and reminders sent relative to each occurrence.

CREATE TABLE events (
    id                uuid        PRIMARY KEY,
    group_name        text        NOT NULL,
    title             text        NOT NULL,
    subject           text        NOT NULL,
    body              text        NOT NULL,
    body_format       text        NOT NULL,
    schedule_kind     text        NOT NULL,
    run_at            timestamptz,
    cron              text,
    pattern           text,
    zone              text        NOT NULL,
    until             timestamptz,
    -- [{"name": "location", "value": "Town hall"}, …], in the author's order.
    properties        jsonb       NOT NULL DEFAULT '[]',
    -- [{"id", "amount", "unit", "timing", "subject", "body", "enabled"}, …]
    reminders         jsonb       NOT NULL DEFAULT '[]',
    -- {"<occurrence instant>": {"text": "…", "format": "MARKDOWN" | "HTML"}, …}
    notes             jsonb       NOT NULL DEFAULT '{}',
    status            text        NOT NULL,
    -- The reminder due next: when, for which occurrence, which reminder. Null when none.
    next_run_at       timestamptz,
    next_occurrence   timestamptz,
    next_reminder     uuid,
    last_sent_at      timestamptz,
    created_by        text        NOT NULL,
    created_at        timestamptz NOT NULL,
    updated_by        text        NOT NULL,
    updated_at        timestamptz NOT NULL
);

CREATE INDEX events_due ON events (next_run_at) WHERE status = 'ACTIVE';
CREATE INDEX events_group ON events (group_name);

CREATE TABLE event_deliveries (
    id          uuid    PRIMARY KEY,
    event_id    uuid    NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    position    integer NOT NULL,
    -- No cascade: an instance an event still uses cannot be deleted.
    instance_id uuid    NOT NULL REFERENCES plugin_instances (id),
    enabled     boolean NOT NULL DEFAULT true,
    overrides   jsonb   NOT NULL DEFAULT '{}'
);

CREATE INDEX event_deliveries_event ON event_deliveries (event_id, position);
CREATE INDEX event_deliveries_instance ON event_deliveries (instance_id);

-- An event's own history, beside the notifications'.
ALTER TABLE audit_events ADD COLUMN event_id uuid;
CREATE INDEX audit_events_event ON audit_events (event_id, id DESC);
