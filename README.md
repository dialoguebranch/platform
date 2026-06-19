# Dialogue Branch Platform

The Dialogue Branch Platform is a monorepo containing the tools and services needed to author,
execute, and serve Dialogue Branch scripts.

For additional information please refer to [www.dialoguebranch.com](https://www.dialoguebranch.com)
and the documentation at [www.dialoguebranch.com/docs](https://www.dialoguebranch.com/docs).

## Repository Structure

```
platform/
├── packages/
│   └── core/           # Core Java library for parsing and executing dialogue scripts
├── apps/
│   ├── api/            # Web service that exposes the core library over a REST API
│   ├── web/            # Vue 3 front-end client
│   └── mock-variable-service/  # Optional stand-alone external variable service
└── examples/           # Example Dialogue Branch scripts (en, nl)
```

## Requirements

- Java 17 or higher
- Docker & Docker Compose
- Node.js (for the `apps/web` front-end)
- An IDE with Git support (IntelliJ IDEA recommended)

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/dialoguebranch/platform.git
cd platform
```

### 2. Build with Gradle

The repo includes a Gradle Wrapper — no local Gradle installation required.

```bash
./gradlew build
```

### 3. Run with Docker Compose

```bash
cp secrets.example.properties secrets.properties   # adjust as needed
docker compose up
```

## Individual Packages & Apps

- **[packages/core](packages/core/README.md)** — Core Java library (Gradle)
- **[apps/api](apps/api/README.md)** — Spring Boot REST API wrapping the core library (includes full deployment & Keycloak setup instructions)
- **[apps/web](apps/web/README.md)** — Vue 3 / Vite front-end (`npm install && npm run dev`)
- **[apps/mock-variable-service](apps/mock-variable-service/)** — Mock external variable service

## Contributing

If you run into issues, please open an [Issue](https://github.com/dialoguebranch/platform/issues)
or contact `info@dialoguebranch.com`.
