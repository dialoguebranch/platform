# Dialogue Branch External Variable Service (mock) Docker image.
#
# Build context must be the platform/ root of the monorepo:
#   docker build -t dlb-mock-variable-service -f apps/mock-variable-service/Dockerfile .
#
# Run the resulting image:
#   docker run -p 8090:8090 dlb-mock-variable-service

# ---------------------------------------------------------- #
# -------------------- Stage 1: Builder -------------------- #
# ---------------------------------------------------------- #

FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

COPY global.json global.json
COPY apps/mock-variable-service/ apps/mock-variable-service/

RUN chmod +x apps/mock-variable-service/gradlew

WORKDIR /build/apps/mock-variable-service

# Build the JAR, skipping tests and Javadoc for a faster image build.
# All runtime configuration is supplied via environment variables — see Stage 2.
RUN ./gradlew clean build -x test -PbuildEnv=dev --no-daemon

# ---------------------------------------------------------- #
# -------------------- Stage 2: Runtime -------------------- #
# ---------------------------------------------------------- #

FROM eclipse-temurin:17-jre

# The HTTP port Spring Boot listens on inside the container (default: 8090).
# Override with -e SERVER_PORT=... or in docker-compose.yml.
# Remember to match your -p host:container port mapping accordingly.
ENV SERVER_PORT=8090
EXPOSE ${SERVER_PORT}

# Create the data directory used by the service
RUN mkdir -p /usr/local/dialogue-branch/data/dlb-external-var-service/

# Copy the built JAR from the builder stage
COPY --from=builder /build/apps/mock-variable-service/build/libs/dlb-external-var-service-*.jar /app/dlb-external-var-service.jar

ENTRYPOINT ["java", "-jar", "/app/dlb-external-var-service.jar"]

# -------------------------------------------------------------------------
# Runtime configuration via environment variables.
# All values below can be overridden in a docker run -e or docker-compose.yml.
# -------------------------------------------------------------------------

ENV DLB_BASE_URL=http://localhost:8090/dlb-external-var-service
ENV DLB_DATA_DIR=/usr/local/dialogue-branch/data/dlb-external-var-service

# -------------------------------------------------------------------------
# Sensitive values — DO NOT set defaults here in production.
# Supply these at runtime via docker run -e, a docker-compose.yml
# environment block, or Docker Secrets.
#
# Optional:
#   DLB_AUTH_API_KEY — API key that callers (e.g. dlb-web-service) must present
# -------------------------------------------------------------------------
