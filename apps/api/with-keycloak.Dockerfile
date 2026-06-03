### From your GIT\dialoguebranch folder (containing dlb-web and dlb-core-java repositories)
### docker build --no-cache -t dlb-web-service:1.2.5 -f dlb-web/dlb-web-service/with-keycloak.Dockerfile .
### docker run -itd -p 8089:8089 --name DLB_Web_Service dlb-web-service:1.2.5
### (User 'docker system prune -a') to clean up the cache and free up disk space

# Use an official Java runtime as a parent image
FROM tomcat:11.0

# Edit the server.xml, changing the port from 8080 to 8089
RUN sed -i 's/port="8080"/port="8089"/' ${CATALINA_HOME}/conf/server.xml

# Make port 8089 available to the world outside this container
EXPOSE 8089

### Prepare the source files and data folders

# Copy the local source folder into the container
COPY ./dlb-web/dlb-web-service/ /usr/local/dialogue-branch/source/dlb-web/dlb-web-service/
COPY ./dlb-core-java/ /usr/local/dialogue-branch/source/dlb-core-java/
RUN for f in `find /usr/local/dialogue-branch/source -name "gradlew" -print`; do chmod 755 $f && sed -i -e 's/\r//' $f; done

# Create some data folders used by the services
RUN mkdir /usr/local/dialogue-branch/data/
RUN mkdir /usr/local/dialogue-branch/data/dlb-web-service/

### Next, build and deploy the Web Service

# Set the working directory to the DLB Web Service source folder
WORKDIR /usr/local/dialogue-branch/source/dlb-web/dlb-web-service/

# Execute a clean build
RUN ./gradlew clean updateVersion build

# Copy the generated .war file into the tomcat webapps
RUN cp /usr/local/dialogue-branch/source/dlb-web/dlb-web-service/build/libs/dlb-web-service-1.2.5.war /usr/local/tomcat/webapps/dlb-web-service.war

# Copy the users.xml file into the data folder (not for the keycloak version!)
# RUN cp /usr/local/dialogue-branch/source/dlb-web/dlb-web-service/config/users.xml /usr/local/dialogue-branch/data/dlb-web-service/

# Set the working directory in the container
WORKDIR /usr/local/tomcat

# Run Tomcat server
CMD ["catalina.sh", "run"]
