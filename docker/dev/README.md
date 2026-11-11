# Development services

PostgreSQL and Mailpit for an application running in the IDE.

```bash
./scripts/bootstrap.sh     # once: passwords into .env, COMPOSE_FILE pointing here
docker compose up -d       # from the repository root
./gradlew bootRun          # the application, on http://localhost:5002
```

| Service | Host port | |
| --- | --- | --- |
| PostgreSQL | `POSTGRES_PORT`, default 5433 | Database `group_notify`, schema `group_notify`, role `group_notify` |
| Mailpit | 1025 (SMTP), 8025 (inbox) | Catches mail from any plugin that sends it (none of the bundled ones do) |

`application-local.yml` (copied from `application-local.example.yml`) imports `.env`,
points SMTP at Mailpit and signs every request in as a development user, since there
is no Authelia locally.

To start from an empty database: `docker compose down -v`.
