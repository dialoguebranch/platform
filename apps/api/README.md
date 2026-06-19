# Dialogue Branch API

The Dialogue Branch API is a Spring Boot web service that exposes the Dialogue Branch Core Library
over a REST API, allowing you to execute Dialogue Branch scripts in a server environment.

For additional information please refer to [www.dialoguebranch.com](https://www.dialoguebranch.com)
and the documentation at [www.dialoguebranch.com/docs](https://www.dialoguebranch.com/docs).

## Requirements

- Java 17 or higher
- Docker & Docker Compose

## 1. Deploying with Keycloak using Docker

The preferred way of using Dialogue Branch is with its Keycloak integration.

### 1.1. Prepare configuration

* Create a `gradle.properties` file in this folder by copying `gradle.docker-with-keycloak.properties`.
  This contains workable default configuration values.
* Create a `secrets.properties` file in the repo root by copying `secrets.example.properties`
  (has insecure, but workable default values).

### 1.2. Build Docker images

* Make sure Docker is running.
* From the repo root, build the Docker image:
  ```bash
  ./gradlew dockerBuild
  ```

For faster development, build the WAR file outside the Docker image and bind-mount it:

```bash
# Build the dev image once
./gradlew dockerBuildDev

# Then, to compile and deploy code changes
./gradlew clean updateConfig build -PbuildEnv=dev

# Subsequent changes only need
./gradlew build -PbuildEnv=dev
```

### 1.3. Run with Docker Compose

```bash
./gradlew dockerComposeUp
# or, for the dev image:
./gradlew dockerComposeUpDev
```

The first time it may take a while until Keycloak is fully initialized and running.

### 1.4. Test the setup

* Open http://localhost:8081/ — this should show the Keycloak administration panel.
  Log in with `admin` / `admin` and do the following:
  * Make sure the selected Realm is `dialoguebranch` (click Manage realms).
  * Create a user (e.g. username `user`), fill in an email address, first name and last name,
    check "email verified".
  * Go to **Credentials** and set a fixed password (e.g. `user`).
  * Go to **Role mapping** and assign client roles: `admin`, `client`, `editor`.
* Open http://localhost:8089/dlb-web-service/ — this should show the Swagger UI. Try it out:
  * Select the `/auth/login/` endpoint and click "Try it out".
  * Set both `user` and `password` to `"user"` and click Execute. You should receive:
    ```json
    {
      "user": "user",
      "role": "user",
      "token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIi..."
    }
    ```
* Open http://localhost:8080/ — the web client (log in with `user` / `user`).
* Open http://localhost:8100/ — phpMyAdmin for the MariaDB database (log in with `root` / `password`).

## 2. Deploying a Standalone Instance using Docker

For a simpler setup without Keycloak, using the built-in user-account management:

### 2.1. Prepare configuration

* Copy `gradle.docker-standalone.properties` to `gradle.properties` in this folder.
* Prepare `config/users.xml` by copying `config/users-example.xml`.
  You can keep the example `client::client`, `editor::editor`, and `admin::admin` users or define
  your own.

### 2.2. Build and run

```bash
# From the repo root
docker build --no-cache -t dlb-web-service -f ./apps/api/standalone.Dockerfile .
docker run -itd -p 8089:8089 --name dlb-web-service dlb-web-service
```

Open http://localhost:8089/dlb-web-service/ to see the Swagger documentation page.

Check out the [Exploring the API](https://dialoguebranch.com/docs/dialogue-branch/dev/tutorials/tutorial-webservice-exploringapi.html)
tutorial for further notes on navigating the API through Swagger.

## 3. Development Setup

### 3.1. IntelliJ IDEA

On the IntelliJ Welcome Screen select **Open** and select the repo root folder. Verify the
following settings:

* **File → Project Structure**
  * *Project Settings → Project*: SDK version 17+, Language Level 17.
  * *Project Settings → Modules*: Module SDK version 17+.
* **IntelliJ IDEA → Settings → Build, Execution, Deployment → Build Tools → Gradle**:
  Gradle JVM version 17+.

### 3.2. Configuration files

Before deploying, create the following files (copy from the provided examples):

* `apps/api/gradle.properties`
* `apps/api/config/users.xml`
* `apps/mock-variable-service/gradle.properties`
* `apps/mock-variable-service/config/service-users.xml`
