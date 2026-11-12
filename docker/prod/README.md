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
  the container over the shared Docker network, as iot-pipeline is. The app joins
  `DOCKER_NETWORK` (default `homelab`, which must already exist) as
  `group-notify-manager` (`APP_CONTAINER_NAME`); PostgreSQL stays on the stack's own
  network;
- add it to `edge_apps` with `sso`, which turns into an Authelia access rule, e.g.

  ```yaml
  - name: notify
    namespace: "homelab"
    fqdn: "notify.brulenet.dev"
    backend_url: "http://group-notify-manager:5002"
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
| `NOTIFY_SECRET_KEY` | yes | Encrypts delivery instances' secrets: 32 bytes in base64 (`openssl rand -base64 32`). Back it up; without it, stored secrets must be re-entered |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_FROM_NAME` | | An SMTP server, only for an external plugin that sends mail |
| `PLUGINS_DIR` | | A host directory of extra plugin jars; default `plugins-dir/` in the checkout |
| `DOCKER_NETWORK` | | Traefik's shared network, joined by the app; default `homelab` |
| `APP_CONTAINER_NAME` | | The name Traefik reaches it by; default `group-notify-manager` |

Any other setting can be given by its full property name in upper case, e.g.
`NOTIFY_DISPATCH_MISFIRE_GRACE=2h`.
