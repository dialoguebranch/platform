# Web Services: Studio

Dialogue Branch Studio is a Vue 3 / Vite / Tailwind CSS single-page application bundled in the monorepo, in the [`apps/studio`](https://github.com/dialoguebranch/platform/tree/main/apps/studio) folder. It serves both as a reference client implementation and as a practical authoring and testing tool for a Dialogue Branch Web Service deployment.

Unlike a [3rd party client application](/web-services/third-party-clients), Studio never authenticates with Keycloak itself and never holds an OAuth2 access token in the browser — it delegates authentication entirely to the [BFF](/web-services/bff-service). See [Studio Authentication (via the BFF)](/web-services/authentication#studio-authentication-via-the-bff) for the full flow.

## Running Studio

```bash
cd apps/studio
npm install
npm run dev      # dev server with hot-reload
npm run build    # production build
npm run preview  # preview production build locally
```

The dev server proxies `/api`, `/oauth2`, `/login`, `/logout`, `/whoami`, and `/actuator` to the BFF, so during local development the BFF (see [BFF Service](/web-services/bff-service)) needs to be running alongside it.

## Local development setups

The repository's top-level `infrastructure/docker/compose.yml` supports three local setups, matching what a given change actually needs:

1. **Web service only** — run `docker compose up -d` (supporting services only: MariaDB, phpMyAdmin, Keycloak) and run the Web Service separately, e.g. `./gradlew bootRun` in `apps/api`.
2. **Studio development** — run `docker compose --profile api up -d` (also builds and starts the Web Service and the BFF), then run Studio itself separately with `npm run dev` as shown above. Its dev-server proxy sends `/api`, `/oauth2`, `/login`, `/logout`, `/whoami`, and `/actuator` to the Dockerized BFF on `http://localhost:8082`, so the browser only ever talks to Vite's own origin — same-origin without needing a real reverse proxy.
3. **Everything in Docker** — run `docker compose --profile studio up -d`. Studio and the BFF both run as containers here too, fronted by an `nginx` service on `http://localhost:8080` that path-splits requests between them (`studio.conf`), mirroring how a real deployment's reverse proxy makes them same-origin. Vite's dev-server proxy isn't available in this setup (nothing is running `npm run dev`), so this nginx service is what takes its place. Useful for testing the full containerized topology before deploying, without needing a real deployment to do it. If you already had a local Keycloak volume from before this third profile was added, `keycloak-sync` adds this profile's `http://localhost:8080/...` redirect/post-logout URIs to the existing `dlb-bff` client automatically on the next `docker compose up` — no volume reset or manual admin-console edit needed.

## Features

* **Dialogue rendering** — Once logged in, Studio lets you select a Dialogue Branch Project and start executing one of its dialogues, rendered either as chat "balloons" or as plain text, switchable via a mode selector.
* **Visual dialogue editor** — A node-graph editor (built on [Vue Flow](https://vueflow.dev/)) for authoring dialogues: reply links between nodes become edges in the graph, and nodes can be dragged, edited (title, speaker, colour, body text), renamed, and connected. Edits operate on a project's *draft* content (see [Authoring API](/web-services/api-service#authoring-api)) — nothing changes for end-users executing the published dialogue until it is explicitly published.
* **Importing scripts** — In Authoring Mode, *Import Scripts* in the Dialogue Browser imports one or more `.dlb` files into a folder of the project as draft dialogues, named after the folder and each file name. It shows which dialogues already exist (including ones pending deletion) and only replaces those when you choose to; the import is all or nothing, and scripts with syntax errors are imported but listed with their errors, to fix before publishing.
* **Exporting a dialogue** — In Authoring Mode, *Export as .dlb* on a dialogue in the Dialogue Browser downloads its current draft as a `.dlb` file, named after the dialogue (without its folder). Import it into the same folder to get the same dialogue back. Its translations are not included, and the action isn't offered on a dialogue pending deletion.
* **Project selector** — Lists the Dialogue Branch Projects available on the connected Web Service (for users with the `editor` or `admin` role), so you can switch between projects without redeploying the client.

::: info Note
If you found errors or have questions about this page, please consider reporting an issue at https://github.com/dialoguebranch/platform or sending an email to info@dialoguebranch.com.
:::
