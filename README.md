# Dialogue Branch Platform

The Dialogue Branch Platform is a monorepo containing the tools and services needed to author,
execute, and serve Dialogue Branch scripts.

For additional information please refer to [www.dialoguebranch.com](https://www.dialoguebranch.com)
and the documentation at [www.dialoguebranch.com/docs](https://www.dialoguebranch.com/docs).

## Repository Structure

```
platform/
├── packages/
│   └── core/           # Core Java library for parsing and executing dialogue scripts
├── apps/
│   ├── api/            # Web service that exposes the core library over a REST API
│   ├── web/            # Vue 3 front-end client
│   └── mock-variable-service/  # Optional stand-alone external variable service
└── examples/           # Example Dialogue Branch scripts (en, nl)
```

## Requirements

- JDK 21 — the Gradle build uses a JDK 21 toolchain and auto-provisions one if it is not
  already installed, so any JDK on your `PATH` that can launch Gradle 9 will do
- Docker
- Node.js 22+ (for `apps/studio`)

## Local Development

### 1. Start the supporting services

```bash
cd infrastructure/docker
docker compose up -d
```

This starts MariaDB, phpMyAdmin, and Keycloak with hard-coded dev credentials. No configuration files are required.

To also build and run the API in Docker (useful when working on Studio only). Add `--build` if
you've changed source code in `apps/api` or `apps/bff` since the images were last built, since
Compose doesn't rebuild automatically otherwise:

```bash
docker compose --profile api up -d --build
```

To run everything in Docker: API, Studio, Backend For Frontend (BFF), and a reverse proxy in front, mirroring a real
deployment (same `--build` note applies):

```bash
docker compose --profile studio up -d --build
```

Studio is then available at [http://localhost:8080](http://localhost:8080).

### 2. Run the API

```bash
cd apps/api
./gradlew bootRun
```

The API starts on [http://localhost:8089/dlb-web-service](http://localhost:8089/dlb-web-service).  
Swagger UI: [http://localhost:8089/dlb-web-service/swagger-ui.html](http://localhost:8089/dlb-web-service/swagger-ui.html)

### 3. Run Studio

```bash
cd apps/studio
npm install   # first time only
npm run dev
```

Studio starts on [http://localhost:5173](http://localhost:5173).

### Local services

| Service | URL | Credentials |
|---|---|---|
| phpMyAdmin | [http://localhost:8100](http://localhost:8100) | `root` / `dev` |
| Keycloak admin | [http://localhost:8081/admin](http://localhost:8081/admin) | `admin` / `admin` |

### Verify your installation

#### 1. Create a Keycloak user

1. Open the [Keycloak admin console](http://localhost:8081/admin) and log in with `admin` / `admin`.
2. Select the **dialoguebranch** realm at **Manage realms** (top-left).
3. Go to **Users → Create new user**. Fill in username `testuser`, email `testuser@example.com`, first name `Test`, last name `User`, and click **Create**.
4. Go to the **Credentials** tab, click **Set password**, enter `password`, disable **Temporary**, and click **Save**.
5. Go to the **Role mapping** tab, click **Assign role**, switch the filter dropdown to **Filter by clients**, select `admin`, `participant`, and `editor` (the `dlb-web-service` client roles), and click **Assign**. Without at least one of these roles, the API will reject requests from this user with a 403 (insufficient privileges) even with a valid token.

#### 2. Authenticate in Swagger UI

The DLB Web Service is an OAuth2 resource server: it only validates bearer tokens, it doesn't
issue them. Swagger UI knows how to run the Authorization Code + PKCE flow itself, so you don't
need to copy a token from anywhere.

1. Open [Swagger UI](http://localhost:8089/dlb-web-service/swagger-ui.html).
2. Click the **Authorize** button (padlock icon, top right).
3. In the **oauth2** section, click **Authorize** – this opens a popup pointed at Keycloak's
   hosted login page. Sign in with `testuser` / `password`.
4. After the popup closes, click **Close** on the Authorize dialog. Swagger UI now attaches the
   token to every "Try it out" call automatically.

Local-dev access tokens last an hour (see `infrastructure/docker/import/dialoguebranch-realm.json`).
If a request eventually returns 401, just click **Authorize** again to get a fresh token – no
manual copy/paste required.

#### 3. Call an endpoint

1. Expand the **Variables** section and click `GET /variables/get`.
2. Click **Try it out**, set `timeZone` to your local time zone (e.g. `Europe/Amsterdam`), then click **Execute**.
3. You should get a `200` response with an empty list – no variables stored yet.

#### 4. Log in to Studio

2. Open Studio at [localhost:5173](http://localhost:5173) or [localhost:8080](http://localhost:8080) and sign in with `testuser` / `password`.
3. You should land on the project selector – this confirms the BFF and proxy are working.

## Individual Packages & Apps

- **[packages/core](packages/core/README.md)** – Core Java library (Gradle)
- **[apps/api](apps/api/README.md)** – Spring Boot REST API wrapping the core library (includes full deployment & Keycloak setup instructions)
- **[apps/studio](apps/studio/README.md)** – Vue 3 / Vite front-end, "Dialogue Branch Studio" (`npm install && npm run dev`)
- **[apps/mock-variable-service](apps/mock-variable-service/)** – Mock external variable service

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get
set up, the branch / commit / changelog conventions, and the pull-request
process. Issues labelled
[`good first issue`](https://github.com/dialoguebranch/platform/labels/good%20first%20issue)
are a good place to start.

- Report a bug or request a feature: open an
  [issue](https://github.com/dialoguebranch/platform/issues/new/choose).
- Report a security vulnerability: **privately**, per [SECURITY.md](SECURITY.md) —
  not via a public issue.
- General questions: `info@dialoguebranch.com`.

This project follows a [Code of Conduct](CODE_OF_CONDUCT.md).
