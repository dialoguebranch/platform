# Docker — Local Development Stack

This directory contains infrastructure configuration for running the Dialogue Branch platform
locally using Docker Compose. The stack brings up three services:

- **MariaDB 11** — relational database used by both the API and Keycloak
- **Keycloak 26** — identity provider, pre-configured with the `dialoguebranch` realm
- **dlb-web-service** — the REST API, configured to authenticate via Keycloak

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) installed and running
- The `dialoguebranch/dlb-web-service` image available locally or on Docker Hub
  (see [`apps/api/DOCKER.md`](../../apps/api/DOCKER.md) for build instructions)

## Setup

1. Copy the secrets template and fill in your values:

   ```bash
   cp secrets.env.example secrets.env
   ```

   Required secrets:

   | Variable | Description |
   |---|---|
   | `MARIADB_PASSWORD` | MariaDB root password |
   | `KEYCLOAK_ADMIN_USERNAME` | Keycloak admin username |
   | `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin password |
   | `AUTH_KEYCLOAK_CLIENT_SECRET` | Keycloak client secret for `dlb-web-service` |
   | `AUTH_NATIVE_JWT_ACCESS_TOKEN_SECRET` | JWT signing secret for access tokens |
   | `AUTH_NATIVE_JWT_REFRESH_TOKEN_SECRET` | JWT signing secret for refresh tokens |

   Generate strong random secrets with:
   ```bash
   openssl rand -base64 64
   ```

2. Start the stack:

   ```bash
   docker compose -f docker-compose.local-dev.yml up
   ```

   Service URLs once running:
   - API: `http://localhost:8089/dlb-web-service`
   - Keycloak admin console: `http://localhost:8081`

## Using a locally built image

To build the `dlb-web-service` image from source instead of pulling from Docker Hub, replace
the `image:` line in `docker-compose.local-dev.yml` with:

```yaml
build:
  context: ../..
  dockerfile: apps/api/standalone.Dockerfile
```

Then run the build before starting the stack:

```bash
docker compose -f docker-compose.local-dev.yml build
docker compose -f docker-compose.local-dev.yml up
```

## Directory structure

```
infrastructure/docker/
├── docker-compose.local-dev.yml          # Local development stack
├── secrets.env.example                   # Secrets template (copy to secrets.env)
├── secrets.env                           # Your local secrets (git-ignored)
└── import/
    ├── dialoguebranch-realm.json         # Keycloak realm configuration
    └── mariadb/
        └── docker-entrypoint-initdb.d/
            └── keycloak-init.sql         # Creates the keycloak database in MariaDB
```
