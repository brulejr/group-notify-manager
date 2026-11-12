# group-notify-manager: Design

Status: first build. Everything described here is implemented unless marked
**Proposed**. Open questions are collected at the end.

## Goal

Let the members of a group (a radio club, a family, a committee) schedule
notifications to themselves and others, once or recurring, delivered through whatever
channels the group uses, with a record of what went out. Groups and membership are
not managed here: they come from LLDAP through Authelia, as every other homelab app
gets them.

## Principles

- **One JVM**, REST API and htmx console side by side, as in iot-pipeline. The console
  calls the same services as the API; nothing is console-only.
- **Feature packages, explicit wiring.** Each feature has a `*Config`, `*Datafill`,
  `*Service`, controllers, health and info. No `@Component`/`@Service`.
- **Visibility lives in the services.** A user-facing service method takes the
  `NotifyUser`; no controller can forget to filter.
- **Plugins know nothing of Spring.** The plugin API depends on the Kotlin standard
  library alone, so plugins are small, can be built elsewhere, and survive the
  application changing its libraries.
- **Every send leaves a record**, success or failure, and changes do too.

## Features

| Package | Responsibility |
| --- | --- |
| `security` | Who the user is: forward-auth headers or OIDC → `NotifyUser` (username, groups, admin, editor) |
| `group` | Which groups hold notifications, and who may see or change a group's |
| `notification` | Notifications and their deliveries: validation, schedule, pause/resume, stores |
| `content` | The body in every form: Markdown → HTML, HTML sanitising, HTML → Markdown / text, placeholders |
| `delivery` | The plugin host: loading, settings, the context lent to plugins, retries, masking |
| `instance` | Plugins provisioned per group as named instances: credentials, defaults, encryption of secrets |
| `dispatch` | The scheduler that finds due notifications, and Send now |
| `audit` | The history of changes and deliveries |
| `console` | The dashboard and the pieces every page shares |
| `platform` | Clock, PostgreSQL schema migration, store type |

Dependencies run one way: `security` ← `group` ← `audit` ← `instance` ←
`notification` ← `dispatch`, with `delivery` used by `instance` and `dispatch`, and
`content` by `notification` and `dispatch`. `instance` learns which notifications use
an instance through `InstanceUsage`, which `notification` provides, rather than by
depending on it.

## Model

A **notification** belongs to one group and has a title (for the console), a subject
and a body (for recipients), a body format (Markdown or HTML), a schedule, a status
and one or more deliveries.

A **schedule** is one-off (a local date-time in a zone), recurring (a cron
expression in a zone, with an optional last day), or a **pattern** in words. Cron is read in the zone, so
`0 18 * * TUE` stays at 18:00 local across daylight saving. Five-field cron is
accepted and converted to Spring's six-field form; Spring's `L` and `TUE#2` work.

A **pattern** (`SchedulePattern`, read by `PatternParser`) covers what cron cannot:
intervals counted from a start date, so "every other week" or "every 3rd month from
February". It is every N hours, days, weeks or months from its start; an hourly one from
its time on the local clock (each run sent once across a daylight saving change); a weekly one on given
weekdays, a monthly one on the nth (1st–5th) or last weekday or on day n, at a local
time, until an optional last day. A month without the day is skipped. The parser is
deterministic, built on `java.time` alone: the same words always mean the same, and
words it does not know are named in the error. A pattern is stored as its canonical
words, which always include the start (today when not said), so it reads back the
same, and "every other" keeps counting from the same day, however often it is edited.

**Status** is `ACTIVE`, `PAUSED` or `COMPLETED`. `nextRunAt` is set only while
active. A one-off completes once sent; a recurring one when it passes its end date.
Resuming picks up from now: missed runs are not sent. A one-off whose time passed while
paused cannot be resumed without a new time.

A **plugin instance** is a delivery plugin set up by an admin for one group under a
name: every one of the plugin's field values, credentials included. Its group and
plugin are fixed once created; its name is unique within the group.

A **delivery** names one instance of the notification's own group, the notification's
**overrides** of the plugin's overridable fields, and an enabled flag. Only overrides
that differ from the instance are kept, so a later change to an instance's default
reaches every notification that did not choose otherwise. At send time the instance's
values, secrets opened, and the overrides are merged into the target the plugin is
given.

Why instances, and not a plugin's configuration on each notification: one place to
enter and rotate a credential, so every notification of a group posts the same way;
and authors, who need not be trusted with credentials, can only send where an admin
has set up for their group, changing how a post looks but never where it goes.

## Security and groups

Authelia signs people in; see [security.md](security.md).

- `FORWARD_AUTH` (default, like iot-pipeline): Traefik asks Authelia, then copies
  `Remote-User`, `Remote-Groups`, `Remote-Name` and `Remote-Email` onto the request.
  A filter turns them into a `NotifyUser`. Stateless. Headers from outside
  `trusted-proxies` are ignored.
- `OIDC`: the application is an OIDC client of Authelia and reads `groups` from the ID
  token. Session-based.

Roles come from groups: `admin-group` sees and manages everything and reaches
`/mgmt`; with `editor-group` set, only its members manage notifications, others read.
A notification outside the user's groups is **not found**, not forbidden, so its
existence is not revealed.

CSRF: the console sends a token header on every htmx request (cookie repository, so
forward-auth stays stateless). REST calls are exempt when they carry a JSON body or
`X-Requested-With`, either of which forces a CORS preflight that this application
never grants.

## Content

The author writes Markdown (EasyMDE) or HTML (Quill). Switching editors converts on
the server. At send time placeholders are filled (HTML-escaped in HTML bodies), then:

- **HTML**: Markdown rendered by commonmark (GFM tables, strikethrough, autolinks,
  single newlines kept as breaks), then sanitised by jsoup's relaxed safelist. The
  console shows only sanitised HTML.
- **Markdown**: the source, or HTML converted back (`HtmlFlattener`).
- **Text**: the sanitised HTML flattened, links as `text (url)`.

Plugins receive all three plus the subject, in a `NotificationMessage`.

## Delivery plugins

See [plugins.md](plugins.md). A plugin declares its **fields** (what an instance is
set up with, which the console turns into a form; some marked `overridable`, never a
secret one) and receives its **settings** (installation policy such as timeouts from
`notify.delivery.settings.<id>.*`, not credentials) once, in `start`. It is lent HTTP,
JSON, mail and a log through `PluginContext`.

Plugins are found by `ServiceLoader` from the class path, which includes any jars in
`loader.path`: the boot jar starts with `PropertiesLauncher`. The `log` plugin is a
bean instead, showing the other way in. Duplicate ids stop startup; a plugin that
fails to start loads as not ready, so its deliveries fail visibly.

The host validates required fields, select options, URLs and numbers before asking
the plugin. Secret fields never leave the server. A plugin's failure is either
retryable (timeouts, 5xx, rate limits) or not; retryable ones are retried up to
`max-attempts` with doubling backoff. A plugin that throws is treated as failing.

Bundled: **groups.io** (REST API: draft, update, post as the API key's member;
hashtags in the subject; topic settings applied after posting; no retry once the
post's outcome is unclear) and **Discord** (channel webhook, embed or plain, explicit
`allowed_mentions`).

## Dispatch

A poller calls `DispatchService.runDue()` every `poll-interval`. For each due
notification it first **advances** the stored notification to its next run,
conditionally on it still being active and due at the same time. Then it sends.

- Advancing first means a crash mid-send does not resend on restart (at most once).
- The condition means a pause or edit made meanwhile wins, and two pollers cannot both
  send the same run.
- The next run is computed from now, so a long outage sends once, not once per
  missed run. A run later than `misfire-grace` is **skipped** and recorded instead:
  a reminder for a meeting that already happened helps nobody.

Notifications are sent in parallel (`threads`); one notification's deliveries in
order. Send now delivers immediately, optionally through one delivery only (the
console's Test button), and leaves the schedule alone.

## Audit

Every create, edit (with what changed), pause, resume, delete, send, failure, skip and
completion; and every change to an instance, naming the fields changed but never a
secret's value. Delivery events carry the plugin, the instance's name and summary (never a secret),
the detail or failure reason, the service's reference id and the number of attempts.
Events are kept when the notification is deleted and purged after `retention`.
History is filtered by group exactly as notifications are.

## Storage

PostgreSQL in its own schema (`group_notify`), migrated by Flyway on first use; or
memory, for tests and trying it out. Tables: `notifications` (a pattern schedule in `pattern`, since V3), `deliveries` (cascade
delete with their notification; a foreign key to `plugin_instances`, so an instance in
use cannot be deleted), `plugin_instances` (configuration as `jsonb`, secret values
sealed as `enc:v1:…`), `audit_events`.

Instance secrets are sealed with AES-256-GCM under `notify.instances.secret-key`
(`NOTIFY_SECRET_KEY`, 32 bytes in base64, written to `.env` by `scripts/bootstrap.sh`),
a fresh nonce each time; the tag also detects a value altered in the database. Without
a key one is made for the run: health warns, and in PostgreSQL goes down once there are
instances, as their secrets would not survive a restart.

V2 introduced instances and dropped the per-notification delivery configuration that
came before, leaving those notifications with no deliveries until instances are chosen.

## Open questions

1. **Rotating the secret key.** Instance secrets are encrypted, but there is no way yet
   to re-seal them under a new `NOTIFY_SECRET_KEY`; for now, set the new key and
   re-enter each secret. **Proposed:** accept a previous key for reading.
2. **Finding a groups.io post.** `postdraft` answers without the new message, so the
   plugin finds it in the archive: the newest message by the key's member, posted
   since the send began, whose subject carries the notification's. Two identical
   notifications sent at once to the same group could be confused.
3. **Recipients from LDAP.** Instance targets are typed in. They could come from
   group members' LLDAP attributes, so "every member of notify_club" stays current.
4. **More than one instance.** The conditional advance already prevents double sends;
   nothing else assumes one instance, but it has not been tried.
5. **Approval.** Should a member's notification to a large list need an admin's
   approval before it goes out?
