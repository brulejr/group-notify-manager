# Security, Authelia and groups

Authelia does all signing in. Groups are LLDAP groups, which Authelia passes on.
Nothing about users or membership is stored here.

## Who sees what

| | Sees | Manages | Delivery instances | `/mgmt` |
| --- | --- | --- | --- | --- |
| A member of a group | That group's notifications and history | Them, unless `editor-group` is set and they are not in it | Sees the group's, without secrets, and chooses among them | no |
| An admin (`admin-group`, default `homelab_admins`) | Every group's | Every group's | Provisions, edits and deletes every group's | yes |
| Anyone else | Nothing; another group's notification is *not found* | | | |

A notification can only send through its own group's instances, and can change only
the fields the plugin marks as overridable, never a credential or where it sends; this
is checked when it is saved and again when it is sent.

Which of someone's groups are notification groups is `notify.groups.pattern`, e.g.
`notify_.*`, so `homelab_users` is not offered as a group to notify. Give groups
friendly names with `notify.groups.names`. Create and join groups in LLDAP; a user's
new group shows up at their next request (forward-auth) or sign-in (OIDC).

## Forward-auth (default)

As iot-pipeline is published: Traefik asks Authelia about every request and copies
`Remote-User`, `Remote-Groups`, `Remote-Name` and `Remote-Email` onto it. The
application believes them, so:

1. publish its port on loopback only and route Traefik to the container over the
   Docker network (see [docker/prod](../docker/prod/README.md));
2. give it an `edge_apps` entry with `sso`, whose `groups` decide who may reach it at
   all, e.g. `[homelab_admins, homelab_users]`;
3. optionally set `notify.security.forward-auth.trusted-proxies` (`TRUSTED_PROXIES`)
   to Traefik's addresses; headers from anywhere else are then ignored;
4. set `LOGOUT_URL=https://auth.brulenet.dev/logout` for the sign-out link.

Without the headers every request is refused with 401. `GET /api/me` shows what the
application received.

For development only, `notify.security.dev-user` signs header-less requests in as a
fixed user. The console shows a **Development user** badge, and health reports it.

## OIDC

When the application is reached without forward-auth in front, it can sign in as an
OIDC client of Authelia instead:

```yaml
notify.security.mode: oidc
spring.security.oauth2.client:
  registration.authelia:
    client-id: group-notify
    client-secret: ${OIDC_CLIENT_SECRET}
    scope: [openid, profile, email, groups]
    authorization-grant-type: authorization_code
    redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
  provider.authelia.issuer-uri: https://auth.brulenet.dev
```

and in the inventory's `authelia_oidc_clients`:

```yaml
- id: group-notify
  name: Group Notify
  secret: "{{ vault_group_notify_oidc_client_secret }}"
  redirect_uris: ["https://notify.brulenet.dev/login/oauth2/code/authelia"]
  groups: [homelab_admins, homelab_users]
  id_token_claims: [email, email_verified, name, preferred_username, groups]
```

The username comes from `preferred_username` and groups from `groups`
(`notify.security.oidc.*` renames them). Sign-out ends the application's session and,
where the provider advertises an `end_session_endpoint`, asks it to end its own.

## CSRF

Console requests carry a CSRF token header, matched against the `NOTIFY-XSRF-TOKEN`
cookie. The cookie has its own name because cookies are not kept apart by port: another
app on `localhost` using Spring's default `XSRF-TOKEN` would otherwise replace it. A
refused token answers 403 with `X-Notify-Denied: csrf`, and the console asks for a
reload rather than reporting a missing permission. REST calls need none if they send a JSON
body or an `X-Requested-With` header, neither of which another site can make a
browser send without a CORS preflight, and the application allows no cross-origin
requests. The Bruno collection sends `X-Requested-With` on its body-less calls.

## Secrets

Credentials, such as a groups.io API key or a Discord webhook URL, are entered by an
admin on a delivery instance. They are encrypted before they are stored (AES-256-GCM,
under `NOTIFY_SECRET_KEY`), never returned: the API shows `********` and the form an
empty input, and sending either back keeps the stored value. They are opened only to
send, and never written to the history or the log.

`NOTIFY_SECRET_KEY` is 32 bytes in base64; `scripts/bootstrap.sh` makes one
(`openssl rand -base64 32`). Back it up beside the database: without it, the stored
secrets cannot be read and must be entered again. Without one set, a key is made for
each run, health says so, and secrets stored in PostgreSQL are lost on restart.

The SMTP password, for a plugin that sends mail, stays an installation setting from
the environment.
