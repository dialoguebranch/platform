# Docker — Dialogue Branch Web Service

This document describes how to build and run the Dialogue Branch Web Service as a Docker
container using `standalone.Dockerfile`.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) installed and running
- A clone of the full `dialoguebranch/platform` monorepo (the build requires both `apps/api/`
  and `packages/core/`)

## Building the image

All Docker commands must be run from the **`platform/`** root of the monorepo, as the build
context needs access to both `apps/api/` and `packages/core/`.

```bash
docker build -t dlb-web-service -f apps/api/standalone.Dockerfile .
```

The build uses a two-stage process: the first stage compiles the Java source and produces a
WAR file; the second stage copies only the WAR into a clean Tomcat image. The JDK and Gradle
are not included in the final image.

## Running the image

### Quick start

```bash
docker run -p 8089:8089 \
  -e DLB_AUTH_JWT_ACCESS_TOKEN_SECRET=your-access-secret \
  -e DLB_AUTH_JWT_REFRESH_TOKEN_SECRET=your-refresh-secret \
  -e DLB_MARIADB_PASSWORD=your-db-password \
  dlb-web-service
```

The service will be available at `http://localhost:8089/dlb-web-service`.

### Using a secrets file (recommended)

Copy the example secrets file and fill in your values:

```bash
cp apps/api/secrets.env.example apps/api/secrets.env
# edit apps/api/secrets.env with your actual secrets
```

Then run with:

```bash
docker run -p 8089:8089 --env-file apps/api/secrets.env dlb-web-service
```

## Configuration

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

### Azure Data Lake *(experimental)*

| Variable | Default | Required | Description |
|---|---|---|---|
| `DLB_AZURE_DATA_LAKE_ENABLED` | `false` | No | Enable Azure Data Lake integration |
| `DLB_AZURE_DATA_LAKE_AUTHENTICATION_METHOD` | — | When enabled | `sas-token` or `account-key` |
| `DLB_AZURE_DATA_LAKE_ACCOUNT_NAME` | — | When enabled | Storage account name |
| `DLB_AZURE_DATA_LAKE_ACCOUNT_KEY` | — | When `account-key` | Storage account key |
| `DLB_AZURE_DATA_LAKE_SAS_ACCOUNT_URL` | — | When `sas-token` | SAS account URL |
| `DLB_AZURE_DATA_LAKE_SAS_TOKEN` | — | When `sas-token` | SAS token |
| `DLB_AZURE_DATA_LAKE_FILE_SYSTEM_NAME` | — | When enabled | Data Lake file system name |

## Running the full stack

A local development stack (API + MariaDB + Keycloak) is defined in
[`infrastructure/docker/docker-compose.local-dev.yml`](../../infrastructure/docker/docker-compose.local-dev.yml).
See [`infrastructure/docker/README.md`](../../infrastructure/docker/README.md) for setup
instructions.

## Minimal docker-compose example (API + MariaDB only)

```yaml
services:
  dlb-web-service:
    image: dialoguebranch/dlb-web-service:latest
    ports:
      - "8089:8089"
    environment:
      DLB_BASE_URL: https://api.example.com/dlb-web-service
      DLB_AUTH_SERVICE: native
      DLB_MARIADB_HOST: mariadb
      DLB_MARIADB_USER: root
      DLB_MARIADB_DATABASE: dialoguebranch
    env_file:
      - secrets.env
    depends_on:
      mariadb:
        condition: service_healthy

  mariadb:
    image: mariadb:11
    environment:
      MARIADB_ROOT_PASSWORD: ${DLB_MARIADB_PASSWORD}
      MARIADB_DATABASE: dialoguebranch
    volumes:
      - db_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 10s
      retries: 10
      start_period: 30s

volumes:
  db_data:
```

## Changing the port

To run the service on a different port, set `SERVER_PORT` and update the `-p` mapping to match:

```bash
docker run -p 9090:9090 -e SERVER_PORT=9090 ... dlb-web-service
```

## Publishing to Docker Hub

Tag and push the image to Docker Hub:

```bash
docker tag dlb-web-service dialoguebranch/dlb-web-service:1.2.5
docker tag dlb-web-service dialoguebranch/dlb-web-service:latest
docker push dialoguebranch/dlb-web-service:1.2.5
docker push dialoguebranch/dlb-web-service:latest
```
