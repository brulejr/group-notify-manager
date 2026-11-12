# Delivery plugins

A delivery plugin is one way of sending a notification. Two are bundled; anything
else, a packet radio gateway, a pager, Matrix, is a plugin you write and drop in.

## Instances

A plugin is never used directly. An admin sets it up for one group as a named
**instance** on the **Delivery plugins** page, e.g. groups.io as *Club announcements*
for `notify_club`, holding everything the plugin needs: credentials, where it sends,
and defaults for how it posts. A group may have several instances of one plugin
(*Club announcements* and *Club chatter*, say), and an instance belongs to its group
alone.

A notification then chooses among its group's instances. It may change only the
fields the plugin marks as **defaults** (*overridable*), such as hashtags or pinning
a topic, never a credential or where it sends; those it changes are stored with the
notification, and the rest follow the instance, so a later change to an instance's
default reaches every notification that kept it.

- Only admins create, edit and delete instances. Members see their groups' instances,
  without secrets, to choose among them.
- Secret fields (an API key, a webhook URL) are encrypted with `NOTIFY_SECRET_KEY`
  (AES-256-GCM) before they are stored, never shown again, and opened only to send.
- An instance a notification still uses cannot be deleted.
- Changes to instances are recorded in the history, naming the fields changed but never
  a secret's value.

The REST API is `/api/instances` (`?group=` to filter): `GET`, and for admins `POST`,
`PUT /{id}` and `DELETE /{id}`. A notification's delivery is
`{"instance": "<id>", "overrides": {…}, "enabled": true}`.

## The bundled plugins

### groups.io (`groupsio`)

Posts a new topic to a group through the [groups.io REST API](https://groups.io/api),
as the member whose API key it holds: `newdraft`, then `updatedraft` with the subject
and the sanitised HTML body, then `postdraft`. It then finds the post in the archive
(`getmessages`), which `postdraft` does not return, so the history links to it, and
applies any topic settings to the topic it started (`updatetopic`). Everything it uses
is on groups.io's free plan; subgroups, which are not, are not offered.

| Field | Default? | |
| --- | --- | --- |
| Group | | The name in `groups.io/g/<name>`, e.g. `myclub` |
| API key | | Secret. From groups.io → Account → API Keys (groups.io/settings/apikeys); posts go out as its member |
| Hashtags | yes | Prepended to the subject, e.g. `#announcement`; groups.io files the post under them |
| Special notice | yes | Reaches every member, even those on no email or digests. Moderators and owners only |
| Post as the group's owners | yes | Sent from the group's owner address. Moderators and owners only |
| Pin to the top | yes | The topic stays at the top of the archive |
| Lock the topic | yes | No replies: for announcements |
| Replies go | yes | To the group, the sender, both, only the sender, or the moderators; blank keeps the group's default |
| Moderate replies | yes | Replies wait for approval |
| API address | | Only for a group on its own domain; default `https://groups.io` |

The topic settings need the owner's or a moderator's `edit_archives` permission. They
cannot undo the post, so if the post cannot be found in the archive or groups.io
refuses a setting, the delivery still counts as sent and its detail says what was not
applied. A post held for approval has no topic yet, so they are skipped.

| Setting (`notify.delivery.settings.groupsio.*`) | |
| --- | --- |
| `timeout` | Seconds per call; default 15 |
| `lookup-attempts`, `lookup-delay-ms` | How often, and how far apart, to look for the new post in the archive, which can lag a little; default 4 and 1500 |

The key's member must be subscribed with permission to post. A moderated group holds
the post unless that member may post unmoderated; the history then says *held for
moderator approval*. groups.io's own refusals (`not subscribed`, `need hashtag`,
`restricted hashtag`, …) are recorded as they are, and the unused draft is deleted.

Failures before the post (groups.io busy or unreachable) are retried. A timeout or
server error during the post itself is not: groups.io may have posted it, and a
retry could post twice, so check the group before sending again.

### Discord (`discord`)

Posts through a channel webhook (channel settings → Integrations → Webhooks). The
webhook URL is the credential, so it is a secret field; an instance may also set the
name and avatar it posts as. Defaults a notification may change: style, an embed card
(title, description, footer with the group) or a plain message; and mention,
`@everyone`, `@here` or a role id, nothing pinging otherwise. Text is cut to Discord's
limits.

| Setting | |
| --- | --- |
| `allowed-hosts` | Webhook hosts accepted; default Discord's own |
| `timeout` | Seconds; default 10 |

### Log only (`log`)

Built in. Writes the notification to the log under `notify.plugin.log`, its subject
alone or with the text (a default). For trying a schedule out. Turn it off with
`notify.delivery.log-plugin=false`.

## Settings

Credentials belong to instances. What is left for the installation is policy, under
`notify.delivery.settings.<id>`:

```yaml
notify:
  delivery:
    settings:
      groupsio:
        timeout: 20
      discord:
        allowed-hosts: discord.com
```

Keys are matched in kebab case however they are written, so an environment variable
works directly: `NOTIFY_DELIVERY_SETTINGS_DISCORD_TIMEOUT`. A plugin that cannot work
still loads, shows as **not ready** in the console with the reason, and its deliveries
fail with that reason. `notify.delivery.disabled: [discord]` stops a plugin loading at
all.

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

    // What an instance is set up with; the console draws the form from this. Credentials
    // are secret fields. Only overridable ones can be changed by a notification.
    override val fields = listOf(
        PluginField("to", "Callsign", required = true, placeholder = "W1AW-9"),
        PluginField("passcode", "APRS-IS passcode", required = true, secret = true),
        PluginField("prefix", "Prefix", placeholder = "e.g. CLUB", overridable = true),
    )

    // Installation policy, once: which server, say. Don't throw for a missing one: report it.
    override fun start(context: PluginContext) {
        this.context = context
        status = PluginStatus.requireSettings(context.settings, "notify.delivery.settings.aprs", "server")
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
            AprsIs(context.settings["server"]!!, target.require("passcode")).send(target.require("to"), text)
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
| `settings` | This plugin's `notify.delivery.settings.<id>.*`: policy, not credentials |
| `http` | A shared `java.net.http.HttpClient`; set a timeout per request |
| `mailer` | The application's SMTP connection, or null if none |
| `log` | The application log, under `notify.plugin.<id>` |
| `toJson` / `parseJson` | JSON without a library of your own |

Rules worth keeping:

- **Return failures, don't throw them.** Say whether a retry could help. An exception
  is caught and recorded, but with less to say.
- **Mark secrets.** A field with `secret = true` is encrypted at rest, never shown
  again once saved, and must never appear in `summarize`. A secret cannot be
  overridable.
- **Only how, never where.** Mark a field `overridable` only if a notification may
  safely change it: never a credential, an address or anything that decides where it
  sends.
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
