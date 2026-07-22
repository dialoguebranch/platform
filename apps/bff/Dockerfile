# Standalone Dialogue Branch BFF (Backend-for-Frontend) Docker image.
#
# Build context must be the platform/ root of the monorepo:
#   docker build -t dlb-bff -f apps/bff/Dockerfile .
#
# Run the resulting image:
#   docker run -p 8082:8082 dlb-bff

# ---------------------------------------------------------- #
# -------------------- Stage 1: Builder -------------------- #
# ---------------------------------------------------------- #

FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

COPY global.json global.json
COPY apps/bff/ apps/bff/

RUN chmod +x apps/bff/gradlew

WORKDIR /build/apps/bff

# Build the JAR, skipping tests for a faster image build.
# All runtime configuration is supplied via environment variables — see Stage 2.
RUN ./gradlew clean build -x test --no-daemon

# ---------------------------------------------------------- #
# -------------------- Stage 2: Runtime -------------------- #
# ---------------------------------------------------------- #

FROM eclipse-temurin:17-jre

# The HTTP port Spring Boot listens on inside the container (default: 8082).
# Override with -e SERVER_PORT=... or in docker-compose.yml.
# Remember to match your -p host:container port mapping accordingly.
ENV SERVER_PORT=8082
EXPOSE ${SERVER_PORT}

# Copy the built JAR from the builder stage
COPY --from=builder /build/apps/bff/build/libs/dlb-bff-*.jar /app/dlb-bff.jar

ENTRYPOINT ["java", "-jar", "/app/dlb-bff.jar"]

# -------------------------------------------------------------------------
# Runtime configuration via environment variables.
# All values below can be overridden in a docker run -e or docker-compose.yml.
# -------------------------------------------------------------------------

# -------------------------------------------------------------------------
# Non-sensitive defaults — safe to set as ENV.
# -------------------------------------------------------------------------

ENV DLB_BFF_KEYCLOAK_BASE_URL=http://keycloak:8080/
ENV DLB_BFF_KEYCLOAK_BROWSER_BASE_URL=http://localhost:8081/
ENV DLB_BFF_API_BASE_URL=http://dlb-web-service:8089/dlb-web-service
ENV DLB_BFF_WEB_SERVICE_CLIENT_ID=dlb-web-service
ENV DLB_BFF_OAUTH2_CLIENT_ID=dlb-bff

# -------------------------------------------------------------------------
# Sensitive values — DO NOT set defaults here.
# Supply these at runtime via docker run -e, a docker-compose.yml
# environment block, or Docker Secrets.
#
# Required:
#   DLB_BFF_OAUTH2_CLIENT_SECRET      — the dlb-bff Keycloak client's secret
# -------------------------------------------------------------------------
