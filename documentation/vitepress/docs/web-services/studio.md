# Web Services: Studio

Dialogue Branch Studio is a Vue 3 / Vite / Tailwind CSS single-page application bundled in the monorepo, in the [`apps/web`](https://github.com/dialoguebranch/platform/tree/main/apps/web) folder. It serves both as a reference client implementation and as a practical authoring and testing tool for a Dialogue Branch Web Service deployment.

Unlike a [3rd party client application](/web-services/third-party-clients), Studio never authenticates with Keycloak itself and never holds an OAuth2 access token in the browser — it delegates authentication entirely to the [BFF](/web-services/bff-service). See [Studio Authentication (via the BFF)](/web-services/authentication#studio-authentication-via-the-bff) for the full flow.

## Running Studio

```bash
cd apps/web
npm install
npm run dev      # dev server with hot-reload
npm run build    # production build
npm run preview  # preview production build locally
```

The dev server proxies `/api`, `/oauth2`, `/login`, `/logout`, `/whoami`, and `/actuator` to the BFF, so during local development the BFF (see [BFF Service](/web-services/bff-service)) needs to be running alongside it.

## Features

* **Dialogue rendering** — Once logged in, Studio lets you select a Dialogue Branch Project and start executing one of its dialogues, rendered either as chat "balloons" or as plain text, switchable via a mode selector.
* **Visual dialogue editor** — A node-graph editor (built on [Vue Flow](https://vueflow.dev/)) for authoring dialogues: reply links between nodes become edges in the graph, and nodes can be dragged, edited (title, speaker, colour, body text), renamed, and connected. Edits operate on a project's *draft* content (see [Authoring API](/web-services/api-service#authoring-api)) — nothing changes for end-users executing the published dialogue until it is explicitly published.
* **Project selector** — Lists the Dialogue Branch Projects available on the connected Web Service (for users with the `editor` or `admin` role), so you can switch between projects without redeploying the client.

::: info Note
If you found errors or have questions about this page, please consider reporting an issue at https://github.com/dialoguebranch/platform or sending an email to info@dialoguebranch.com.
:::
