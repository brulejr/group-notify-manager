-- Delivery plugins are provisioned per group as named instances, which hold all of a
-- plugin's configuration, credentials included (secrets sealed by the application).
-- A notification's delivery now names an instance and may override only the plugin's
-- overridable fields.

CREATE TABLE plugin_instances (
    id         uuid        PRIMARY KEY,
    group_name text        NOT NULL,
    plugin     text        NOT NULL,
    name       text        NOT NULL,
    -- Every field value; secret ones as enc:v1:… (AES-256-GCM).
    config     jsonb       NOT NULL,
    created_by text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by text        NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX plugin_instances_group_name ON plugin_instances (group_name, lower(name));

-- The old per-notification configuration has no instance to belong to: it is dropped,
-- and its notifications keep everything else until an instance is chosen for them.
DELETE FROM deliveries;

ALTER TABLE deliveries
    DROP COLUMN plugin,
    DROP COLUMN label,
    DROP COLUMN target,
    -- No cascade: an instance still in use cannot be deleted.
    ADD COLUMN instance_id uuid  NOT NULL REFERENCES plugin_instances (id),
    -- The notification's own values for overridable fields, where they differ.
    ADD COLUMN overrides   jsonb NOT NULL DEFAULT '{}';

CREATE INDEX deliveries_instance ON deliveries (instance_id);
