#!/bin/sh
# Entrypoint script for the Dialogue Branch Web Service container.

# When using native authentication, a users.xml file is required.
# If none has been mounted by the user, fall back to the bundled example file
# (which contains insecure default accounts — replace it for production use).
USERS_FILE="${DLB_DATA_DIR}/users.xml"
USERS_EXAMPLE="${DLB_DATA_DIR}/users-example.xml"
if [ ! -f "${USERS_FILE}" ]; then
    echo "WARNING: No users.xml found — copying users-example.xml as default. Replace this file for production use."
    cp "${USERS_EXAMPLE}" "${USERS_FILE}"
fi

exec java -jar /app/dlb-web-service.jar
