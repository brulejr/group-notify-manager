# group-notify-manager

Kotlin / Spring Boot service that sends group notifications on schedule through
pluggable deliveries (groups.io, Discord, and external plugin jars), with
an htmx console, a REST API and an audit trail, behind Authelia. `docs/DESIGN.md` is
the authoritative design doc: read it before changing structure.

@.claude/CONVENTIONS.md

## Layout

A Gradle multi-project build:

- `plugin-api/`: the delivery plugin contract (`DeliveryPlugin`, `PluginContext`, …),
  free of Spring and of any dependency but the Kotlin standard library. Its
  `testFixtures` hold a fake context, a recording mailer and a stub HTTP server.
- `plugins/<id>/`: the bundled plugins (discord, groupsio). Each is its own
  jar, found through `META-INF/services/io.brulejr.groupnotify.plugin.DeliveryPlugin`,
  never referenced from the application's code (they are `runtimeOnly`).
- the root project: the application, package `io.brulejr.groupnotify`.

One package per feature: `notification/`, `dispatch/` (the scheduler and Send now),
`delivery/` (the plugin host: loading, settings, retries, secret masking), `content/`
(Markdown, sanitised HTML, text, placeholders), `audit/`, `group/`, `console/`.
Following the conventions, each has exactly one `*Config` declaring its beans, a
validated `*Datafill`, a `*Service` holding its logic, a REST controller under `api/`,
a page controller under `web/`, a `*HealthIndicator` and a `*InfoContributor`.
Cross-cutting pieces: `platform/` (clock, `DatabaseMigrator`, `StoreType`,
`SqlTimestamps`) and `security/` (forward-auth filter, OIDC mapping, `NotifyUser`).

Stores sit behind ports (`NotificationPort`, `AuditPort`) with PostgreSQL and memory
implementations, picked by `store: postgres|memory` in the feature's Datafill.
Visibility is enforced in the services, never only in controllers: every
user-facing method takes a `NotifyUser`, and a notification outside the user's groups
is reported as not found.

## Commands

```
./gradlew build          # compile + test, every module
./gradlew test           # tests only
set -a; . ./.env; set +a  # or let application-local.yml import .env
./gradlew bootRun        # app on :5002
docker compose up -d     # dev services via COMPOSE_FILE in .env: PostgreSQL :5433, Mailpit :1025/:8025
./scripts/bootstrap.sh   # first run: generates the passwords, writes .env
```

## Notes

- Java 21 toolchain. Jackson is the `tools.jackson` 3.x namespace. Spring Boot 4.1.
- Port 5002 (iot-pipeline and oiltank-monitor use 5001); PostgreSQL on 5433.
- Flyway runs through `DatabaseMigrator`, not auto-configuration. Schema changes are
  new files in `src/main/resources/db/migration`; never edit one that has shipped.
- Spring tests use `@ActiveProfiles("test")` (`application-test.yml`): memory stores,
  scheduler off, a datasource pointing nowhere. Extend `support/WebTest` for MockMvc
  with the security filters; sign requests in with `Remote-*` headers. No test may
  reach a real service: use the `log` plugin or `RecordingPlugin`.
- The `*/postgres/*Test` classes run against Testcontainers and are skipped without Docker.
- The boot jar's Main-Class is `PropertiesLauncher`, so `loader.path` / `LOADER_PATH`
  adds plugin jars. Don't switch it back to `JarLauncher`.
- Secrets in a delivery target (`PluginField.secret`) never leave the server: the API
  returns `********`, the form leaves the input empty, and blank or the mask on save
  keeps the stored value (`DeliveryService.keepSecrets`).
- Templates: give a template Java collections (`toMutableList()`), never Kotlin's empty
  singletons. Never put `th:if`/`th:each` on the element carrying `th:replace`. A
  container that sets `hx-target` also sets `hx-disinherit="*"`. Fragments fetched on
  their own go in a template that is never rendered as a page (`notifications/parts.html`).
  `ConsolePagesTest` renders every page; extend it with each new one.
- Every `.kt` file under `src/` opens with the MIT license header block comment.
