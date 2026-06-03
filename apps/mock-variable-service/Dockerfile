### From your GIT/dialoguebranch/ folder
### docker build --no-cache -t dlb-external-var-service -f ./dlb-web/dlb-external-var-service/standalone.Dockerfile .
### docker run -itd -p 8090:8090 --name dlb-external-var-service dlb-external-var-service
### (User 'docker system prune -a') to clean up the cache and free up disk space

# Use an official Java runtime as a parent image
FROM tomcat:11.0

# Edit the server.xml, changing the port from 8080 to 8090
RUN sed -i 's/port="8080"/port="8090"/' ${CATALINA_HOME}/conf/server.xml

# Make port 8090 available to the world outside this container
EXPOSE 8090

# Copy the local source folder into the container
COPY . /usr/local/dialogue-branch/source/dlb-web/dlb-external-var-service/

# Ensure that the gradlew script may be executed in the container
RUN for f in `find /usr/local/dialogue-branch/source -name "gradlew" -print`; do chmod 755 $f && sed -i -e 's/\r//' $f; done

# Create the data folders used by the service
RUN mkdir /usr/local/dialogue-branch/data/
RUN mkdir /usr/local/dialogue-branch/data/dlb-external-var-service/

# Set the working directory to the DLB External Variable Service source folder
WORKDIR /usr/local/dialogue-branch/source/dlb-web/dlb-external-var-service/

# Execute a clean build
RUN ./gradlew clean updateVersion updateConfig build -PbuildEnv=prod

# Copy the generated .war file into the tomcat webapps
RUN cp /usr/local/dialogue-branch/source/dlb-web/dlb-external-var-service/build/libs/dlb-external-var-service-1.3.0.war /usr/local/tomcat/webapps/dlb-external-var-service.war

# Set the working directory in the container
WORKDIR /usr/local/tomcat

# Run Tomcat server
CMD ["catalina.sh", "run"]
