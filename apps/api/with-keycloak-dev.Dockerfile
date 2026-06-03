### You can use this Dockerfile for faster development. Compiling Java code and building a WAR file is done outside
### the Docker image. You build the WAR file first. Then you build the Docker image once and start everything up with
### the corresponding Docker Compose file. After that you can compile and build the WAR file again without the
### need to change anything in Docker. Docker Compose just mounts your local WAR file in the Docker container.

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

# Create some data folders used by the services
RUN mkdir -p /usr/local/dialogue-branch/data/
RUN mkdir /usr/local/dialogue-branch/data/dlb-web-service/

# Copy the users.xml file into the data folder (not for the keycloak version!)
# RUN cp /usr/local/dialogue-branch/source/dlb-web/dlb-web-service/config/users.xml /usr/local/dialogue-branch/data/dlb-web-service/

# Set the working directory in the container
WORKDIR /usr/local/tomcat

# Run Tomcat server
CMD ["catalina.sh", "run"]
