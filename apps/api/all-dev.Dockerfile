### You can use this Dockerfile for faster development. Compiling Java code and building the WAR 
### files is done outside the Docker image. You build the WAR files first (dlb-web-service and 
### dlb-external-var-service). Then you build the Docker image once and start everything up with
### the corresponding Docker Compose file (docker-compose/compose-all-dev.yml). After that you can 
### compile and build the WAR files again without the need to change anything in Docker. 
### Docker Compose just mounts your local WAR files in this Docker container.

### From GIT/dialoguebranch/dlb-web/dlb-web-service
### ./gradlew clean updateVersion build

### From your GIT\dialoguebranch folder (containing dlb-web and dlb-core-java repositories)
### docker build --no-cache -t dlb-web-service:dev -f ./dlb-web/dlb-web-service/with-keycloak-dev.Dockerfile .
### docker run -itd -p 8089:8089 --name DLB_Web_Service dlb-web-service:dev
### (User 'docker system prune -a') to clean up the cache and free up disk space

# Use an official Java runtime as a parent image
FROM tomcat:11.0

# Edit the server.xml, changing the port from 8080 to 8089
RUN sed -i 's/port="8080"/port="8089"/' ${CATALINA_HOME}/conf/server.xml

# Make port 8089 available to the world outside this container
EXPOSE 8089

### Prepare the data folders

# Create the data folders used by dlb-web-service
RUN mkdir -p /usr/local/dialogue-branch/data/
RUN mkdir /usr/local/dialogue-branch/data/dlb-web-service/

# Create the data folders used by dlb-external-var-service
RUN mkdir /usr/local/dialogue-branch/data/dlb-ext-var-service/

# Copy the config folder (containing users.xml) into the container, make sure there is an actual
# users.xml file when using the native authentication system (instead of keycloak)
COPY ./dlb-web/dlb-web-service/config/*.* /usr/local/dialogue-branch/data/dlb-web-service/

# Set the working directory in the container
WORKDIR /usr/local/tomcat

# Run Tomcat server
CMD ["catalina.sh", "run"]
