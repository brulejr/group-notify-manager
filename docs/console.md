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
| Delivery plugins | Your groups' delivery instances, and each plugin, whether it is ready and what an instance is set up with. Admins create, edit and delete instances here, and see where each plugin came from |

## Writing a notification

- **Group** decides who can see it: only that group's members, and admins.
- **Title** is for the console, **subject** for recipients.
- **Body**: switch between **Markdown** (EasyMDE, with its own side preview) and
  **Rich text** (Quill). Switching converts what is there; going from rich text to
  Markdown loses colours and alignment. **Preview** shows the body exactly as each kind
  of delivery receives it: sanitised HTML, the Markdown Discord gets, and plain text.
- **Schedule**: one-off at a date and time, or recurring by cron with an optional last
  day, in a time zone. The next runs appear as you type.
- **Deliveries**: **Add delivery** lists the group's delivery instances; pick one or
  more. Each shows the settings this notification may change, with the instance's
  defaults filled in, and *Set for this notification* against any you changed. The
  switch disables a delivery without removing it. Credentials are not here: they are on
  the instance. A group with no instances says so; an admin sets them up.
- New notifications can **start paused**.

A refused form is redrawn with each problem against its input.

## Sending by hand

**Send now** sends through every enabled delivery and does not move the schedule.
**Test** on a delivery sends through that one only. Both are recorded in the history
with a hand icon and your name.

## Setting up a delivery instance

Admins only, from **Delivery plugins**: **New instance** on a plugin or a group, then
the group (fixed once created), a name its authors will recognise, and the plugin's
fields: credentials (encrypted, and shown afterwards only as *Saved — leave blank to
keep it*), where it sends, and the defaults its notifications start with, marked *A
default: a notification may change it*. An instance a notification still uses cannot
be deleted; the console names the notifications.
