# Local provisioning

The application in a container, with PostgreSQL and Mailpit, as close to production
as a workstation gets without Traefik and Authelia.

```bash
./scripts/bootstrap.sh
./gradlew bootJar
docker compose -f docker/local/compose.local.yaml --env-file .env up -d --build
```

The console is on http://localhost:5002 and the Mailpit inbox on http://localhost:8025.

With no Authelia in front, every request is signed in as `DEV_USER` (default
`devuser`) in `DEV_USER_GROUPS` (default `homelab_admins,notify_club`). Set them in
`.env` to try another user's view.

Stop an instance running in the IDE first: two schedulers would send every
notification twice.
