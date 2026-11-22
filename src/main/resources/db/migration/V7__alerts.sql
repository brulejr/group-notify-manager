-- Alerts: a group's instances marked to receive its failure alerts, and the instance a
-- delivery went through on each audit event, so alerts can tell one instance's
-- failures from another's (and find their own earlier alerts) after a restart.
ALTER TABLE plugin_instances ADD COLUMN alerts boolean NOT NULL DEFAULT false;

ALTER TABLE audit_events ADD COLUMN instance_id uuid;
CREATE INDEX audit_events_instance ON audit_events (instance_id, id DESC) WHERE instance_id IS NOT NULL;
