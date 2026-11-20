-- Each reminder of an event now chooses its own deliveries, rather than the event
-- choosing them once for all its reminders. An event's existing deliveries are copied
-- onto every one of its reminders, so it goes on sending exactly where it did.

ALTER TABLE event_deliveries ADD COLUMN reminder_id uuid;

INSERT INTO event_deliveries (id, event_id, position, instance_id, enabled, overrides, reminder_id)
SELECT gen_random_uuid(), d.event_id, d.position, d.instance_id, d.enabled, d.overrides, (r ->> 'id')::uuid
FROM event_deliveries d
JOIN events e ON e.id = d.event_id
CROSS JOIN LATERAL jsonb_array_elements(e.reminders) AS r
WHERE d.reminder_id IS NULL;

DELETE FROM event_deliveries WHERE reminder_id IS NULL;

ALTER TABLE event_deliveries ALTER COLUMN reminder_id SET NOT NULL;

DROP INDEX event_deliveries_event;
CREATE INDEX event_deliveries_reminder ON event_deliveries (event_id, reminder_id, position);
