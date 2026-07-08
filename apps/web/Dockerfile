# Standalone Dialogue Branch Web Client Docker image.
#
# Build context must be the platform/ root of the monorepo:
#   docker build -t dlb-web-client -f apps/web/Dockerfile .
#
# Run the resulting image:
#   docker run -p 8090:80 dlb-web-client
#
# Vite bakes VITE_* variables into the static JS bundle at *build* time — unlike apps/api, these
# cannot be changed by setting environment variables on the running container. Override any of the
# ARGs below with `docker build --build-arg NAME=value ...` to point the built client at a
# different API/Keycloak instance.

# ---------------------------------------------------------- #
# -------------------- Stage 1: Builder -------------------- #
# ---------------------------------------------------------- #

FROM node:22 AS builder

WORKDIR /build

COPY global.json global.json
COPY apps/web/ apps/web/

WORKDIR /build/apps/web

ARG VITE_DLB_API_BASE_URL=http://localhost:8089/dlb-web-service/v1
ARG VITE_DLB_AUTH_KEYCLOAK_URL=http://localhost:8081
ARG VITE_DLB_AUTH_KEYCLOAK_REALM=dialoguebranch
ARG VITE_DLB_AUTH_KEYCLOAK_CLIENT_ID=dlb-web-service
ARG VITE_DLB_LOG_LEVEL=1

ENV VITE_DLB_API_BASE_URL=$VITE_DLB_API_BASE_URL \
    VITE_DLB_AUTH_KEYCLOAK_URL=$VITE_DLB_AUTH_KEYCLOAK_URL \
    VITE_DLB_AUTH_KEYCLOAK_REALM=$VITE_DLB_AUTH_KEYCLOAK_REALM \
    VITE_DLB_AUTH_KEYCLOAK_CLIENT_ID=$VITE_DLB_AUTH_KEYCLOAK_CLIENT_ID \
    VITE_DLB_LOG_LEVEL=$VITE_DLB_LOG_LEVEL

RUN npm install && npm run build

# ---------------------------------------------------------- #
# -------------------- Stage 2: Runtime -------------------- #
# ---------------------------------------------------------- #

FROM httpd:2.4 AS final-stage

COPY --from=builder /build/apps/web/dist /usr/local/apache2/htdocs
