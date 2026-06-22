# Standalone Dialogue Branch Web Service Docker image.
#
# Build context must be the platform/ root of the monorepo:
#   docker build -t dlb-web-service -f apps/api/standalone.Dockerfile .
#
# Run the resulting image:
#   docker run -p 8089:8089 dlb-web-service
#
# To override the service configuration at runtime, mount a custom service.properties:
#   docker run -p 8089:8089 \
#     -v /path/to/your/service.properties:/usr/local/dialogue-branch/data/dlb-web-service/service.properties \
#     dlb-web-service

# ---------------------------------------------------------- #
# -------------------- Stage 1: Builder -------------------- #
# ---------------------------------------------------------- #

FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copy the monorepo source needed for this service
COPY global.json global.json
COPY packages/core/ packages/core/
COPY apps/api/ apps/api/

RUN chmod +x apps/api/gradlew

WORKDIR /build/apps/api

# Build the WAR, skipping tests and Javadoc for a faster image build.
# All runtime configuration is supplied via environment variables — see Stage 2.
RUN ./gradlew clean updateVersion build -x test -PbuildEnv=dev --no-daemon

# ---------------------------------------------------------- #
# -------------------- Stage 2: Runtime -------------------- #
# ---------------------------------------------------------- #

FROM tomcat:11.0.22-jre17-temurin

# The HTTP port Tomcat listens on inside the container (default: 8089).
# Override with -e SERVER_PORT=... or in docker-compose.yml.
# Remember to match your -p host:container port mapping accordingly.
ENV SERVER_PORT=8089
EXPOSE ${SERVER_PORT}

# Create the data directory used by the service
RUN mkdir -p /usr/local/dialogue-branch/data/dlb-web-service/

# Copy the built WAR from the builder stage into Tomcat
COPY --from=builder /build/apps/api/build/libs/dlb-web-service-*.war \
    ${CATALINA_HOME}/webapps/dlb-web-service.war

# Copy the example users file as the default authentication config
COPY apps/api/config/users-example.xml \
    /usr/local/dialogue-branch/data/dlb-web-service/users.xml

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
ENV DLB_AZURE_DATA_LAKE_ENABLED=false

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
#   DLB_AZURE_DATA_LAKE_ACCOUNT_KEY          — Azure Data Lake account key
#   DLB_AZURE_DATA_LAKE_SAS_TOKEN            — Azure Data Lake SAS token
# -------------------------------------------------------------------------

CMD []
