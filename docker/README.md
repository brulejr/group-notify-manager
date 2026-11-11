# Docker

Everything for running group-notify-manager in containers lives here, one directory
per way of running it:

| Directory | Use | What runs in Docker |
| --- | --- | --- |
| [`dev/`](dev/README.md) | Development in the IDE | The support services only: PostgreSQL and Mailpit |
| [`local/`](local/README.md) | Local provisioning | The application, PostgreSQL and Mailpit |
| [`prod/`](prod/README.md) | Production, deployed by Ansible behind Traefik and Authelia | The application and PostgreSQL |

Shared by all three:

| Path | |
| --- | --- |
| `Dockerfile` | The application image. It packages the jar Gradle built (`./gradlew bootJar`); it does not build it. |
| `Dockerfile.dockerignore` | Keeps everything but that jar out of the build context, which is the repository root. |
| `postgres/initdb/` | Runs on PostgreSQL's first start: creates the application's role and schema. |

Every compose file is run from the repository root and names the env file
explicitly. Compose looks for `.env` next to the compose file, which here would be
the wrong directory:

```bash
docker compose -f docker/<usage>/compose.<usage>.yaml --env-file .env <command>
```

For `dev`, `scripts/bootstrap.sh` sets `COMPOSE_FILE` in `.env`, so plain
`docker compose up -d` from the repository root works too.

`dev` and `local` share the project name `group-notify-manager` and so the same
volume: `local` runs the development data with the application added, rather than
starting a second copy.

Extra delivery plugins are jars in `plugins-dir/` at the repository root, mounted at
`/app/plugins`; see [docs/plugins.md](../docs/plugins.md).
