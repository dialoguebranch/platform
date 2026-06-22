#!/bin/sh
# Entrypoint script for the Dialogue Branch Web Service container.
# Applies runtime environment variables before starting Tomcat.

# Configure the Tomcat HTTP port from SERVER_PORT (default: 8089)
sed -i "s/port=\"8080\"/port=\"${SERVER_PORT}\"/" "${CATALINA_HOME}/conf/server.xml"

exec catalina.sh run
