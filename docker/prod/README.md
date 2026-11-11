# Production

The application and PostgreSQL on one Docker host, deployed by Ansible like
iot-pipeline, behind Traefik and Authelia.

```bash
./gradlew bootJar
docker compose -f docker/prod/compose.prod.yaml --env-file <env file> up -d --build
```

## Publishing it

The application has no login of its own in the default `forward-auth` mode: it
believes the `Remote-User`, `Remote-Groups`, `Remote-Name` and `Remote-Email` headers.
So it must only be reachable through Traefik:

- publish it on loopback (`APP_BIND_IP=127.0.0.1`, the default) and route Traefik to
  the container over the shared Docker network, as iot-pipeline is;
- add it to `edge_apps` with `sso`, which turns into an Authelia access rule, e.g.

  ```yaml
  - name: notify
    namespace: "homelab"
    fqdn: "notify.brulenet.dev"
    backend_url: "http://group-notify-manager-app-1:5002"
    sso:
      groups: [homelab_admins, homelab_users]
  ```

- optionally set `TRUSTED_PROXIES` to Traefik's network, e.g. `172.16.0.0/12`, so the
  headers are ignored from anywhere else.

Authelia decides who reaches the application at all; the groups it passes decide which
notifications each person sees. See [docs/security.md](../../docs/security.md).

## Environment

| Variable | Required | |
| --- | --- | --- |
| `POSTGRES_DB`, `POSTGRES_ADMIN_PASSWORD`, `POSTGRES_APP_USERNAME`, `POSTGRES_APP_PASSWORD` | yes | The database, as `scripts/bootstrap.sh` generates them |
| `ADMIN_GROUP` | | Sees every group; default `homelab_admins` |
| `GROUP_PATTERN` | | Which groups hold notifications, e.g. `notify_.*`; default every group |
| `LOGOUT_URL` | | The console's sign-out link, e.g. `https://auth.brulenet.dev/logout` |
| `TRUSTED_PROXIES` | | Where forward-auth headers are believed from |
| `DEFAULT_ZONE` | | New notifications' time zone; default `America/New_York` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_FROM_NAME` | for email and groups.io | The SMTP server, e.g. Gmail with an app password |
| `GROUPSIO_FROM` | | The member address groups.io posts go out as, if not `MAIL_FROM` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` or `TWILIO_MESSAGING_SERVICE_SID` | for SMS | The Twilio account |
| `PLUGINS_DIR` | | A host directory of extra plugin jars; default `plugins-dir/` in the checkout |

Any other setting can be given by its full property name in upper case, e.g.
`NOTIFY_DISPATCH_MISFIRE_GRACE=2h`.
