# The console

Served under `/ui`: server-rendered with Thymeleaf, made interactive with htmx, styled
with Bootstrap in light and dark. Every library is served from the jar, so it works
without internet.

| Page | |
| --- | --- |
| Dashboard | Counts, what is coming up, the plugins' readiness, recent activity. Reloads itself |
| All notifications | Filter by text, group and status; pause, resume, send now, edit, delete from the row |
| New / Edit | The editor, below |
| A notification | The message as it will be sent, its schedule and next runs, its deliveries (each with a Test button), its history |
| History | Every change and delivery in your groups; filter by group and kind, load older |
| Groups | Your groups, with counts; start a notification in one |
| Delivery plugins | Your groups' delivery instances, and each plugin, whether it is ready and what an instance is set up with. Those who manage a group create, edit and delete its instances here; admins also see where each plugin came from |

## Writing a notification

- **Group** decides who can see it: only that group's members, and admins.
- **Title** is for the console, **subject** for recipients.
- **Body**: switch between **Markdown** (EasyMDE, with its own side preview) and
  **Rich text** (Quill). Switching converts what is there; going from rich text to
  Markdown loses colours and alignment. **Preview** shows the body exactly as each kind
  of delivery receives it: sanitised HTML, the Markdown Discord gets, and plain text.
- **Schedule**: one-off at a date and time; recurring by cron with an optional last
  day; or a **pattern** in words, in a time zone. The next runs appear as you type, and
  a pattern is said back in plain English. A pattern is:
  - `every [other | N] hour | day | week | month`, or `every [other] tuesday`;
  - then `on tuesday, thursday`, `on the 3rd tuesday` (1st–5th or last) or `on day 15`;
  - `at 6pm` (or `18:00`, `noon`); for hours, the time it counts from, midnight unless
    said, so `every 2 hours at 8am` runs at 8, 10, 12…;
  - optionally `starting 2027-02-01` and `until December 31, 2027`.

  For example `every other month on the 3rd tuesday at 6pm`. "Every other" counts from
  the start, today unless you say; a month without the day (a 5th Tuesday, a 31st) is
  skipped. Words it does not know are named, not guessed.
- **Deliveries**: **Add delivery** lists the group's delivery instances; pick one or
  more. Each shows the settings this notification may change, with the instance's
  defaults filled in, and *Set for this notification* against any you changed. The
  switch disables a delivery without removing it. Credentials are not here: they are on
  the instance. A group with no instances says so, with a link to set one up.
- New notifications can **start paused**.

A refused form is redrawn with each problem against its input.

## Events

**Events** in the menu: a group's meetings, nets and field days, each with reminders
around every occurrence. Any member of the group creates and manages them.

- **Event**: group, title, and the subject and body every reminder sends, with the
  placeholders listed beside the form: `{{event.when}}`, `{{event.date}}`, one
  `{{event.<name>}}` per property, and the standard ones.
- **Properties**: name/value pairs, e.g. `location` → `Town hall`.
- **Schedule**: one-off, cron or pattern, as for a notification; each run is an
  occurrence.
- **Reminders**: how long before or after each occurrence, or at its start, each
  switchable off, each with its own subject and body if you open it (the speech
  bubble), in Markdown.
- **Deliveries**: the group's instances, as for a notification, used by every reminder.

An event's page lists its coming occurrences with the reminders each will be sent, and
each one's **note**, as it reads: words for that occurrence's messages only, such as
"This week: guest speaker". **Add note** or **Edit note** opens the occurrence on a page
of its own, with the same editor as the body, Markdown or rich text with its preview,
so the event's page never carries more than one heavy editor. Saving an empty note
removes it. **Send now** sends the
event's own words for its next occurrence; **Test** on a delivery sends through it
alone. Pausing holds every reminder; resuming picks up from now.

## Sending by hand

**Send now** sends through every enabled delivery and does not move the schedule.
**Test** on a delivery sends through that one only. Both are recorded in the history
with a hand icon and your name.

## Setting up a delivery instance

For anyone who manages a group's notifications (and admins, for any group), from
**Delivery plugins**: **New instance** on a plugin or a group, then the group (one you
manage; fixed once created), a name its authors will recognise, and the plugin's
fields: credentials (encrypted, and shown afterwards only as *Saved — leave blank to
keep it*), where it sends, and the defaults its notifications start with, marked *A
default: a notification may change it*. An instance a notification still uses cannot
be deleted; the console names the notifications.
