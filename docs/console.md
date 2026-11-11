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
| Delivery plugins | Each plugin, whether it is ready and why not, what a delivery asks for. Admins also see where it came from and which settings are set |

## Writing a notification

- **Group** decides who can see it: only that group's members, and admins.
- **Title** is for the console, **subject** for recipients.
- **Body**: switch between **Markdown** (EasyMDE, with its own side preview) and
  **Rich text** (Quill). Switching converts what is there; going from rich text to
  Markdown loses colours and alignment. **Preview** shows the body exactly as each kind
  of delivery receives it: sanitised HTML, the Markdown Discord gets, and plain text.
- **Schedule**: one-off at a date and time, or recurring by cron with an optional last
  day, in a time zone. The next runs appear as you type.
- **Deliveries**: **Add delivery** and pick a plugin; its fields appear. A label is
  optional; the switch disables a delivery without removing it. A secret field, such
  as a webhook URL, shows *Saved — leave blank to keep it* once stored.
- New notifications can **start paused**.

A refused form is redrawn with each problem against its input.

## Sending by hand

**Send now** sends through every enabled delivery and does not move the schedule.
**Test** on a delivery sends through that one only. Both are recorded in the history
with a hand icon and your name.
