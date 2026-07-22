# Standalone Dialogue Branch Web Client Docker image.
#
# Build context must be the platform/ root of the monorepo:
#   docker build -t dlb-web-client -f apps/web/Dockerfile .
#
# Run the resulting image:
#   docker run -p 8090:80 dlb-web-client
#
# This client talks only to the BFF (apps/bff), same-origin, never to the Web Service or Keycloak
# directly — see src/auth.js. In production that same-origin illusion comes from the reverse
# proxy in front of both this container and the BFF routing by path, not from this image itself;
# see the BFF's own Javadoc for the routing this depends on.
#
# Vite bakes VITE_* variables into the static JS bundle at *build* time — unlike apps/api, these
# cannot be changed by setting environment variables on the running container. Override the ARG
# below with `docker build --build-arg VITE_DLB_API_BASE_URL=... .` only if the BFF isn't reachable
# at the default relative path for some deployment-specific reason.

# ---------------------------------------------------------- #
# -------------------- Stage 1: Builder -------------------- #
# ---------------------------------------------------------- #

FROM node:22 AS builder

WORKDIR /build

COPY global.json global.json
COPY apps/web/ apps/web/

WORKDIR /build/apps/web

ARG VITE_DLB_API_BASE_URL=/api/v1
ARG VITE_DLB_LOG_LEVEL=1

ENV VITE_DLB_API_BASE_URL=$VITE_DLB_API_BASE_URL \
    VITE_DLB_LOG_LEVEL=$VITE_DLB_LOG_LEVEL

RUN npm install && npm run build

# ---------------------------------------------------------- #
# -------------------- Stage 2: Runtime -------------------- #
# ---------------------------------------------------------- #

FROM httpd:2.4 AS final-stage

COPY --from=builder /build/apps/web/dist /usr/local/apache2/htdocs
