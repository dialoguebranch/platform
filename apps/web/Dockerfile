### From your GIT\dialoguebranch folder (containing dlb-web and dlb-core-java repositories)
### docker build --no-cache -t dlb-web-client -f dlb-web/dlb-web-client-vuejs/Dockerfile .
### (User 'docker system prune -a') to clean up the cache and free up disk space

FROM node:22 AS build-stage

COPY ./dlb-web/dlb-web-client-vuejs/ /src/

WORKDIR /src

RUN npm install && npm run build

FROM httpd:2.4 AS final-stage

COPY --from=build-stage src/dist /usr/local/apache2/htdocs
