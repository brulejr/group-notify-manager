# Development

```bash
./gradlew build                 # every module, with tests
./gradlew :plugins:sms:test     # one plugin
./gradlew bootRun               # the application on :5002
./gradlew bootJar               # build/libs/group-notify-manager.jar
```

## Source layout

```
plugin-api/                     the plugin contract; testFixtures: fakes for plugin tests
plugins/{discord,email,groupsio,sms}/
src/main/kotlin/io/brulejr/groupnotify/
  platform/                     clock, Flyway migrator, StoreType
  security/                     forward-auth filter, OIDC mapping, NotifyUser, /api/me
  group/                        notification groups and who may see them
  audit/                        history: stores, service, api/, web/
  content/                      Markdown, sanitised HTML, text, placeholders
  delivery/                     plugin host: loader, context, retries, secrets, LogPlugin
  notification/                 the model, stores, service, api/, web/
  dispatch/                     scheduler, Send now
  console/                      dashboard, shared web pieces (Htmx, Toast, ConsoleModel)
src/main/resources/
  db/migration/                 Flyway
  templates/ui/                 Thymeleaf
  static/assets/                notify.css, notify.js
```

## Tests

| | |
| --- | --- |
| Plugin modules | Each plugin against `FakePluginContext`, a recording mailer or a stub HTTP server |
| `*ServiceTest`, `ScheduleTest`, `HtmlFlattenerTest`, … | Plain unit tests; `support/Fixtures` wires the services without Spring on a `TestClock` |
| `support/WebTest` subclasses | The whole application through MockMvc and the security filters, signed in by `Remote-*` headers |
| `ConsolePagesTest` | Renders every console page; extend it with each new one |
| `*/postgres/*Test` | Against a Testcontainers PostgreSQL; skipped without Docker |

No test reaches a real service; use the `log` plugin or `RecordingPlugin`.

## Conventions

[`.claude/CONVENTIONS.md`](../.claude/CONVENTIONS.md), shared with iot-pipeline and
oiltank-monitor, and the notes in [`CLAUDE.md`](../CLAUDE.md).
