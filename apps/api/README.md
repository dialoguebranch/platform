# Dialogue Branch API

The Dialogue Branch API is a Spring Boot web service that exposes the Dialogue Branch Core Library
over a REST API, allowing you to execute Dialogue Branch scripts in a server environment.

For additional information please refer to [www.dialoguebranch.com](https://www.dialoguebranch.com)
and the documentation at [www.dialoguebranch.com/docs](https://www.dialoguebranch.com/docs).

## Requirements

- Java 17 or higher
- Docker & Docker Compose

---

## 1. Running with Docker

### 1.1. Building the image

All Docker commands must be run from the **`platform/`** root of the monorepo, as the build
context needs access to both `apps/api/` and `packages/core/`.

```bash
docker build -t dlb-web-service -f apps/api/Dockerfile .
```

The build uses a two-stage process: the first stage compiles the Java source and produces a
WAR file; the second stage copies only the WAR into a clean Tomcat image. The JDK and Gradle
are not included in the final image.

### 1.2. Running the full local development stack

A local development stack (API + MariaDB + Keycloak + Web Client) is defined in
[`infrastructure/docker/docker-compose.local-dev.yml`](../../infrastructure/docker/docker-compose.local-dev.yml).
See [`infrastructure/docker/README.md`](../../infrastructure/docker/README.md) for setup
instructions.

### 1.4. Minimal deployment (API + MariaDB only)

A minimal compose file for deploying the API with native authentication and no Keycloak is
provided at
[`infrastructure/docker/docker-compose.minimal.yml`](../../infrastructure/docker/docker-compose.minimal.yml).

```bash
cp infrastructure/docker/secrets.minimal.env.example infrastructure/docker/secrets.minimal.env
# edit secrets.minimal.env with your values
docker compose -f infrastructure/docker/docker-compose.minimal.yml up
```

---

## 2. Configuration

All configuration is supplied via environment variables at runtime. The table below lists
every supported variable, its default value, and whether it is required.

### General

| Variable | Default | Required | Description |
|---|---|---|---|
| `SERVER_PORT` | `8089` | No | Port Tomcat listens on inside the container |
| `DLB_BASE_URL` | `http://localhost:8089/dlb-web-service` | Yes | Public base URL of the service |
| `DLB_DATA_DIR` | `/usr/local/dialogue-branch/data/dlb-web-service` | No | Path to the service data directory |

### Authentication

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_AUTH_SERVICE` | `native` | No | Authentication backend: `native` or `keycloak` |

#### Native authentication (`DLB_AUTH_SERVICE=native`)

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_AUTH_JWT_ACCESS_TOKEN_SECRET` | — | **Yes** | Secret used to sign JWT access tokens |
| `DLB_AUTH_JWT_REFRESH_TOKEN_SECRET` | — | **Yes** | Secret used to sign JWT refresh tokens |
| `DLB_AUTH_ACCESS_TOKEN_EXPIRATION_SECONDS` | `300` | No | Access token lifetime in seconds |
| `DLB_AUTH_REFRESH_TOKEN_EXPIRATION_SECONDS` | `1800` | No | Refresh token lifetime in seconds |

Generate strong secrets with:
```bash
openssl rand -base64 64
```

#### Keycloak authentication (`DLB_AUTH_SERVICE=keycloak`)

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_AUTH_KEYCLOAK_BASE_URL` | `http://keycloak:8080/` | Yes | Base URL of your Keycloak instance |
| `DLB_AUTH_KEYCLOAK_REALM` | `dialoguebranch` | Yes | Keycloak realm name |
| `DLB_AUTH_KEYCLOAK_CLIENT_ID` | `dlb-web-service` | Yes | Keycloak client ID |
| `DLB_AUTH_KEYCLOAK_CLIENT_SECRET` | — | **Yes** | Keycloak client secret |

### Database

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_MARIADB_HOST` | `mariadb` | Yes | MariaDB hostname |
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

### 4.2. Configuration files

Before deploying, create the following files (copy from the provided examples):

- `apps/api/gradle.properties`
- `apps/api/config/users.xml`
- `apps/mock-variable-service/gradle.properties`
- `apps/mock-variable-service/config/service-users.xml`
