# Web Services: BFF

`apps/bff` is a small Spring Boot [Backend-for-Frontend](https://samnewman.io/patterns/architecture/bff/) service that sits between [Dialogue Branch Studio](/web-services/studio) (`apps/web`) and the Web Service. It exists so that the browser never has to hold an OAuth2 access or refresh token: the token lives server-side, in the BFF's HTTP session, and the browser only ever holds a `JSESSIONID` session cookie. See [Studio Authentication (via the BFF)](/web-services/authentication#studio-authentication-via-the-bff) for the full request flow, with diagrams.

Unlike the Web Service, the BFF is deployed as a plain executable JAR (not a WAR), listening on port `8082` by default. It can also be built as a Docker image using the provided `Dockerfile` at `apps/bff/Dockerfile` (built from the repository root, since the Docker build context spans both `apps/bff/` and the rest of the monorepo).

```bash
cd apps/bff
./gradlew build   # compile and build the executable JAR
```

## End-Points

The BFF exposes the following end-points to the browser:

* `GET /oauth2/authorization/keycloak` — starts the login flow: performs the Authorization Code + PKCE exchange against Keycloak and stores the resulting token in the session.
* `GET /whoami` — returns the current session's username and Dialogue Branch Web Service roles as JSON, decoded from the session's access token.
* `/api/**` — proxies every call through to the Web Service, attaching the session's access token as the `Authorization: Bearer` header. `GET /api/v1/info/all` is reachable without a session, matching that end-point's public status on the Web Service itself.
* `POST /logout` — RP-initiated logout: ends both the BFF's session and the underlying Keycloak SSO session.
* `GET /actuator/health`, `/actuator/info` — Spring Boot Actuator health/info endpoints.

## Configuration

The BFF is configured through `dlb.bff.*` properties (see `apps/bff/src/main/resources/application.yml`), each overridable at runtime via a matching `DLB_BFF_*` environment variable:

* `dlb.bff.oauth2-client-id` / `dlb.bff.oauth2-client-secret` — this service's own Keycloak client credentials (`dlb-bff` by default).
* `dlb.bff.keycloak-base-url` — the internal Keycloak address, used for token exchange and signature verification.
* `dlb.bff.keycloak-browser-base-url` — the browser-facing Keycloak address, used to redirect the browser for login and logout. Only differs from the internal address when the BFF runs inside a container network with its own internal Keycloak hostname.
* `dlb.bff.keycloak-realm` — the Keycloak realm (`dialoguebranch` by default).
* `dlb.bff.api-base-url` — the internal address of the Web Service that `/api/**` calls are proxied to.
* `dlb.bff.web-service-client-id` — the Keycloak client id (`dlb-web-service`) whose `resource_access` roles `/whoami` reads for the current user.
* `dlb.bff.post-login-redirect-url` — where the browser is sent after a successful login or logout; the web client's own origin (e.g. its Vite dev server address in local development, or "/" behind a same-origin reverse proxy in production).

## Local Development

Running `docker compose --profile api up` (see the repository's top-level `infrastructure/docker/compose.yml`) builds and starts the BFF alongside the Web Service, since developing Studio locally needs both.

::: info Note
If you found errors or have questions about this page, please consider reporting an issue at https://github.com/dialoguebranch/platform or sending an email to info@dialoguebranch.com.
:::
