# Dialogue Branch API

The Dialogue Branch API is a Spring Boot web service that exposes the Dialogue Branch Core Library
over a REST API, allowing you to execute Dialogue Branch scripts in a server environment.

For additional information please refer to [www.dialoguebranch.com](https://www.dialoguebranch.com)
and the documentation at [www.dialoguebranch.com/docs](https://www.dialoguebranch.com/docs).

## Requirements

- JDK 21+
- Docker

---

## 1. Running with Docker

### 1.1. Building the image

All Docker commands must be run from the **`platform/`** root of the monorepo, as the build
context needs access to both `apps/api/` and `packages/core/`.

```bash
docker build -t dlb-web-service -f apps/api/Dockerfile .
```

The build uses a two-stage process: the first stage compiles the Java source and produces an
executable JAR; the second stage copies only the JAR into a clean JRE image.

### 1.2. Local development

For local development, use the Docker Compose stack in `infrastructure/docker/`. See the
[root README](../../README.md) for full setup instructions.

---

## 2. Configuration

All configuration is supplied via environment variables at runtime. The table below lists
every supported variable, its default value, and whether it is required.

### General

| Variable | Default | Required | Description |
|---|---|---|---|
| `SERVER_PORT` | `8089` | No | Port Spring Boot listens on inside the container |
| `DLB_BASE_URL` | `http://localhost:8089/dlb-web-service` | Yes | Public base URL of the service |
| `DLB_DATA_DIR` | `/usr/local/dialogue-branch/data/dlb-web-service` | No | Path to the service data directory |

### Authentication

The service is a pure OAuth2 resource server: it validates bearer tokens issued by Keycloak but
plays no role in issuing or refreshing them. Clients authenticate directly with Keycloak using
the Authorization Code + PKCE flow.

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_AUTH_KEYCLOAK_BASE_URL` | `http://keycloak:8080/` | Yes | Base URL this service itself uses to reach Keycloak (e.g. for JWKS validation) |
| `DLB_AUTH_KEYCLOAK_BROWSER_BASE_URL` | same as `DLB_AUTH_KEYCLOAK_BASE_URL` | No | Base URL a user's browser can reach Keycloak at, used to build the OAuth2 URLs shown in Swagger UI. Only needed when it differs from `DLB_AUTH_KEYCLOAK_BASE_URL` (e.g. containerized deployments where Keycloak has a different internal vs. external hostname) |
| `DLB_AUTH_KEYCLOAK_REALM` | `dialoguebranch` | Yes | Keycloak realm name |
| `DLB_AUTH_KEYCLOAK_CLIENT_ID` | `dlb-web-service` | Yes | Keycloak client ID |

### Database

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_MARIADB_HOST` | `localhost` | Yes | MariaDB hostname |
| `DLB_MARIADB_PORT` | `3306` | No | MariaDB port |
| `DLB_MARIADB_USER` | `root` | Yes | MariaDB username |
| `DLB_MARIADB_PASSWORD` | — | **Yes** | MariaDB password |
| `DLB_MARIADB_DATABASE` | `dialoguebranch` | Yes | MariaDB database name |

### External Variable Service

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_EXTERNAL_VARIABLE_SERVICE_ENABLED` | `false` | No | Enable the external variable service |
| `DLB_EXTERNAL_VARIABLE_SERVICE_URL` | — | When enabled | URL of the external variable service |
| `DLB_EXTERNAL_VARIABLE_SERVICE_API_VERSION` | `1` | No | API version to use |
| `DLB_EXTERNAL_VARIABLE_SERVICE_API_KEY` | — | When enabled | API key for authentication |

### Changing the port

To run the service on a different port, set `SERVER_PORT` and update the `-p` mapping to match:

```bash
docker run -p 9090:9090 -e SERVER_PORT=9090 ... dlb-web-service
```

---

## 3. Publishing to Docker Hub

Tag and push the image to Docker Hub:

```bash
docker tag dlb-web-service dialoguebranch/dlb-web-service:1.2.5
docker tag dlb-web-service dialoguebranch/dlb-web-service:latest
docker push dialoguebranch/dlb-web-service:1.2.5
docker push dialoguebranch/dlb-web-service:latest
```

---

## 4. Development Setup

### 4.1. IntelliJ IDEA

On the IntelliJ Welcome Screen select **Open** and select the repo root folder. Verify the
following settings:

- **File → Project Structure**
  - *Project Settings → Project*: SDK version 17+, Language Level 17.
  - *Project Settings → Modules*: Module SDK version 17+.
- **IntelliJ IDEA → Settings → Build, Execution, Deployment → Build Tools → Gradle**:
  Gradle JVM version 17+.

