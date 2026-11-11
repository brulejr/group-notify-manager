# Configuration

Everything is in `src/main/resources/application.yml`, each value under a feature's
prefix and bound to that feature's `*Datafill`, which validates it at startup. Most
have a short environment variable; any property can also be set by its full name in
upper case (`notify.dispatch.misfire-grace` → `NOTIFY_DISPATCH_MISFIRE_GRACE`).

## Secrets and `.env`

`scripts/bootstrap.sh` writes the PostgreSQL passwords to `.env` (gitignored). Add
the rest by hand:

```properties
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=me@gmail.com
MAIL_PASSWORD=<app password>
MAIL_FROM=me@gmail.com
TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=...
TWILIO_FROM=+15551234567
```

`application-local.yml` (copy `application-local.example.yml`; gitignored, and left out
of the boot jar) imports `.env` and sets the development user.

## Reference

| Property | Env | Default | |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | 5002 | |
| `notify.security.mode` | `AUTH_MODE` | `forward-auth` | or `oidc`; see [security.md](security.md) |
| `notify.security.admin-group` | `ADMIN_GROUP` | `homelab_admins` | |
| `notify.security.editor-group` | | unset | When set, only its members manage notifications |
| `notify.security.logout-url` | `LOGOUT_URL` | | |
| `notify.security.forward-auth.trusted-proxies` | `TRUSTED_PROXIES` | any | Comma-separated addresses or CIDRs |
| `notify.security.dev-user.*` | | unset | Development only |
| `notify.groups.pattern` | `GROUP_PATTERN` | `.*` | Which groups hold notifications |
| `notify.groups.exclude`, `.names` | | | |
| `notify.notifications.store` | `NOTIFICATION_STORE` | `postgres` | or `memory` |
| `notify.notifications.default-zone` | `DEFAULT_ZONE` | `America/New_York` | |
| `notify.notifications.max-deliveries` | | 10 | |
| `notify.audit.store` | `AUDIT_STORE` | `postgres` | or `memory` |
| `notify.audit.retention` | | 365d | |
| `notify.dispatch.enabled` | | true | Off: nothing is sent on schedule |
| `notify.dispatch.poll-interval` | | 15s | |
| `notify.dispatch.misfire-grace` | | 1h | A later run is skipped, not sent |
| `notify.dispatch.threads` | | 4 | |
| `notify.delivery.max-attempts` | | 3 | For retryable failures |
| `notify.delivery.retry-backoff` | | 5s | Doubles each try |
| `notify.delivery.disabled` | | | Plugin ids not to load |
| `notify.delivery.log-plugin` | | true | |
| `notify.delivery.mail.from`, `.from-name` | `MAIL_FROM`, `MAIL_FROM_NAME` | `notify@localhost` | |
| `notify.delivery.settings.<id>.*` | | | Plugin settings; see [plugins.md](plugins.md) |
| `spring.mail.host`, `.port`, `.username`, `.password` | `MAIL_HOST`, … | none | No host: email and groups.io not ready |
| `notify.content.date-format`, `.time-format`, `.locale` | | `EEEE, MMMM d, yyyy`, `h:mm a z`, `en-US` | For placeholders |
| `notify.console.title` | `CONSOLE_TITLE` | Group Notify | |
| `notify.console.refresh-interval` | | 30s | Dashboard reload |
| `notify.database.schema` | | `group_notify` | |
| `spring.datasource.*` | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_APP_USERNAME`, `POSTGRES_APP_PASSWORD` | port 5433 | Not defaulted otherwise, deliberately |

## Placeholders

In a subject or body, filled in when sent, in the notification's time zone:

| | |
| --- | --- |
| `{{title}}` | The notification's title |
| `{{group}}` | The group's display name |
| `{{date}}` | e.g. Tuesday, November 10, 2026 |
| `{{time}}` | e.g. 6:00 PM EST |
| `{{weekday}}` | e.g. Tuesday |
| `{{datetime}}` | Both |
