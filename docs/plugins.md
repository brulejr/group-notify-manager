# Delivery plugins

A delivery plugin is one way of sending a notification. Four are bundled; anything
else, a packet radio gateway, a pager, Matrix, is a plugin you write and drop in.

## The bundled plugins

### groups.io (`groupsio`)

Posts to a group or subgroup by emailing its posting address, the way any member
posts. Uses the application's SMTP server.

| Field | |
| --- | --- |
| Group | `myclub`, `myclub+events` for a subgroup, or a full address |
| Hashtags | Prepended to the subject, e.g. `#announcement`; groups.io files the post under them |

| Setting (`notify.delivery.settings.groupsio.*`) | |
| --- | --- |
| `from` (`GROUPSIO_FROM`) | The member address to post as; default `notify.delivery.mail.from` |
| `domain` | Default `groups.io` |

The sender must be subscribed with permission to post. A moderated group holds the
post unless that member may post unmoderated.

### Email (`email`)

HTML with a plain-text alternative, through `spring.mail.*`. Fields: To, Cc, Bcc
(for a list whose members should not see one another), Reply to, Subject prefix. No
settings beyond `notify.delivery.mail.from` and `from-name`.

### Discord (`discord`)

Posts through a channel webhook (channel settings → Integrations → Webhooks). The
webhook URL is the credential, so it is a secret field. Style: an embed card (title,
description, footer with the group) or a plain message. Mention: `@everyone`, `@here`
or a role id; nothing pings otherwise. Text is cut to Discord's limits.

| Setting | |
| --- | --- |
| `allowed-hosts` | Webhook hosts accepted; default Discord's own |
| `timeout` | Seconds; default 10 |

### SMS (`sms`)

One message per number through Twilio, so recipients never see each other. Fields:
numbers (E.164), a prefix such as `[Club]`, whether to start with the subject.

| Setting | Environment | |
| --- | --- | --- |
| `account-sid` | `TWILIO_ACCOUNT_SID` | required |
| `auth-token` | `TWILIO_AUTH_TOKEN` | required (an API key secret works, with `api-key-sid`) |
| `from` | `TWILIO_FROM` | the sending number, or… |
| `messaging-service-sid` | `TWILIO_MESSAGING_SERVICE_SID` | …a messaging service |
| `max-length` | | default 1600 |

A send that reaches some numbers but not others is recorded as a failure naming the
numbers that failed, and is not retried, so nobody is texted twice.

### Log only (`log`)

Built in. Writes the notification to the log under `notify.plugin.log`. For trying a
schedule out. Turn it off with `notify.delivery.log-plugin=false`.

## Settings

Each plugin's installation settings live under `notify.delivery.settings.<id>`:

```yaml
notify:
  delivery:
    settings:
      sms:
        account-sid: ACxxxxxxxx
        auth-token: ${TWILIO_AUTH_TOKEN}
```

Keys are matched in kebab case however they are written, so an environment variable
works directly: `NOTIFY_DELIVERY_SETTINGS_SMS_AUTH_TOKEN`. A plugin missing a setting
still loads, shows as **not ready** in the console with the reason, and its deliveries
fail with that reason until it is configured. `notify.delivery.disabled: [sms]` stops a
plugin loading at all.

## Writing a plugin

A plugin is a jar containing:

1. a class implementing `io.brulejr.groupnotify.plugin.DeliveryPlugin`, with a public
   no-argument constructor;
2. `META-INF/services/io.brulejr.groupnotify.plugin.DeliveryPlugin`, naming it.

It depends on `notify-plugin-api` only (`plugin-api/` here), which itself depends on
nothing but the Kotlin standard library. Here is the outline of an APRS plugin that
sends a message through an APRS-IS server:

```kotlin
class AprsPlugin : DeliveryPlugin {
    private lateinit var context: PluginContext
    private var status = PluginStatus.notReady("not started")

    override val id = "aprs"                     // stored with every delivery: never change it
    override val name = "APRS"
    override val description = "Sends an APRS message to a callsign through APRS-IS."
    override val icon = "broadcast"              // a Bootstrap Icons name

    // What each delivery asks for; the console draws the form from this.
    override val fields = listOf(
        PluginField("to", "Callsign", required = true, placeholder = "W1AW-9"),
        PluginField("prefix", "Prefix", placeholder = "e.g. CLUB"),
    )

    // Installation settings, once. Don't throw for a missing one: report it.
    override fun start(context: PluginContext) {
        this.context = context
        status = PluginStatus.requireSettings(context.settings, "notify.delivery.settings.aprs", "callsign", "passcode", "server")
    }

    override fun status() = status

    override fun validate(target: DeliveryTarget) =
        if (Regex("[A-Z0-9]{3,6}(-\\d{1,2})?").matches(target.require("to").uppercase())) emptyList()
        else listOf(FieldProblem("to", "is not a callsign"))

    // Shown to every group member and in the audit trail: never a secret.
    override fun summarize(target: DeliveryTarget) = target.require("to").uppercase()

    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        // APRS messages are 67 characters; message.text is the plain-text body.
        val text = listOfNotNull(target["prefix"], message.subject).joinToString(" ").take(67)
        return try {
            AprsIs(context.settings).send(target.require("to"), text)
            DeliveryResult.Delivered("sent to APRS-IS")
        } catch (e: java.io.IOException) {
            DeliveryResult.Failed("APRS-IS: ${e.message}", retryable = true)   // retried with backoff
        }
    }
}
```

What the application lends through `PluginContext`:

| | |
| --- | --- |
| `settings` | This plugin's `notify.delivery.settings.<id>.*` |
| `http` | A shared `java.net.http.HttpClient`; set a timeout per request |
| `mailer` | The application's SMTP connection, or null if none |
| `log` | The application log, under `notify.plugin.<id>` |
| `toJson` / `parseJson` | JSON without a library of your own |

Rules worth keeping:

- **Return failures, don't throw them.** Say whether a retry could help. An exception
  is caught and recorded, but with less to say.
- **Mark secrets.** A field with `secret = true` is never shown again once saved, and
  must never appear in `summarize`.
- **Be thread-safe.** Several notifications are sent at once.
- **Keep dependencies inside the jar** (shade them) or, better, use what the context
  lends. Kotlin plugins rely on the application's standard library.

The plugin API's test fixtures give you `FakePluginContext`, `RecordingMailer`,
`StubHttpServer` and `TestMessages`, so a plugin can be tested without the
application: `testImplementation(testFixtures(project(":plugin-api")))` in this build,
or the `notify-plugin-api-*-test-fixtures.jar` that `./gradlew :plugin-api:testFixturesJar`
produces for a build elsewhere. The bundled plugins' tests are examples.

A plugin can be written in Java too; the interface's optional methods are JVM
default methods.

## Installing a plugin

Put the jar in `plugins-dir/` at the repository root (mounted at `/app/plugins` by
the compose files), or anywhere `loader.path` names:

```bash
java -Dloader.path=/opt/notify/plugins -jar group-notify-manager.jar   # or LOADER_PATH=...
```

and restart. The startup log lists every plugin, where it came from and whether it is
ready; so does the console's **Delivery plugins** page. Two plugins with the same id
stop startup with both named.
