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
| `delivery` | The plugin host: loading, settings, the context lent to plugins, retries, secrets |
| `dispatch` | The scheduler that finds due notifications, and Send now |
| `audit` | The history of changes and deliveries |
| `console` | The dashboard and the pieces every page shares |
| `platform` | Clock, PostgreSQL schema migration, store type |

Dependencies run one way: `security` ← `group` ← `audit` ← `notification` ←
`dispatch`, with `delivery` and `content` used by `notification` and `dispatch`.

## Model

A **notification** belongs to one group and has a title (for the console), a subject
and a body (for recipients), a body format (Markdown or HTML), a schedule, a status
and one or more deliveries.

A **schedule** is one-off (a local date-time in a zone) or recurring (a cron
expression in a zone, with an optional last day). Cron is read in the zone, so
`0 18 * * TUE` stays at 18:00 local across daylight saving. Five-field cron is
accepted and converted to Spring's six-field form; Spring's `L` and `TUE#2` work.

**Status** is `ACTIVE`, `PAUSED` or `COMPLETED`. `nextRunAt` is set only while
active. A one-off completes once sent; a recurring one when it passes its end date.
Resuming picks up from now: missed runs are not sent. A one-off whose time passed while
paused cannot be resumed without a new time.

A **delivery** is a plugin id, a target (the values of that plugin's fields), a label
and an enabled flag. Targets are stored as `jsonb`.

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

See [plugins.md](plugins.md). A plugin declares its **fields** (what each delivery
asks for, which the console turns into a form) and receives its **settings**
(per-installation credentials from `notify.delivery.settings.<id>.*`) once, in
`start`. It is lent HTTP, JSON, mail and a log through `PluginContext`.

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
completion. Delivery events carry the plugin, the target's summary (never a secret),
the detail or failure reason, the service's reference id and the number of attempts.
Events are kept when the notification is deleted and purged after `retention`.
History is filtered by group exactly as notifications are.

## Storage

PostgreSQL in its own schema (`group_notify`), migrated by Flyway on first use; or
memory, for tests and trying it out. Tables: `notifications`, `deliveries` (cascade
delete), `audit_events`.

## Open questions

1. **Secrets at rest.** Discord webhook URLs and the like are stored in plain `jsonb`.
   **Proposed:** encrypt secret fields with a key from the environment.
2. **Finding a groups.io post.** `postdraft` answers without the new message, so the
   plugin finds it in the archive: the newest message by the key's member, posted
   since the send began, whose subject carries the notification's. Two identical
   notifications sent at once to the same group could be confused.
3. **Recipients from LDAP.** Delivery targets are typed in. They could come from
   group members' LLDAP attributes, so "every member of notify_club" stays current.
4. **More than one instance.** The conditional advance already prevents double sends;
   nothing else assumes one instance, but it has not been tried.
5. **Approval.** Should a member's notification to a large list need an admin's
   approval before it goes out?
