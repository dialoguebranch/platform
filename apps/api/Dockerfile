# Standalone Dialogue Branch Web Service Docker image.
#
# Build context must be the platform/ root of the monorepo:
#   docker build -t dlb-web-service -f apps/api/Dockerfile .
#
# Run the resulting image:
#   docker run -p 8089:8089 dlb-web-service

# ---------------------------------------------------------- #
# -------------------- Stage 1: Builder -------------------- #
# ---------------------------------------------------------- #

FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

COPY global.json global.json
COPY packages/core/ packages/core/
COPY apps/api/ apps/api/

RUN chmod +x apps/api/gradlew

WORKDIR /build/apps/api

# Build the JAR, skipping tests and Javadoc for a faster image build.
# All runtime configuration is supplied via environment variables — see Stage 2.
RUN ./gradlew clean build -x test -PbuildEnv=dev --no-daemon

# ---------------------------------------------------------- #
# -------------------- Stage 2: Runtime -------------------- #
# ---------------------------------------------------------- #

FROM eclipse-temurin:17-jre-alpine

# The HTTP port Spring Boot listens on inside the container (default: 8089).
# Override with -e SERVER_PORT=... or in docker-compose.yml.
# Remember to match your -p host:container port mapping accordingly.
ENV SERVER_PORT=8089
EXPOSE ${SERVER_PORT}

# Create the data directory used by the service
RUN mkdir -p /usr/local/dialogue-branch/data/dlb-web-service/

# Copy the built JAR from the builder stage
COPY --from=builder /build/apps/api/build/libs/dlb-web-service-*.jar /app/dlb-web-service.jar

# Bundle the example users file so the entrypoint can use it as a fallback.
# At startup, the entrypoint copies this to users.xml only if no users.xml
# is already present (e.g. mounted via a volume at runtime).
# To use your own accounts, mount a custom users.xml at:
#   /usr/local/dialogue-branch/data/dlb-web-service/users.xml
COPY apps/api/config/users-example.xml \
    /usr/local/dialogue-branch/data/dlb-web-service/users-example.xml

# Copy and register the entrypoint script
COPY apps/api/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

# -------------------------------------------------------------------------
# Runtime configuration via environment variables.
# All values below can be overridden in a docker run -e or docker-compose.yml.
# See https://www.dialoguebranch.com/docs for full documentation.
# -------------------------------------------------------------------------

# General
ENV DLB_BASE_URL=http://localhost:8089/dlb-web-service
ENV DLB_DATA_DIR=/usr/local/dialogue-branch/data/dlb-web-service

# -------------------------------------------------------------------------
# Non-sensitive defaults — safe to set as ENV.
# -------------------------------------------------------------------------

ENV DLB_AUTH_SERVICE=native
ENV DLB_AUTH_ACCESS_TOKEN_EXPIRATION_SECONDS=300
ENV DLB_AUTH_REFRESH_TOKEN_EXPIRATION_SECONDS=1800
ENV DLB_AUTH_KEYCLOAK_BASE_URL=http://keycloak:8080/
ENV DLB_AUTH_KEYCLOAK_REALM=dialoguebranch
ENV DLB_AUTH_KEYCLOAK_CLIENT_ID=dlb-web-service
ENV DLB_MARIADB_HOST=mariadb
ENV DLB_MARIADB_PORT=3306
ENV DLB_MARIADB_USER=root
ENV DLB_MARIADB_DATABASE=dialoguebranch
ENV DLB_EXTERNAL_VARIABLE_SERVICE_ENABLED=false
ENV DLB_EXTERNAL_VARIABLE_SERVICE_API_VERSION=1

# -------------------------------------------------------------------------
# Sensitive values — DO NOT set defaults here.
# Supply these at runtime via docker run -e, a docker-compose.yml
# environment block, or Docker Secrets.
#
# Required:
#   DLB_AUTH_JWT_ACCESS_TOKEN_SECRET  — JWT signing secret for access tokens
#   DLB_AUTH_JWT_REFRESH_TOKEN_SECRET — JWT signing secret for refresh tokens
#   DLB_MARIADB_PASSWORD              — MariaDB user password
#
# Optional (only needed when the relevant feature is enabled):
#   DLB_AUTH_KEYCLOAK_CLIENT_SECRET          — Keycloak client secret
#   DLB_EXTERNAL_VARIABLE_SERVICE_URL        — URL of the external variable service
#   DLB_EXTERNAL_VARIABLE_SERVICE_API_KEY    — API key for the external variable service
# -------------------------------------------------------------------------
