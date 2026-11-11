# Security, Authelia and groups

Authelia does all signing in. Groups are LLDAP groups, which Authelia passes on.
Nothing about users or membership is stored here.

## Who sees what

| | Sees | Manages | `/mgmt` |
| --- | --- | --- | --- |
| A member of a group | That group's notifications and history | Them, unless `editor-group` is set and they are not in it | no |
| An admin (`admin-group`, default `homelab_admins`) | Every group's | Every group's | yes |
| Anyone else | Nothing; another group's notification is *not found* | | |

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

Plugin credentials (such as the SMTP password) are installation settings, from the
environment, never in the database. Per-delivery secrets, such as a Discord webhook
URL, are stored with the notification but never returned: the API shows `********`
and the form an empty input. Sending either back keeps the stored value. They are not
encrypted at rest yet; see the open questions in [DESIGN.md](DESIGN.md).
