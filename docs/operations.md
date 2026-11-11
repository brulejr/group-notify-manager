# Operations

## Actuator

Under `/mgmt`. Health is open, for container checks; the rest needs an admin.

| Endpoint | |
| --- | --- |
| `/mgmt/health` | Every feature's indicator. `dispatch` goes down when a poll fails or polls stop; `delivery` lists each plugin's readiness but stays up, since a group may not use an unready plugin |
| `/mgmt/info` | How each feature is configured, which plugins came from where, and which settings are set (never their values) |
| `/mgmt/metrics` | Micrometer |

## REST API

All under `/api`, signed in as for the console, limited to the caller's groups. The
Bruno collection in `bruno/group-notify-manager` covers every call.

| | |
| --- | --- |
| `GET /api/me` | Who the application thinks you are |
| `GET, POST /api/notifications` | List (`q`, `group`, `status`), create |
| `GET, PUT, DELETE /api/notifications/{id}` | |
| `GET /api/notifications/{id}/upcoming?count=5` | Next runs |
| `POST /api/notifications/{id}/pause`, `/resume` | |
| `POST /api/notifications/{id}/send[?deliveryId=]` | Send now; reports each delivery |
| `GET /api/audit` | History: `group`, `notificationId`, `type` (repeatable), `since`, `before`, `limit` |
| `GET /api/plugins[/{id}]` | Plugins, their fields and readiness |
| `GET /api/groups` | Your groups with counts |
| `POST /api/content/preview` | A body as HTML, Markdown and text |
| `GET /api/dispatch`, `POST /api/dispatch/run` | The scheduler's state; send what is due now. Admins |

A body-less `POST` or `DELETE` needs an `X-Requested-With` header; see
[security.md](security.md).

## Logs

| Logger | |
| --- | --- |
| `io.brulejr.groupnotify.delivery.PluginLoader` | Each plugin at startup, where from, ready or why not |
| `io.brulejr.groupnotify.dispatch` | Polls that sent something, failed deliveries, retries |
| `notify.plugin.<id>` | What each plugin logs |

## Troubleshooting

- **A notification did not go out.** Its history says whether it was sent, failed
  (with the plugin's reason) or skipped (the application was down past
  `misfire-grace`). No entry at all: is it active, and is `dispatch` healthy?
- **A plugin is "not ready".** The reason names the missing setting.
- **groups.io posts vanish.** The sender is not a member, or the group is moderated;
  check the group's pending messages.
- **401 on everything.** The forward-auth headers are not arriving: check Traefik's
  `authResponseHeaders`, and `trusted-proxies`.
- **Startup fails on the database.** By design; `spring.datasource.*` has unresolved
  placeholders or PostgreSQL is down. Use memory stores to run without it.
