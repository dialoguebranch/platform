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

- JDK 21+
- Docker
- Node.js 22+ (for `apps/web`)

## Local Development

### 1. Start the supporting services

```bash
cd infrastructure/docker
docker compose up -d
```

This starts MariaDB, phpMyAdmin, and Keycloak with hard-coded dev credentials. No configuration files are required.

To also build and run the API in Docker (useful when working on the web client only):

```bash
docker compose --profile api up -d
```

### 2. Run the API

```bash
cd apps/api
./gradlew bootRun
```

The API starts on [http://localhost:8089/dlb-web-service](http://localhost:8089/dlb-web-service).  
Swagger UI: [http://localhost:8089/dlb-web-service/swagger-ui.html](http://localhost:8089/dlb-web-service/swagger-ui.html)

### 3. Run the web client

```bash
cd apps/web
npm install   # first time only
npm run dev
```

The web client starts on [http://localhost:5173](http://localhost:5173).

### Local services

| Service | URL | Credentials |
|---|---|---|
| phpMyAdmin | [http://localhost:8100](http://localhost:8100) | `root` / `dev` |
| Keycloak admin | [http://localhost:8081/admin](http://localhost:8081/admin) | `admin` / `admin` |

### Verify your installation

#### 1. Create a Keycloak user

1. Open the [Keycloak admin console](http://localhost:8081/admin) and log in with `admin` / `admin`.
2. Select the **dialoguebranch** realm from the top-left dropdown.
3. Go to **Users → Create new user**, fill in a username, and click **Create**.
4. Go to the **Credentials** tab, click **Set password**, enter a password, disable **Temporary**, and click **Save**.

#### 2. Authenticate in Swagger UI

1. Open [Swagger UI](http://localhost:8089/dlb-web-service/swagger-ui.html).
2. Click the **Authorize** button (padlock icon, top right).
3. Paste a bearer token. To get one, run:

   ```bash
   curl -s -X POST http://localhost:8081/realms/dialoguebranch/protocol/openid-connect/token \
     -d grant_type=password \
     -d client_id=dlb-web-service \
     -d client_secret=dev-client-secret \
     -d username=<user> \
     -d password=<password> | jq -r .access_token
   ```

4. Click **Authorize**, then **Close**.

#### 3. Call an endpoint

1. Expand the **Variables** section and click `GET /v{version}/variables/get`.
2. Click **Try it out**, set `version` to `1` and `timeZone` to your local time zone (e.g. `Europe/Amsterdam`), then click **Execute**.
3. You should get a `200` response with an empty list — no variables stored yet.

## Individual Packages & Apps

- **[packages/core](packages/core/README.md)** — Core Java library (Gradle)
- **[apps/api](apps/api/README.md)** — Spring Boot REST API wrapping the core library (includes full deployment & Keycloak setup instructions)
- **[apps/web](apps/web/README.md)** — Vue 3 / Vite front-end (`npm install && npm run dev`)
- **[apps/mock-variable-service](apps/mock-variable-service/)** — Mock external variable service

## Contributing

If you run into issues, please open an [Issue](https://github.com/dialoguebranch/platform/issues)
or contact `info@dialoguebranch.com`.
